package com.altronixsoft.securerag;

import org.springframework.boot.SpringApplication;

/**
 * Runs the application locally without API keys: test profile (local ONNX embeddings), a stub chat
 * model, and PostgreSQL + Keycloak from compose.yaml via Docker Compose support.
 */
public class TestSecureRagJavaApplication {

    public static void main(String[] args) {
        SpringApplication.from(SecureRagJavaApplication::main).with(StubChatModelConfiguration.class).withAdditionalProfiles("test").run(args);
    }

}
