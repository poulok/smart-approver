
package org.hiero.smartapprover.model;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * Configuration for a specific repository.
 */
public class RepoConfig {
    /**
     * Whether smart approval management is enabled.
     */
    private boolean enabled = true;

    /**
     * Whether to add comments explaining approval dismissals.
     */
    private boolean notifyOnDismissal = true;

    /**
     * File patterns to exclude from approval management.
     */
    private List<String> excludePaths = new ArrayList<>();

    /**
     * Custom template for dismissal comments.
     */
    private String commentTemplate;

    /**
     * Whether to use strict mode (all files must have owners).
     */
    private boolean strictMode = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isNotifyOnDismissal() {
        return notifyOnDismissal;
    }

    public void setNotifyOnDismissal(boolean notifyOnDismissal) {
        this.notifyOnDismissal = notifyOnDismissal;
    }

    public List<String> getExcludePaths() {
        return excludePaths;
    }

    public void setExcludePaths(List<String> excludePaths) {
        this.excludePaths = excludePaths;
    }

    public String getCommentTemplate() {
        return commentTemplate;
    }

    public void setCommentTemplate(String commentTemplate) {
        this.commentTemplate = commentTemplate;
    }

    public boolean isStrictMode() {
        return strictMode;
    }

    public void setStrictMode(boolean strictMode) {
        this.strictMode = strictMode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        RepoConfig that = (RepoConfig) o;

        return new EqualsBuilder().append(enabled, that.enabled)
                .append(notifyOnDismissal, that.notifyOnDismissal).append(strictMode, that.strictMode)
                .append(excludePaths, that.excludePaths).append(commentTemplate, that.commentTemplate).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(enabled).append(notifyOnDismissal).append(excludePaths)
                .append(commentTemplate).append(strictMode).toHashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("enabled", enabled)
                .append("notifyOnDismissal", notifyOnDismissal)
                .append("excludePaths", excludePaths)
                .append("commentTemplate", commentTemplate)
                .append("strictMode", strictMode)
                .toString();
    }
}
