package org.hiero.smartapprover.model;

/**
 * Model class for repository configuration
 */
public record RepoConfig(boolean enabled, boolean notifyCodeOwners, boolean autoAssignReviewers) {

    public RepoConfig() {
        // Default values
        this(true, true, false);
    }
}
