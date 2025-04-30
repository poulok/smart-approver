package org.hiero.smartapprover;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the GitHub Smart Approval Bot application
 */
@SpringBootApplication
@EnableScheduling
public class SmartApprovalBotApplication {
    public static void main(String[] args) {
        SpringApplication.run(SmartApprovalBotApplication.class, args);
    }
}
