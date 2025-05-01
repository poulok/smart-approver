package org.hiero.smartapprover.config;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.io.StringReader;
import java.security.KeyPair;
import java.security.Security;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
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
        KeyPair keyPair = loadKey();
        return Algorithm.RSA256((RSAPublicKey) keyPair.getPublic(),
				(RSAPrivateKey) keyPair.getPrivate());
    }

	private KeyPair loadKey() throws IOException {
		Security.addProvider(new BouncyCastleProvider());

		ClassPathResource resource = new ClassPathResource(properties.getPrivateKeyPath());
		byte[] content = resource.getInputStream().readAllBytes();
		String privateKeyContent = new String(content);

		KeyPair keyPair;
		try (org.bouncycastle.openssl.PEMParser pemParser = new  org.bouncycastle.openssl.PEMParser(new StringReader(privateKeyContent))) {
			Object object = pemParser.readObject();

			// Convert the parsed object to a PrivateKey
			org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
			keyPair = converter.getKeyPair((PEMKeyPair) object);

//			var encodedPrivate = Base64.getEncoder().encode(keyPair.getPrivate().getEncoded());
//			System.out.println("encodedPriv: [" + encodedPrivate + "]");

//			var pubKey = keyPair.getPublic();
//			var encoded = Base64.getEncoder().encode(pubKey.getEncoded());
//			System.out.println("encodedPub: [" + new String(encoded) + "]");
			return keyPair;
		}
	}

    @Bean
    public String generateJWT() throws Exception {
        Algorithm algorithm = jwtAlgorithm();

        // JWT token valid for 10 minutes
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(300);

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
