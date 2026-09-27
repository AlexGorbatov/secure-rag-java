package com.altronixsoft.securerag;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * The test profile has no real chat model (spring.ai.model.chat=none). This stub takes its place, so
 * the application context starts and nothing can call a paid API. Used by every test through
 * {@link TestcontainersConfiguration}, and on its own by {@link TestSecureRagJavaApplication}.
 *
 * <p>Unconditional on purpose: every automated test must be guaranteed a non-network {@code ChatModel},
 * regardless of what any other active profile sets {@code spring.ai.model.chat} to. A caller that wants
 * a real chat model instead (see {@link TestSecureRagJavaApplication}'s LM_STUDIO flag) must not add
 * this configuration in the first place, rather than relying on a property this class watches — that
 * would make the test safety net depend on property precedence instead of being structural.
 */
@TestConfiguration(proxyBeanMethods = false)
public class StubChatModelConfiguration {

    @Bean
    StubChatModel chatModel() {
        return new StubChatModel();
    }

}
