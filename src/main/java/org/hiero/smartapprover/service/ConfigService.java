package org.hiero.smartapprover.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.hiero.smartapprover.config.AppConfig;
import org.hiero.smartapprover.model.RepoConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for managing repository-specific configurations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConfigService {

    private final AppConfig appConfig;
    private final RepositoryService repositoryService;

    /**
     * Gets the configuration for a specific repository.
     *
     * @param installationId GitHub App installation ID
     * @param owner Repository owner/organization
     * @param repo Repository name
     * @return Repository configuration
     * @throws IOException if GitHub API communication fails
     */
    public RepoConfig getRepositoryConfig(long installationId, String owner, String repo) throws IOException {
        JsonNode configJson = repositoryService.getRepositoryConfig(installationId, owner, repo);

        // Start with default configuration
        RepoConfig config = new RepoConfig();
        config.setEnabled(appConfig.isDefaultEnabled());
        config.setNotifyOnDismissal(appConfig.isDefaultNotifyOnDismissal());
        config.setExcludePaths(new ArrayList<>());

        // If there's a repository-specific config, override defaults
        if (configJson != null) {
            // Override enabled flag if present
            if (configJson.has("enabled")) {
                config.setEnabled(configJson.get("enabled").asBoolean());
            }

            // Override notification setting if present
            if (configJson.has("notifyOnDismissal")) {
                config.setNotifyOnDismissal(configJson.get("notifyOnDismissal").asBoolean());
            }

            // Add exclude paths if present
            if (configJson.has("excludePaths") && configJson.get("excludePaths").isArray()) {
                JsonNode excludePathsNode = configJson.get("excludePaths");
                List<String> excludePaths = new ArrayList<>();

                for (JsonNode pathNode : excludePathsNode) {
                    excludePaths.add(pathNode.asText());
                }

                config.setExcludePaths(excludePaths);
            }

            // Parse additional config options here as needed
        }

        return config;
    }
}
