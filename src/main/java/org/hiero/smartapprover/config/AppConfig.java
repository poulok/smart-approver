package org.hiero.smartapprover.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuration properties for the Smart Approval Bot application.
 */
@Configuration
@ConfigurationProperties(prefix = "app.config")
@Data
public class AppConfig {

    /**
     * Default value for whether the smart approval feature is enabled.
     */
    private boolean defaultEnabled = true;

    /**
     * Default value for whether to notify on dismissal with comments.
     */
    private boolean defaultNotifyOnDismissal = true;

    /**
     * Path to the repository-specific configuration file.
     */
    private String configFilePath = ".github/smart-approval-config.json";

    /**
     * List of possible paths where CODEOWNERS file might be located.
     */
    private List<String> codeownersPaths = List.of(
            ".github/CODEOWNERS",
            "CODEOWNERS",
            "docs/CODEOWNERS",
            ".gitlab/CODEOWNERS"
    );
}