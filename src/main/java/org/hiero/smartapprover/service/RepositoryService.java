package org.hiero.smartapprover.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hiero.smartapprover.config.AppConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kohsuke.github.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Base64;

/**
 * Service for interacting with GitHub repositories.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RepositoryService {

    private final AppConfig appConfig;
    private final ObjectMapper objectMapper;
    private final GitHubAppInstallationService installationService;

    /**
     * Retrieves the content of the CODEOWNERS file from the repository.
     *
     * @param installationId GitHub App installation ID
     * @param owner Repository owner/organization
     * @param repo Repository name
     * @param ref Branch or commit SHA
     * @return Content of the CODEOWNERS file, or null if not found
     * @throws IOException if an error occurs while accessing the GitHub API
     */
    public String getCodeownersContent(long installationId, String owner, String repo, String ref) throws IOException {
        GitHub gitHub = installationService.getInstallationClient(installationId);
        GHRepository repository = gitHub.getRepository(owner + "/" + repo);

        // Try each possible CODEOWNERS file location
        for (String path : appConfig.getCodeownersPaths()) {
            try {
                GHContent content = repository.getFileContent(path, ref);
                return content.getContent();
            } catch (GHFileNotFoundException e) {
                // File doesn't exist at this path, try the next one
                continue;
            }
        }

        return null; // No CODEOWNERS file found
    }

    /**
     * Retrieves the repository-specific configuration file content.
     *
     * @param installationId GitHub App installation ID
     * @param owner Repository owner/organization
     * @param repo Repository name
     * @return JSON node representing the configuration, or null if not found
     * @throws IOException if an error occurs while accessing the GitHub API
     */
    public JsonNode getRepositoryConfig(long installationId, String owner, String repo) throws IOException {
        GitHub gitHub = installationService.getInstallationClient(installationId);
        GHRepository repository = gitHub.getRepository(owner + "/" + repo);

        try {
            GHContent content = repository.getFileContent(appConfig.getConfigFilePath());
            String configContent = content.getContent();
            return objectMapper.readTree(configContent);
        } catch (GHFileNotFoundException e) {
            // Config file doesn't exist
            log.debug("No configuration file found at {} in {}/{}",
                    appConfig.getConfigFilePath(), owner, repo);
            return null;
        }
    }

    /**
     * Decodes base64-encoded content from GitHub API.
     *
     * @param content Base64-encoded content
     * @return Decoded string
     */
    private String decodeContent(String content) {
        byte[] decodedBytes = Base64.getDecoder().decode(content);
        return new String(decodedBytes);
    }
}
