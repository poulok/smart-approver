package org.hiero.smartapprover.model;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * Model class for tracking pull request state
 */
public class PullRequestState {
    private final String repositoryFullName;
    private final int pullRequestNumber;
    private String lastProcessedSha;
    private Instant lastProcessedTime;
    private Set<String> changedFilesSinceLastProcess;

    public PullRequestState(String repositoryFullName, int pullRequestNumber) {
        this.repositoryFullName = repositoryFullName;
        this.pullRequestNumber = pullRequestNumber;
        this.changedFilesSinceLastProcess = new HashSet<>();
    }

    public String getRepositoryFullName() {
        return repositoryFullName;
    }

    public int getPullRequestNumber() {
        return pullRequestNumber;
    }

    public String getLastProcessedSha() {
        return lastProcessedSha;
    }

    public void setLastProcessedSha(String lastProcessedSha) {
        this.lastProcessedSha = lastProcessedSha;
    }

    public Instant getLastProcessedTime() {
        return lastProcessedTime;
    }

    public void setLastProcessedTime(Instant lastProcessedTime) {
        this.lastProcessedTime = lastProcessedTime;
    }

    public Set<String> getChangedFilesSinceLastProcess() {
        return changedFilesSinceLastProcess;
    }

    public void setChangedFilesSinceLastProcess(Set<String> changedFilesSinceLastProcess) {
        this.changedFilesSinceLastProcess = changedFilesSinceLastProcess;
    }
}
