
package org.hiero.smartapprover.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for a specific repository.
 */
@Data
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
}
