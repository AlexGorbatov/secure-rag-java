package com.altronixsoft.securerag;

import org.springframework.boot.SpringApplication;

/**
 * Runs the application locally without API keys: test profile (local ONNX embeddings), a stub chat
 * model, and PostgreSQL + Keycloak from compose.yaml via Docker Compose support. The additional "demo"
 * profile enables SeedDataRunner so there is something to ask about immediately; the automated test
 * suite (@SpringBootTest with @ActiveProfiles("test") only) never activates it.
 */
public class TestSecureRagJavaApplication {

    public static void main(String[] args) {
        SpringApplication.from(SecureRagJavaApplication::main)
                .with(StubChatModelConfiguration.class)
                .withAdditionalProfiles("test", "demo")
                .run(args);
    }

}
