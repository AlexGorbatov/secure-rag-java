package com.altronixsoft.securerag;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * The test profile has no real chat model (spring.ai.model.chat=none). This stub takes its place, so
 * the application context starts and nothing can call a paid API. Used by every test through
 * {@link TestcontainersConfiguration}, and on its own by {@link TestSecureRagJavaApplication}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class StubChatModelConfiguration {

    @Bean
    StubChatModel chatModel() {
        return new StubChatModel();
    }

}
