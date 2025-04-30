package org.hiero.smartapprover.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hiero.smartapprover.model.DismissalResult;
import org.kohsuke.github.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for interacting with GitHub pull requests.
 */
@Service
public class PullRequestService {

    private static final Logger log = LoggerFactory.getLogger(PullRequestService.class);

    private final GitHubAppInstallationService installationService;
    private final ObjectMapper objectMapper;

    /**
     * Constructor for PullRequestService.
     *
     * @param installationService Service for managing GitHub App installations
     * @param objectMapper ObjectMapper for JSON processing
     */
    public PullRequestService(GitHubAppInstallationService installationService, ObjectMapper objectMapper) {
        this.installationService = installationService;
        this.objectMapper = objectMapper;
    }

    /**
     * Get modified files in a pull request.
     *
     * @param installationId GitHub App installation ID
     * @param owner Repository owner/organization
     * @param repo Repository name
     * @param pullNumber Pull request number
     * @return List of modified file paths
     * @throws IOException if GitHub API communication fails
     */
    public List<String> getModifiedFiles(long installationId, String owner, String repo, int pullNumber)
            throws IOException {
        GitHub gitHub = installationService.getInstallationClient(installationId);
        GHRepository repository = gitHub.getRepository(owner + "/" + repo);
        GHPullRequest pullRequest = repository.getPullRequest(pullNumber);

        List<String> files = new ArrayList<>();
        PagedIterable<GHPullRequestFileDetail> fileDetails = pullRequest.listFiles();

        for (GHPullRequestFileDetail file : fileDetails) {
            files.add(file.getFilename());
        }

        return files;
    }

    /**
     * Get all reviews for a pull request.
     *
     * @param installationId GitHub App installation ID
     * @param owner Repository owner/organization
     * @param repo Repository name
     * @param pullNumber Pull request number
     * @return List of review JSON nodes
     * @throws IOException if GitHub API communication fails
     */
    public List<JsonNode> getPullRequestReviews(long installationId, String owner, String repo, int pullNumber)
            throws IOException {
        GitHub gitHub = installationService.getInstallationClient(installationId);
        GHRepository repository = gitHub.getRepository(owner + "/" + repo);
        GHPullRequest pullRequest = repository.getPullRequest(pullNumber);

        List<GHPullRequestReview> reviews = pullRequest.listReviews().toList();

        // Convert to JsonNode for easier processing
        return reviews.stream()
                .map(this::convertReviewToJson)
                .collect(Collectors.toList());
    }

    /**
     * Dismiss specific reviews with explanations.
     *
     * @param installationId GitHub App installation ID
     * @param owner Repository owner/organization
     * @param repo Repository name
     * @param pullNumber Pull request number
     * @param dismissals Dismissal results containing reviews to dismiss
     * @throws IOException if GitHub API communication fails
     */
    public void dismissReviews(long installationId, String owner, String repo, int pullNumber,
            DismissalResult dismissals) throws IOException {
        GitHub gitHub = installationService.getInstallationClient(installationId);
        GHRepository repository = gitHub.getRepository(owner + "/" + repo);
        GHPullRequest pullRequest = repository.getPullRequest(pullNumber);

        for (Map.Entry<String, Map<String, Object>> entry : dismissals.getOwnerReasons().entrySet()) {
            String username = entry.getKey();
            Map<String, Object> details = entry.getValue();

            int reviewId = (int) details.get("reviewId");
            String reason = (String) details.get("reason");

            try {
                for (final GHPullRequestReview review : pullRequest.listReviews()) {
                    if (review.getId() == reviewId) {
                        review.dismiss(reason);
                        break;
                    }
                }

                log.info("Dismissed review ID {} from {}", reviewId, username);
            } catch (IOException e) {
                log.error("Failed to dismiss review from {}: {}", username, e.getMessage());
            }
        }
    }

    /**
     * Add a comment to the PR explaining which approvals were reset and why.
     *
     * @param installationId GitHub App installation ID
     * @param owner Repository owner/organization
     * @param repo Repository name
     * @param pullNumber Pull request number
     * @param dismissals Dismissal results containing affected owners
     * @param modifiedFiles List of modified files
     * @throws IOException if GitHub API communication fails
     */
    public void addExplanationComment(long installationId, String owner, String repo, int pullNumber,
            DismissalResult dismissals, List<String> modifiedFiles) throws IOException {
        GitHub gitHub = installationService.getInstallationClient(installationId);
        GHRepository repository = gitHub.getRepository(owner + "/" + repo);
        GHPullRequest pullRequest = repository.getPullRequest(pullNumber);

        Set<String> affectedOwners = dismissals.getOwnerReasons().keySet();

        if (affectedOwners.isEmpty()) {
            return; // No approvals were reset
        }

        StringBuilder commentBody = new StringBuilder();
        commentBody.append("## Smart Approval Bot Update\n\n");
        commentBody.append("Files were modified that affected the following code owners:\n\n");

        for (String affectedOwner : affectedOwners) {
            commentBody.append("- @").append(affectedOwner).append("\n");
        }

        commentBody.append("\nTheir approvals have been dismissed because their files were modified:\n\n");

        // List up to 10 modified files (to avoid extremely long comments)
        List<String> filesToShow = modifiedFiles.size() > 10
                ? modifiedFiles.subList(0, 10)
                : modifiedFiles;

        for (String file : filesToShow) {
            commentBody.append("- `").append(file).append("`\n");
        }

        if (modifiedFiles.size() > 10) {
            commentBody.append("- ... and ").append(modifiedFiles.size() - 10).append(" more files\n");
        }

        commentBody.append("\nPlease request new reviews from these code owners.");

        try {
            pullRequest.comment(commentBody.toString());
        } catch (IOException e) {
            log.error("Failed to add explanation comment: {}", e.getMessage());
        }
    }

    /**
     * Convert a GHPullRequestReview to a JsonNode for easier processing.
     *
     * @param review GitHub Pull Request Review object
     * @return JsonNode representation
     */
    private JsonNode convertReviewToJson(GHPullRequestReview review) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", review.getId());
        map.put("state", review.getState().name());
        map.put("submitted_at", review.getSubmittedAt().toString());

        Map<String, Object> user = new HashMap<>();
        user.put("login", review.getUser().getLogin());
        map.put("user", user);

        return objectMapper.valueToTree(map);
    }
}
