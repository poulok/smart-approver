package org.hiero.smartapprover.model;

import java.util.List;

/**
 * Model class representing a Code Owner rule
 */
public class CodeOwnerRule {
    private String pattern;
    private List<String> owners;

    public CodeOwnerRule(String pattern, List<String> owners) {
        this.pattern = pattern;
        this.owners = owners;
    }

    public String getPattern() {
        return pattern;
    }

    public List<String> getOwners() {
        return owners;
    }
}
