package org.hiero.smartapprover.config;

import java.util.List;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for the Smart Approval Bot application.
 */
@Configuration
@ConfigurationProperties(prefix = "app.config")
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

    public boolean isDefaultEnabled() {
        return defaultEnabled;
    }

    public void setDefaultEnabled(boolean defaultEnabled) {
        this.defaultEnabled = defaultEnabled;
    }

    public boolean isDefaultNotifyOnDismissal() {
        return defaultNotifyOnDismissal;
    }

    public void setDefaultNotifyOnDismissal(boolean defaultNotifyOnDismissal) {
        this.defaultNotifyOnDismissal = defaultNotifyOnDismissal;
    }

    public String getConfigFilePath() {
        return configFilePath;
    }

    public void setConfigFilePath(String configFilePath) {
        this.configFilePath = configFilePath;
    }

    public List<String> getCodeownersPaths() {
        return codeownersPaths;
    }

    public void setCodeownersPaths(List<String> codeownersPaths) {
        this.codeownersPaths = codeownersPaths;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        AppConfig appConfig = (AppConfig) o;

        return new EqualsBuilder().append(defaultEnabled, appConfig.defaultEnabled)
                .append(defaultNotifyOnDismissal, appConfig.defaultNotifyOnDismissal)
                .append(configFilePath, appConfig.configFilePath).append(codeownersPaths, appConfig.codeownersPaths)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(defaultEnabled).append(defaultNotifyOnDismissal)
                .append(configFilePath)
                .append(codeownersPaths).toHashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("defaultEnabled", defaultEnabled)
                .append("defaultNotifyOnDismissal", defaultNotifyOnDismissal)
                .append("configFilePath", configFilePath)
                .append("codeownersPaths", codeownersPaths)
                .toString();
    }
}