package org.hiero.smartapprover.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.hiero.smartapprover.model.DismissalResult;
import org.hiero.smartapprover.model.RepoConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Service for handling GitHub webhook events.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GitHubEventService {

    private final RepositoryService repositoryService;
    private final CodeownersService codeownersService;
    private final PullRequestService pullRequestService;
    private final ConfigService configService;

    /**
     * Handles the pull_request.synchronize event (new commits pushed to a PR).
     *
     * @param payload JSON payload from the webhook
     */
    @Async
    public void handlePullRequestSynchronize(JsonNode payload) {
        try {
            // Extract repository and PR details
            String repoOwner = payload.path("repository").path("owner").path("login").asText();
            String repoName = payload.path("repository").path("name").asText();
            int prNumber = payload.path("number").asInt();
            long installationId = payload.path("installation").path("id").asLong();
            String headSha = payload.path("pull_request").path("head").path("sha").asText();

            log.info("Processing pull request synchronize event: {}/{} #{}", repoOwner, repoName, prNumber);

            // Check if feature is enabled for this repository
            RepoConfig config = configService.getRepositoryConfig(installationId, repoOwner, repoName);
            if (!config.isEnabled()) {
                log.info("Smart approval management disabled for {}/{}", repoOwner, repoName);
                return;
            }

            // Get CODEOWNERS content
            String codeownersContent = repositoryService.getCodeownersContent(installationId, repoOwner, repoName, headSha);
            if (codeownersContent == null) {
                log.info("No CODEOWNERS file found in {}/{}", repoOwner, repoName);
                return;
            }

            // Parse CODEOWNERS file
            Map<String, List<String>> codeownersRules = codeownersService.parseCodeowners(codeownersContent);

            // Get modified files in this update
            List<String> modifiedFiles = pullRequestService.getModifiedFiles(installationId, repoOwner, repoName, prNumber);

            // Get all reviews for this PR
            List<JsonNode> reviews = pullRequestService.getPullRequestReviews(installationId, repoOwner, repoName, prNumber);

            // Determine which approvals should be reset
            DismissalResult dismissals = codeownersService.determineApprovalsToReset(
                    modifiedFiles, codeownersRules, reviews, config.getExcludePaths());

            // If there are approvals to dismiss
            if (!dismissals.getReviewIds().isEmpty()) {
                // Dismiss the affected reviews
                pullRequestService.dismissReviews(installationId, repoOwner, repoName, prNumber, dismissals);

                // Add an explanation comment if enabled
                if (config.isNotifyOnDismissal()) {
                    pullRequestService.addExplanationComment(installationId, repoOwner, repoName, prNumber,
                            dismissals, modifiedFiles);
                }

                log.info("Dismissed {} approvals for PR {}/{} #{}",
                        dismissals.getReviewIds().size(), repoOwner, repoName, prNumber);
            } else {
                log.info("No approvals need to be dismissed for PR {}/{} #{}",
                        repoOwner, repoName, prNumber);
            }

        } catch (IOException e) {
            log.error("Error processing pull request synchronize event", e);
        }
    }

    /**
     * Handles the pull_request.opened event.
     * This is mainly for logging and establishing initial state if needed.
     *
     * @param payload JSON payload from the webhook
     */
    @Async
    public void handlePullRequestOpened(JsonNode payload) {
        String repoOwner = payload.path("repository").path("owner").path("login").asText();
        String repoName = payload.path("repository").path("name").asText();
        int prNumber = payload.path("number").asInt();

        log.info("Pull request opened: {}/{} #{}", repoOwner, repoName, prNumber);
        // No need to dismiss approvals on a newly opened PR since there aren't any yet
    }
}
