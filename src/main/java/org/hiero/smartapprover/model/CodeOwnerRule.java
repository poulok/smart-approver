package org.hiero.smartapprover.model;

import java.util.List;

/**
 * Model class representing a Code Owner rule
 */
public record CodeOwnerRule(String pattern, List<String> owners) {
}
