package com.altronixsoft.securerag;

import org.springframework.boot.SpringApplication;

/**
 * Runs the application locally without API keys: test profile (local ONNX embeddings), a stub chat
 * model, and PostgreSQL + Keycloak from compose.yaml via Docker Compose support. The additional "demo"
 * profile enables SeedDataRunner so there is something to ask about immediately; the automated test
 * suite (@SpringBootTest with @ActiveProfiles("test") only) never activates it. This is the default —
 * a plain checkout of this repo behaves exactly as CLAUDE.md's profile table describes.
 *
 * <p>Set the environment variable {@code LM_STUDIO=1} to swap the stub for a real chat model pointed at
 * a local LM Studio server instead (see application-lmstudio.properties), for a real generated answer
 * with no API key. Off by default and opt-in per invocation, rather than a standing profile every
 * clone of the repo activates: it needs LM Studio's local server running when a question is asked, and
 * is a personal convenience, not a supported deployment target.
 */
public class TestSecureRagJavaApplication {

    private static final String LM_STUDIO_FLAG = "LM_STUDIO";

    public static void main(String[] args) {
        boolean lmStudio = "1".equals(System.getenv(LM_STUDIO_FLAG));

        var application = SpringApplication.from(SecureRagJavaApplication::main)
                .withAdditionalProfiles(lmStudio
                        ? new String[] {"test", "demo", "lmstudio"}
                        : new String[] {"test", "demo"});
        if (!lmStudio) {
            application = application.with(StubChatModelConfiguration.class);
        }
        application.run(args);
    }

}
