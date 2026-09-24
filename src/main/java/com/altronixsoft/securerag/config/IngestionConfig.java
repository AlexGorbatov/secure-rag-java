package com.altronixsoft.securerag.config;

import org.apache.tika.Tika;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IngestionProperties.class)
class IngestionConfig {

    /**
     * Content-type detection from the file's bytes. The Tika facade is thread-safe, so one instance is
     * shared.
     */
    @Bean
    Tika tika() {
        return new Tika();
    }

    @Bean
    TokenTextSplitter tokenTextSplitter(IngestionProperties properties) {
        return TokenTextSplitter.builder()
                .withChunkSize(properties.chunkSizeTokens())
                .build();
    }

}
