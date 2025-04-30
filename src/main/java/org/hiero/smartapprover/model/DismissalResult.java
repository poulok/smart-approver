package org.hiero.smartapprover.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Result of determining which approvals should be dismissed.
 */
@Data
public class DismissalResult {
    /**
     * List of review IDs that should be dismissed.
     */
    private List<Integer> reviewIds = new ArrayList<>();

    /**
     * Map of owner usernames to dismissal details.
     */
    private Map<String, Map<String, Object>> ownerReasons = new HashMap<>();
}
