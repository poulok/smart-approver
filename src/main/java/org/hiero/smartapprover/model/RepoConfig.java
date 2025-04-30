package org.hiero.smartapprover.model;

/**
 * Model class for repository configuration
 */
public class RepoConfig {
    private boolean enabled;
    private boolean notifyCodeOwners;
    private boolean autoAssignReviewers;

    public RepoConfig() {
        // Default values
        this.enabled = true;
        this.notifyCodeOwners = true;
        this.autoAssignReviewers = false;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isNotifyCodeOwners() {
        return notifyCodeOwners;
    }

    public void setNotifyCodeOwners(boolean notifyCodeOwners) {
        this.notifyCodeOwners = notifyCodeOwners;
    }

    public boolean isAutoAssignReviewers() {
        return autoAssignReviewers;
    }

    public void setAutoAssignReviewers(boolean autoAssignReviewers) {
        this.autoAssignReviewers = autoAssignReviewers;
    }
}
