package org.hiero.smartapprover.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.hiero.smartapprover.model.DismissalResult;
import io.github.azagniotov.matcher.AntPathMatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for working with CODEOWNERS files and determining file ownership.
 */
@Service
@Slf4j
public class CodeownersService {

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * Parse CODEOWNERS file content and return a structured representation.
     *
     * @param content Content of the CODEOWNERS file
     * @return Map of file patterns to lists of owners
     */
    public Map<String, List<String>> parseCodeowners(String content) {
        Map<String, List<String>> rules = new LinkedHashMap<>();

        // Split content by lines and process each line
        String[] lines = content.split("\n");

        for (String line : lines) {
            // Skip comments and empty lines
            if (line.trim().isEmpty() || line.trim().startsWith("#")) {
                continue;
            }

            // Extract pattern and owners
            String[] parts = line.trim().split("\\s+");
            if (parts.length >= 2) {
                String pattern = parts[0];

                // Extract owners, removing @ prefix if present
                List<String> owners = Arrays.stream(parts)
                        .skip(1) // Skip the pattern
                        .map(owner -> owner.startsWith("@") ? owner.substring(1) : owner)
                        .collect(Collectors.toList());

                rules.put(pattern, owners);
            }
        }

        return rules;
    }

    /**
     * Find code owners for a specific file path.
     *
     * @param rules Map of file patterns to lists of owners
     * @param filePath Path of the file to check ownership for
     * @return List of code owners for the file
     */
    public List<String> findFileOwners(Map<String, List<String>> rules, String filePath) {
        List<String> matchingOwners = new ArrayList<>();

        // In CODEOWNERS, later patterns take precedence
        // So we need to process in order and override previous matches
        for (Map.Entry<String, List<String>> entry : rules.entrySet()) {
            String pattern = entry.getKey();

            if (pathMatcher.isMatch(pattern, filePath)) {
                // Clear previous matches as per GitHub CODEOWNERS behavior
                matchingOwners.clear();

                // Add all owners from this rule
                matchingOwners.addAll(entry.getValue());
            }
        }

        return matchingOwners;
    }

    /**
     * Determine which approvals should be dismissed based on file changes.
     *
     * @param modifiedFiles List of modified file paths
     * @param codeownersRules Map of file patterns to lists of owners
     * @param reviews List of reviews from the PR
     * @param excludePaths List of paths to exclude from approval management
     * @return DismissalResult containing affected owners and review IDs to dismiss
     */
    public DismissalResult determineApprovalsToReset(
            List<String> modifiedFiles,
            Map<String, List<String>> codeownersRules,
            List<JsonNode> reviews,
            List<String> excludePaths) {

        // Filter out excluded paths
        List<String> relevantFiles = modifiedFiles.stream()
                .filter(file -> !isExcluded(file, excludePaths))
                .collect(Collectors.toList());

        // Find all owners affected by the modified files
        Set<String> affectedOwners = new HashSet<>();

        for (String file : relevantFiles) {
            List<String> owners = findFileOwners(codeownersRules, file);
            affectedOwners.addAll(owners);
        }

        DismissalResult result = new DismissalResult();

        // Filter for approved reviews only and group by user
        Map<String, List<JsonNode>> approvalsByUser = reviews.stream()
                .filter(review -> "APPROVED".equals(review.path("state").asText()))
                .collect(Collectors.groupingBy(review -> review.path("user").path("login").asText()));

        // For each user with approvals, check if they're an affected owner
        for (Map.Entry<String, List<JsonNode>> entry : approvalsByUser.entrySet()) {
            String username = entry.getKey();
            List<JsonNode> userReviews = entry.getValue();

            // Check if this user is one of the affected owners
            if (affectedOwners.contains(username)) {
                // Get the most recent approval from this user
                JsonNode latestApproval = userReviews.stream()
                        .max(Comparator.comparing(review -> review.path("submitted_at").asText()))
                        .orElse(null);

                if (latestApproval != null) {
                    int reviewId = latestApproval.path("id").asInt();
                    result.getReviewIds().add(reviewId);

                    // Store reason for dismissal
                    result.getOwnerReasons().put(username, Map.of(
                            "reviewId", reviewId,
                            "reason", "Your approval was dismissed because files you own were modified."
                    ));
                }
            }

            // We'd also check team membership here in a production app
            // This would require additional API calls to check team membership
        }

        return result;
    }

    /**
     * Check if a file path is excluded based on the exclusion patterns.
     *
     * @param filePath File path to check
     * @param excludePaths List of exclusion patterns
     * @return True if the file is excluded, false otherwise
     */
    private boolean isExcluded(String filePath, List<String> excludePaths) {
        if (excludePaths == null || excludePaths.isEmpty()) {
            return false;
        }

        return excludePaths.stream()
                .anyMatch(pattern -> pathMatcher.isMatch(pattern, filePath));
    }
}
