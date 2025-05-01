package org.hiero.smartapprover.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hiero.smartapprover.service.PullRequestService;
import org.hiero.smartapprover.service.PullRequestStateService;
import org.hiero.smartapprover.service.CodeOwnerService;
import org.apache.commons.codec.digest.HmacUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

/**
 * Webhook event handling for GitHub events
 */
@RestController
@RequestMapping("/api/webhook")
public class WebhookController {
    private static final Logger logger = LoggerFactory.getLogger(WebhookController.class);

    private final ObjectMapper objectMapper;
    private final PullRequestService pullRequestService;
    private final PullRequestStateService pullRequestStateService;
    private final CodeOwnerService codeOwnerService;
    private final String webhookSecret;

    public WebhookController(
            ObjectMapper objectMapper,
            PullRequestService pullRequestService,
            PullRequestStateService pullRequestStateService,
            CodeOwnerService codeOwnerService,
            String webhookSecret) {
        this.objectMapper = objectMapper;
        this.pullRequestService = pullRequestService;
        this.pullRequestStateService = pullRequestStateService;
        this.codeOwnerService = codeOwnerService;
        this.webhookSecret = webhookSecret;
    }

    @PostMapping("/events")
    public ResponseEntity<String> handleWebhook(
			@RequestBody RequestEntity<String> requestEntity) {
		var headers = requestEntity.getHeaders();
		String eventType = headers.getFirst("X-GitHub-Event");
		String signature = headers.getFirst("X-Hub-Signature-256");
		String deliveryId = headers.getFirst("X-GitHub-Delivery");

        logger.info("Received webhook: {} - {}", eventType, deliveryId);

        // Verify webhook signature
		String payload = requestEntity.getBody();
		if (!isSignatureValid(payload, signature)) {
            logger.warn("Invalid webhook signature for delivery: {}", deliveryId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid signature");
        }

        try {
            JsonNode eventPayload = objectMapper.readTree(payload);

            switch (eventType) {
                case "pull_request":
                    handlePullRequestEvent(eventPayload);
                    break;
                case "pull_request_review":
                    handlePullRequestReviewEvent(eventPayload);
                    break;
                case "push":
                    handlePushEvent(eventPayload);
                    break;
				case null:
				default:
                    logger.debug("Ignoring unhandled event type: {}", eventType);
            }

            return ResponseEntity.ok("Webhook processed");
        } catch (IOException e) {
            logger.error("Error processing webhook", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing webhook");
        }
    }

    private boolean isSignatureValid(String payload, String signature) {
        if (webhookSecret == null || webhookSecret.isEmpty()) {
            logger.warn("Webhook secret not configured. Skipping signature validation.");
            return true;
        }

        if (signature == null || !signature.startsWith("sha256=")) {
            return false;
        }

        String expectedSignature = "sha256=" + HmacUtils.hmacSha256Hex(webhookSecret, payload);
        return expectedSignature.equals(signature);
    }

    private void handlePullRequestEvent(JsonNode payload) throws IOException {
        String action = payload.get("action").asText();
        logger.info("Processing pull request event with action: {}", action);

        JsonNode repository = payload.get("repository");
        JsonNode pullRequest = payload.get("pull_request");

        String repoFullName = repository.get("full_name").asText();
        int prNumber = pullRequest.get("number").asInt();

        if ("opened".equals(action) || "reopened".equals(action) || "synchronize".equals(action)) {
            pullRequestService.processPullRequestChanges(repoFullName, prNumber);
        } else if ("closed".equals(action)) {
            // Clean up resources when a PR is closed
            cleanupPullRequest(repoFullName, prNumber);
        } else if ("edited".equals(action)) {
            // Handle PR title/description edits if needed
            // This could be relevant if you want to parse PR descriptions for commands
        }
    }

    private void cleanupPullRequest(String repoFullName, int prNumber) {
        logger.info("Cleaning up resources for closed PR #{} in {}", prNumber, repoFullName);

        // Clear the PR state
        pullRequestStateService.removeState(repoFullName, prNumber);

        // Clear any cached CODEOWNERS rules if they're specific to this PR
        // We're not implementing this now since our cache is branch-based, not PR-based
    }

    private void handlePullRequestReviewEvent(JsonNode payload) {
        String action = payload.get("action").asText();
        logger.info("Processing pull request review event with action: {}", action);

        // Process review submissions, could be used to track who reviewed what files
        if ("submitted".equals(action)) {
            // Track the review for later use
        }
    }

    private void handlePushEvent(JsonNode payload) {
        // Handle pushes to PRs if needed
        String ref = payload.get("ref").asText();
        logger.info("Processing push event to ref: {}", ref);

        // If this is a push to a PR branch, we could process the changes
    }
}
