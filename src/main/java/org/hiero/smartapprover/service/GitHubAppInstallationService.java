package org.hiero.smartapprover.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import java.io.IOException;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service for managing GitHub App installation tokens.
 */
@Service
public class GitHubAppInstallationService {

    @Value("${github.app.id}")
    private String appId;

    private final RSAPrivateKey privateKey;

    // Cache for installation tokens to avoid too many API calls
    private final Map<Long, InstallationToken> tokenCache = new ConcurrentHashMap<>();

    /**
     * Constructor for GitHubAppInstallationService.
     *
     * @param privateKey RSAPrivateKey for signing JWT tokens
     */
    public GitHubAppInstallationService(RSAPrivateKey privateKey) {this.privateKey = privateKey;}

    /**
     * Gets a GitHub client authenticated for a specific installation.
     *
     * @param installationId Installation ID
     * @return Authenticated GitHub client
     * @throws IOException if GitHub API communication fails
     */
    public GitHub getInstallationClient(long installationId) throws IOException {
        String token = getInstallationToken(installationId);
        return new GitHubBuilder()
                .withOAuthToken(token)
                .build();
    }

    /**
     * Gets an installation token, either from cache or from GitHub API.
     *
     * @param installationId Installation ID
     * @return Installation token
     * @throws IOException if GitHub API communication fails
     */
    private String getInstallationToken(long installationId) throws IOException {
        // Check if we have a valid cached token
        InstallationToken cachedToken = tokenCache.get(installationId);
        if (cachedToken != null && cachedToken.isValid()) {
            return cachedToken.getToken();
        }

        // Create a new JWT token for app authentication
        String jwtToken = createJwtToken();

        // Use the JWT to get an installation token
        GitHub appClient = new GitHubBuilder()
                .withJwtToken(jwtToken)
                .build();

        String token = appClient.getApp().getInstallationById(installationId)
                .createToken()
                .create()
                .getToken();

        // Cache the token (GitHub tokens are valid for 1 hour, but we'll use 50 minutes to be safe)
        Instant expiryTime = Instant.now().plus(50, ChronoUnit.MINUTES);
        tokenCache.put(installationId, new InstallationToken(token, expiryTime));

        return token;
    }

    /**
     * Creates a JWT token for GitHub App authentication.
     *
     * @return JWT token
     */
    private String createJwtToken() {
        // JWT tokens for GitHub Apps are valid for 10 minutes max
        Instant now = Instant.now();
        Instant expiry = now.plus(10, ChronoUnit.MINUTES);

        return JWT.create()
                .withIssuer(appId)
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(expiry))
                .sign(Algorithm.RSA256(null, privateKey));
    }

    /**
     * Class representing a cached installation token.
     */
    private static class InstallationToken {
        private final String token;
        private final Instant expiryTime;

        public InstallationToken(String token, Instant expiryTime) {
            this.token = token;
            this.expiryTime = expiryTime;
        }

        public String getToken() {
            return token;
        }

        public boolean isValid() {
            return Instant.now().isBefore(expiryTime);
        }
    }
}
