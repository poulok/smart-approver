package org.hiero.smartapprover.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * Result of determining which approvals should be dismissed.
 */
public class DismissalResult {
    /**
     * List of review IDs that should be dismissed.
     */
    private List<Integer> reviewIds = new ArrayList<>();

    /**
     * Map of owner usernames to dismissal details.
     */
    private Map<String, Map<String, Object>> ownerReasons = new HashMap<>();

    public List<Integer> getReviewIds() {
        return reviewIds;
    }

    public void setReviewIds(List<Integer> reviewIds) {
        this.reviewIds = reviewIds;
    }

    public Map<String, Map<String, Object>> getOwnerReasons() {
        return ownerReasons;
    }

    public void setOwnerReasons(Map<String, Map<String, Object>> ownerReasons) {
        this.ownerReasons = ownerReasons;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DismissalResult that = (DismissalResult) o;

        return new EqualsBuilder().append(reviewIds, that.reviewIds)
                .append(ownerReasons, that.ownerReasons).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(reviewIds).append(ownerReasons).toHashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("reviewIds", reviewIds)
                .append("ownerReasons", ownerReasons)
                .toString();
    }
}
