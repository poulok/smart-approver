package org.hiero.smartapprover.service;

import org.hiero.smartapprover.model.CodeOwnerRule;
import org.hiero.smartapprover.model.RepoConfig;
import org.kohsuke.github.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for processing Pull Request changes
 */
@Service
public class PullRequestService {
    private static final Logger logger = LoggerFactory.getLogger(PullRequestService.class);

    private final GitHubService gitHubService;
    private final CodeOwnerService codeOwnerService;
    private final PullRequestStateService pullRequestStateService;

    public PullRequestService(
            GitHubService gitHubService,
            CodeOwnerService codeOwnerService,
            PullRequestStateService pullRequestStateService) {
        this.gitHubService = gitHubService;
        this.codeOwnerService = codeOwnerService;
        this.pullRequestStateService = pullRequestStateService;
    }

    /**
     * Process changes in a pull request
     */
    public void processPullRequestChanges(String repoFullName, int prNumber) throws IOException {
        logger.info("Processing changes for PR #{} in {}", prNumber, repoFullName);

        GHRepository repository = gitHubService.getRepository(repoFullName);
        GHPullRequest pullRequest = gitHubService.getPullRequest(repoFullName, prNumber);

        // Check if the feature is enabled for this repository
        RepoConfig config = gitHubService.getRepositoryConfig(repository);
        if (!config.enabled()) {
            logger.info("Smart approval bot is disabled for repository {}", repoFullName);
            return;
        }

        // Capture information about the current change event
        String currentSha = pullRequest.getHead().getSha();
        Instant processingTime = Instant.now();

        // Check if the PR has changed since the last processing
        if (!pullRequestStateService.hasChangedSinceLastProcess(repoFullName, prNumber, currentSha)) {
            logger.info("PR #{} in {} has not changed since last processing, skipping", prNumber, repoFullName);
            return;
        }

        // Get the changed files in this PR
        Set<String> changedFiles = getChangedFiles(pullRequest);
        logger.info("Found {} changed files in PR #{}", changedFiles.size(), prNumber);

        // If we've processed this PR before, get only the files that have changed since then
        Set<String> filesChangedSinceLastProcess = changedFiles;
        if (pullRequestStateService.hasBeenProcessed(repoFullName, prNumber)) {
            // Compare with the previous state to find only files that have changed since last processing
            Set<String> previouslyChangedFiles =
                    pullRequestStateService.getChangedFilesSinceLastProcess(repoFullName, prNumber);

            // Files that are in the current set but not in the previous set are new changes
            // Files that are in both sets but have been modified need to be checked
            // For simplicity, we consider all changes relevant here
            filesChangedSinceLastProcess = new HashSet<>(changedFiles);

            logger.info("PR #{} in {} has {} files changed since last processing",
                    prNumber, repoFullName, filesChangedSinceLastProcess.size());
        }

        // Get CODEOWNERS rules from the target branch
        String targetBranch = pullRequest.getBase().getRef();
        List<CodeOwnerRule> codeOwnerRules = codeOwnerService.getCodeOwnerRules(repository, targetBranch);
        logger.info("Found {} code owner rules in branch {}", codeOwnerRules.size(), targetBranch);

        if (codeOwnerRules.isEmpty()) {
            logger.info("No CODEOWNERS file found, skipping approval management");
            return;
        }

        // Map files to their owners - only consider files changed since last processing
        Map<String, Set<String>> fileOwners =
                codeOwnerService.mapFilesToOwners(codeOwnerRules, filesChangedSinceLastProcess);

        // Get all affected owners
        Set<String> affectedOwners = codeOwnerService.getAffectedOwners(fileOwners);
        logger.info("Found {} affected code owners: {}", affectedOwners.size(), affectedOwners);

        // Process reviews and dismiss those from affected owners
        if (!affectedOwners.isEmpty()) {
            processReviews(pullRequest, affectedOwners, filesChangedSinceLastProcess);

            // Auto-assign reviewers if configured
            if (config.autoAssignReviewers()) {
                assignReviewers(pullRequest, affectedOwners);
            }

            // Notify relevant code owners if configured
            if (config.notifyCodeOwners()) {
                notifyCodeOwners(pullRequest, affectedOwners, fileOwners);
            }

            // Add a summary comment for this processing event
            StringBuilder summary = new StringBuilder("## Smart Approval Bot Summary\n\n");
            summary.append("Processing completed at: ").append(processingTime).append("\n");
            summary.append("Commit SHA: `").append(currentSha).append("`\n\n");
            summary.append("Changed files in this update: ").append(filesChangedSinceLastProcess.size()).append("\n");
            summary.append("Affected code owners: ").append(affectedOwners.size()).append("\n");

            gitHubService.addPullRequestComment(pullRequest, summary.toString());
        } else {
            logger.info("No code owners affected by changes in PR #{} in {}", prNumber, repoFullName);
        }

        // Update the PR state
        pullRequestStateService.updateState(repoFullName, prNumber, currentSha, changedFiles);
    }

    /**
     * Get all changed files in a pull request
     */
    private Set<String> getChangedFiles(GHPullRequest pullRequest) {
        Set<String> changedFiles = new HashSet<>();

        for (GHPullRequestFileDetail file : pullRequest.listFiles()) {
            changedFiles.add(file.getFilename());
        }

        return changedFiles;
    }

    /**
     * Process reviews and dismiss those from affected owners
     */
    private void processReviews(GHPullRequest pullRequest, Set<String> affectedOwners, Set<String> changedFiles) throws IOException {
        List<GHPullRequestReview> reviews = pullRequest.listReviews().toList();

        // Group reviews by user and get only the latest review from each user
        Map<String, GHPullRequestReview> latestReviews = new HashMap<>();
        for (GHPullRequestReview review : reviews) {
            String reviewer = review.getUser().getLogin();

            // Skip reviews that are not approval or explicit non-approval
            if (review.getState() != GHPullRequestReviewState.APPROVED &&
                    review.getState() != GHPullRequestReviewState.CHANGES_REQUESTED) {
                continue;
            }

            // Store only the latest review from each user
            if (!latestReviews.containsKey(reviewer) ||
                    latestReviews.get(reviewer).getSubmittedAt().before(review.getSubmittedAt())) {
                latestReviews.put(reviewer, review);
            }
        }

        // Dismiss reviews from affected owners
        Map<String, List<String>> dismissedReviewerFiles = new HashMap<>();
        for (Map.Entry<String, GHPullRequestReview> entry : latestReviews.entrySet()) {
            String reviewer = entry.getKey();
            GHPullRequestReview review = entry.getValue();

            // Skip reviews that are not approvals
            if (review.getState() != GHPullRequestReviewState.APPROVED) {
                continue;
            }

            // Check if this reviewer is an affected owner (remove @ prefix if present)
            String normalizedReviewer = reviewer.startsWith("@") ? reviewer.substring(1) : reviewer;
            boolean isAffected = affectedOwners.stream()
                    .map(owner -> owner.startsWith("@") ? owner.substring(1) : owner)
                    .anyMatch(normalizedReviewer::equals);

            if (isAffected) {
                // Find which files this reviewer owns that were changed
                List<String> ownedChangedFiles = findOwnedChangedFiles(normalizedReviewer, changedFiles);

                if (!ownedChangedFiles.isEmpty()) {
                    String message = "Dismissing approval because files you own have been modified.";
                    gitHubService.dismissReview(pullRequest, review, message);
                    dismissedReviewerFiles.put(reviewer, ownedChangedFiles);
                }
            }
        }

        // Add a detailed comment summarizing the dismissed reviews
        if (!dismissedReviewerFiles.isEmpty()) {
            StringBuilder comment = new StringBuilder("## Smart Approval Bot: Approvals Dismissed\n\n");

            comment.append("I've dismissed approvals from the following code owners as their files were modified:\n\n");

            for (Map.Entry<String, List<String>> entry : dismissedReviewerFiles.entrySet()) {
                String reviewer = entry.getKey();
                List<String> files = entry.getValue();

                comment.append("### ").append(reviewer).append("\n\n");

                // Show up to 5 files with an indicator if there are more
                for (int i = 0; i < Math.min(5, files.size()); i++) {
                    comment.append("- `").append(files.get(i)).append("`\n");
                }

                if (files.size() > 5) {
                    comment.append("- ...and ").append(files.size() - 5).append(" more files\n");
                }

                comment.append("\n");
            }

            comment.append("These code owners will need to review the changes and approve again.\n");

            gitHubService.addPullRequestComment(pullRequest, comment.toString());
        }
    }

    /**
     * Find which files owned by a reviewer were changed
     */
    private List<String> findOwnedChangedFiles(String reviewer, Set<String> changedFiles) {
        GHPullRequest pullRequest = null;
        List<String> ownedFiles = new ArrayList<>();

        // In a real implementation, this would use the codeOwnerService to determine
        // which of the changed files are owned by this specific reviewer
        try {
            String repoFullName = pullRequest.getRepository().getFullName();
            String targetBranch = pullRequest.getBase().getRef();
            GHRepository repository = gitHubService.getRepository(repoFullName);

            List<CodeOwnerRule> codeOwnerRules = codeOwnerService.getCodeOwnerRules(repository, targetBranch);

            // For each changed file, check if this reviewer is an owner
            for (String file : changedFiles) {
                Set<String> owners = codeOwnerService.getOwnersForFile(codeOwnerRules, file);

                // Normalize owner names for comparison (remove @ if present)
                boolean isOwner = owners.stream()
                        .map(owner -> owner.startsWith("@") ? owner.substring(1) : owner)
                        .anyMatch(owner -> owner.equals(reviewer));

                if (isOwner) {
                    ownedFiles.add(file);
                }
            }
        } catch (Exception e) {
            logger.error("Error determining owned files: {}", e.getMessage());
            // Fallback to return all changed files
            return new ArrayList<>(changedFiles);
        }

        return ownedFiles;
    }

    /**
     * Assign affected code owners as reviewers
     */
    private void assignReviewers(GHPullRequest pullRequest, Set<String> affectedOwners) throws IOException {
        // Filter out the PR author from the reviewer list
        String authorLogin = pullRequest.getUser().getLogin();
        Set<GHUser> reviewers = affectedOwners.stream()
                .map(owner -> owner.startsWith("@") ? owner.substring(1) : owner)
                .filter(owner -> !owner.equals(authorLogin))
                .map(MyUser::new)
                .collect(Collectors.toSet());

        if (!reviewers.isEmpty()) {
            logger.info("Assigning reviewers to PR #{}: {}", pullRequest.getNumber(), reviewers);
            pullRequest.requestReviewers(new ArrayList<>(reviewers));
        }
    }

    private static class MyUser extends GHUser {
        private MyUser(String login) {
            this.login = login;
        }
    }

    /**
     * Notify affected code owners about the changes
     */
    private void notifyCodeOwners(GHPullRequest pullRequest, Set<String> affectedOwners, Map<String, Set<String>> fileOwners) throws IOException {
        if (affectedOwners.isEmpty()) {
            return;
        }

        // Create a mapping of owners to their files
        Map<String, List<String>> ownerFiles = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : fileOwners.entrySet()) {
            String file = entry.getKey();
            Set<String> owners = entry.getValue();

            for (String owner : owners) {
                ownerFiles.computeIfAbsent(owner, k -> new ArrayList<>()).add(file);
            }
        }

        // Build the notification comment
        StringBuilder comment = new StringBuilder("## Code Owner Notification\n\n");
        comment.append("This pull request modifies files owned by the following people:\n\n");

        for (String owner : affectedOwners) {
            List<String> files = ownerFiles.getOrDefault(owner, Collections.emptyList());
            if (!files.isEmpty()) {
                comment.append("### ").append(owner).append("\n\n");

                // Show up to 5 files with an indicator if there are more
                for (int i = 0; i < Math.min(5, files.size()); i++) {
                    comment.append("- `").append(files.get(i)).append("`\n");
                }

                if (files.size() > 5) {
                    comment.append("- ...and ").append(files.size() - 5).append(" more files\n");
                }

                comment.append("\n");
            }
        }

        gitHubService.addPullRequestComment(pullRequest, comment.toString());
    }
}
