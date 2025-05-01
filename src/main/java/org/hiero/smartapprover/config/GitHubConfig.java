package org.hiero.smartapprover.config;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.RSAPrivateKeySpec;
import java.time.Instant;
import java.util.Date;
import org.bouncycastle.util.io.pem.PemReader;

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
		ClassPathResource resource = new ClassPathResource(properties.getPrivateKeyPath());
		byte[] content = resource.getInputStream().readAllBytes();
		String privateKeyContent = new String(content);

		try (PemReader pemReader = new PemReader(new StringReader(privateKeyContent))) {
			byte[] keyBytes = pemReader.readPemObject().getContent();

			// Extract modulus and private exponent (PKCS#1 structure)
			ASN1Primitive asn1Primitive = ASN1Primitive.fromByteArray(keyBytes);
			ASN1Sequence asn1Sequence = (ASN1Sequence) asn1Primitive;
			byte[] modulusBytes = ((ASN1Integer) asn1Sequence.getObjectAt(1)).getEncoded();
			BigInteger modulus = new BigInteger(1, modulusBytes);
			byte[] privateExponentBytes = ((ASN1Integer) asn1Sequence.getObjectAt(2)).getEncoded();
			BigInteger privateExponent = new BigInteger(1, privateExponentBytes);

			RSAPrivateKeySpec keySpec = new RSAPrivateKeySpec(modulus, privateExponent);
			KeyFactory keyFactory = KeyFactory.getInstance("RSA");
			return (RSAPrivateKey) keyFactory.generatePrivate(keySpec);
		}
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
