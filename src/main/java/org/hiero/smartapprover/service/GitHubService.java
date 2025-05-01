package org.hiero.smartapprover.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hiero.smartapprover.model.RepoConfig;
import org.kohsuke.github.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Base64;
import java.util.Optional;

/**
 * Service for interacting with GitHub repositories
 */
@Service
public class GitHubService {
    private static final Logger logger = LoggerFactory.getLogger(GitHubService.class);
    private static final String CONFIG_PATH = ".github/smart-approval.json";

    private final GitHub gitHubClient;
    private final ObjectMapper objectMapper;

    public GitHubService(GitHub gitHubClient, ObjectMapper objectMapper) {
        this.gitHubClient = gitHubClient;
        this.objectMapper = objectMapper;
    }

    public GHRepository getRepository(String repoFullName) throws IOException {
        return gitHubClient.getRepository(repoFullName);
    }

    public GHPullRequest getPullRequest(String repoFullName, int prNumber) throws IOException {
        GHRepository repository = getRepository(repoFullName);
        return repository.getPullRequest(prNumber);
    }

    public RepoConfig getRepositoryConfig(GHRepository repository) {
        try {
            Optional<GHContent> configFile = getFileContent(repository, CONFIG_PATH);

            if (configFile.isPresent()) {
                String content = new String(Base64.getDecoder().decode(configFile.get().getContent()));
                return objectMapper.readValue(content, RepoConfig.class);
            }
        } catch (IOException e) {
            logger.warn("Failed to load repository configuration: {}", e.getMessage());
        }

        // Return default configuration if none found
        return new RepoConfig();
    }

    public Optional<GHContent> getFileContent(GHRepository repository, String path) {
        try {
            GHContent content = repository.getFileContent(path);
            return Optional.of(content);
        } catch (IOException e) {
            logger.debug("File not found: {}", path);
            return Optional.empty();
        }
    }

    public Optional<GHContent> getFileContent(GHRepository repository, String path, String ref) {
        try {
            GHContent content = repository.getFileContent(path, ref);
            return Optional.of(content);
        } catch (IOException e) {
            logger.debug("File not found: {} at ref {}", path, ref);
            return Optional.empty();
        }
    }

    public void dismissReview(GHPullRequest pullRequest, GHPullRequestReview reviewToDismiss, String message)
            throws IOException {
        logger.info("Dismissing review #{} by {} on PR #{}",
                reviewToDismiss.getId(), reviewToDismiss.getUser().getLogin(), pullRequest.getNumber());

        for (final GHPullRequestReview review : pullRequest.listReviews()) {
            if (review.getId() == reviewToDismiss.getId()) {
                reviewToDismiss.dismiss(message);
                break;
            }
        }
    }

    public void addPullRequestComment(GHPullRequest pullRequest, String comment) throws IOException {
        logger.info("Adding comment to PR #{}: {}", pullRequest.getNumber(), comment);
        pullRequest.comment(comment);
    }

//    public GHAppInstallation getAppInstallationForRepo(GHRepository repository) throws IOException {
//        GHAppInstallationToken token = repository.getApp().getInstallationToken();
//        return repository.getApp().getInstallationById(token.getId());
//    }
}
