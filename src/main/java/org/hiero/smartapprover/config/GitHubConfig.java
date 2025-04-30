package org.hiero.smartapprover.config;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * Configuration for GitHub API client
 */
@Configuration
@EnableScheduling
public class GitHubConfig {

    private final GitHubAppProperties properties;

    public GitHubConfig(GitHubAppProperties properties) {
        this.properties = properties;
    }

    @Bean
    public Algorithm jwtAlgorithm() throws Exception {
        RSAPrivateKey privateKey = loadPrivateKey();
        return Algorithm.RSA256(null, privateKey);
    }

    private RSAPrivateKey loadPrivateKey() throws Exception {
        String privateKeyContent = new String(Files.readAllBytes(Paths.get(properties.getPrivateKeyPath())));

        // Strip out header, footer, and any whitespace
        String privateKeyPEM = privateKeyContent
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        // Decode the key
        byte[] encoded = Base64.getDecoder().decode(privateKeyPEM);

        // Create the key
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
        return (RSAPrivateKey) keyFactory.generatePrivate(keySpec);
    }

    @Bean
    public String generateJWT() throws Exception {
        Algorithm algorithm = jwtAlgorithm();

        // JWT token valid for 10 minutes
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(600);

        return JWT.create()
                .withIssuer(properties.getAppId())
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(expiresAt))
                .sign(algorithm);
    }

    @Bean
    public GitHub gitHubClient(String jwt) throws IOException {
        return new GitHubBuilder()
                .withJwtToken(jwt)
                .build();
    }
}
