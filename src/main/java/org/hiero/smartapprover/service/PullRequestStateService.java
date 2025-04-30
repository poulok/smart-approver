package org.hiero.smartapprover.service;

import org.hiero.smartapprover.model.PullRequestState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for tracking pull request state
 */
@Service
public class PullRequestStateService {
    private static final Logger logger = LoggerFactory.getLogger(PullRequestStateService.class);

    // In-memory storage for PR state (in a production environment, this could be a database)
    private final Map<String, PullRequestState> pullRequestStates = new ConcurrentHashMap<>();

    /**
     * Get the state for a pull request, creating it if it doesn't exist
     */
    public PullRequestState getOrCreateState(String repositoryFullName, int pullRequestNumber) {
        String key = getKey(repositoryFullName, pullRequestNumber);
        return pullRequestStates.computeIfAbsent(key,
                k -> new PullRequestState(repositoryFullName, pullRequestNumber));
    }

    /**
     * Update the state for a pull request after processing
     */
    public void updateState(String repositoryFullName, int pullRequestNumber,
            String currentSha, Set<String> changedFiles) {
        PullRequestState state = getOrCreateState(repositoryFullName, pullRequestNumber);
        state.setLastProcessedSha(currentSha);
        state.setLastProcessedTime(Instant.now());
        state.setChangedFilesSinceLastProcess(changedFiles);

        logger.info("Updated state for PR #{} in {}: SHA={}, changedFiles={}",
                pullRequestNumber, repositoryFullName, currentSha, changedFiles.size());
    }

    /**
     * Get changed files since the last processing
     */
    public Set<String> getChangedFilesSinceLastProcess(String repositoryFullName, int pullRequestNumber) {
        PullRequestState state = getOrCreateState(repositoryFullName, pullRequestNumber);
        return state.getChangedFilesSinceLastProcess();
    }

    /**
     * Check if a pull request has been processed before
     */
    public boolean hasBeenProcessed(String repositoryFullName, int pullRequestNumber) {
        PullRequestState state = getOrCreateState(repositoryFullName, pullRequestNumber);
        return state.getLastProcessedSha() != null;
    }

    /**
     * Check if a pull request has changed since last processing
     */
    public boolean hasChangedSinceLastProcess(String repositoryFullName, int pullRequestNumber, String currentSha) {
        PullRequestState state = getOrCreateState(repositoryFullName, pullRequestNumber);
        String lastSha = state.getLastProcessedSha();

        if (lastSha == null) {
            return true; // First time processing
        }

        return !lastSha.equals(currentSha);
    }

    /**
     * Create a unique key for the pull request state map
     */
    private String getKey(String repositoryFullName, int pullRequestNumber) {
        return repositoryFullName + "#" + pullRequestNumber;
    }

    /**
     * Remove state for a closed pull request
     */
    public void removeState(String repositoryFullName, int pullRequestNumber) {
        String key = getKey(repositoryFullName, pullRequestNumber);
        pullRequestStates.remove(key);
        logger.info("Removed state for PR #{} in {}", pullRequestNumber, repositoryFullName);
    }
}
