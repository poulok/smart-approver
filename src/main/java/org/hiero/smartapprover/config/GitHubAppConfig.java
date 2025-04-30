package org.hiero.smartapprover.config;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;

/**
 * Configuration class for GitHub App authentication and API client setup.
 */
@Configuration
@Slf4j
public class GitHubAppConfig {

    @Value("${github.app.id}")
    private String appId;

    @Value("${github.app.private-key-path}")
    private String privateKeyPath;

    /**
     * Creates a JWT token for GitHub App authentication.
     *
     * @return JWT token string
     */
    @Bean
    public String createJwtToken() {
        try {
            String privateKey = loadPrivateKey();
            RSAPrivateKey rsaPrivateKey = createPrivateKey(privateKey);

            return JWT.create()
                    .withIssuer(appId)
                    .withIssuedAt(new Date())
                    .withExpiresAt(Date.from(Instant.now().plus(10, ChronoUnit.MINUTES)))
                    .sign(Algorithm.RSA256(null, rsaPrivateKey));
        } catch (Exception e) {
            log.error("Failed to create JWT token", e);
            throw new RuntimeException("Failed to create JWT token", e);
        }
    }

    /**
     * Creates a GitHub App API client.
     *
     * @return GitHub client configured for app authentication
     */
    @Bean
    public GitHub gitHubAppClient() {
        try {
            String jwtToken = createJwtToken();
            return new GitHubBuilder()
                    .withJwtToken(jwtToken)
                    .build();
        } catch (IOException e) {
            log.error("Failed to create GitHub client", e);
            throw new RuntimeException("Failed to create GitHub client", e);
        }
    }

    /**
     * Loads the private key from the file system.
     *
     * @return Private key content as string
     * @throws IOException if file read fails
     */
    private String loadPrivateKey() throws IOException {
        return FileUtils.readFileToString(new File(privateKeyPath), StandardCharsets.UTF_8);
    }

    /**
     * Creates an RSA private key from the given private key string.
     *
     * @param privateKeyPEM Private key in PEM format
     * @return RSA private key
     * @throws NoSuchAlgorithmException if RSA algorithm is not available
     * @throws InvalidKeySpecException if the key specification is invalid
     */
    private RSAPrivateKey createPrivateKey(String privateKeyPEM)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        // Remove the first and last lines (BEGIN/END PRIVATE KEY)
        String privateKeyContent = privateKeyPEM
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        // Base64 decode the key
        byte[] keyBytes = Base64.getDecoder().decode(privateKeyContent);

        // Create a private key using the key bytes
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return (RSAPrivateKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
    }
}
