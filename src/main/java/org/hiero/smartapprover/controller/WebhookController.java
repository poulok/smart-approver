package org.hiero.smartapprover.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;
import org.hiero.smartapprover.service.GitHubEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for handling GitHub webhook events.
 */
@RestController
@RequestMapping("/webhook")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final GitHubEventService gitHubEventService;
    private final ObjectMapper objectMapper;

    @Value("${github.app.webhook-secret}")
    private String webhookSecret;

    /**
     * Constructor for WebhookController.
     *
     * @param gitHubEventService Service for handling GitHub events
     * @param objectMapper ObjectMapper for JSON processing
     */
    public WebhookController(GitHubEventService gitHubEventService, ObjectMapper objectMapper) {
        this.gitHubEventService = gitHubEventService;
        this.objectMapper = objectMapper;
    }

    /**
     * Handles incoming GitHub webhook events.
     *
     * @param request HTTP request containing the webhook payload
     * @param eventType GitHub event type from X-GitHub-Event header
     * @param signature HMAC signature from X-Hub-Signature-256 header
     * @return Response entity
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> handleWebhook(
            HttpServletRequest request,
            @RequestHeader("X-GitHub-Event") String eventType,
            @RequestHeader("X-Hub-Signature-256") String signature) {

        try {
            // Read request body
            String payload = request.getReader().lines().collect(Collectors.joining());

            // Verify webhook signature
            if (!verifySignature(payload, signature)) {
                log.warn("Invalid webhook signature");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            // Parse JSON payload
            JsonNode json = objectMapper.readTree(payload);

            // Process event based on type
            switch (eventType) {
                case "pull_request":
                    handlePullRequestEvent(json);
                    break;
                case "ping":
                    // Just acknowledge ping events
                    log.info("Received ping event");
                    break;
                default:
                    log.info("Received unsupported event type: {}", eventType);
            }

            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            return ResponseEntity.ok(response);

        } catch (IOException e) {
            log.error("Error processing webhook", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Handles pull request events by dispatching to appropriate service methods.
     *
     * @param json JSON payload for the pull request event
     */
    private void handlePullRequestEvent(JsonNode json) {
        String action = json.path("action").asText();

        switch (action) {
            case "synchronize":
                // New commits pushed to the PR
                gitHubEventService.handlePullRequestSynchronize(json);
                break;
            case "opened":
                // PR newly created
                gitHubEventService.handlePullRequestOpened(json);
                break;
            default:
                log.debug("Ignoring pull_request.{} event", action);
        }
    }

    /**
     * Verifies the webhook signature using HMAC-SHA256.
     *
     * @param payload Request body content
     * @param signature Signature from X-Hub-Signature-256 header
     * @return True if signature is valid, false otherwise
     */
    private boolean verifySignature(String payload, String signature) {
        if (webhookSecret == null || webhookSecret.isEmpty()) {
            log.warn("Webhook secret not configured, skipping signature verification");
            return true;
        }

        // The signature has format "sha256=hex_digest"
        if (!signature.startsWith("sha256=")) {
            return false;
        }

        String expectedSignature = "sha256=" + new HmacUtils(HmacAlgorithms.HMAC_SHA_256, webhookSecret)
                .hmacHex(payload);

        return expectedSignature.equals(signature);
    }
}