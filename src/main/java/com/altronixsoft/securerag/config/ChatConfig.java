package com.altronixsoft.securerag.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ChatConfig {

    /**
     * Built from the auto-configured builder, so the provider (OpenAI, Azure, a test stub) is chosen by
     * configuration alone. No default advisors: retrieval is done explicitly by ChatService with a
     * per-caller access filter.
     */
    @Bean
    ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }

}
