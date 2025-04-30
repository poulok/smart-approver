package org.hiero.smartapprover;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Main application class for the GitHub Smart Approval Bot.
 * <p>
 * This application intelligently manages the code review approval process by:
 * 1. Detecting which files are modified in each commit or pull request
 * 2. Identifying the code owners of those specific files (using CODEOWNERS file)
 * 3. Only clearing approvals from the relevant code owners when their files are modified
 * 4. Preserving approvals from code owners whose files remain unchanged
 * </p>
 */
@SpringBootApplication
@EnableAsync
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
