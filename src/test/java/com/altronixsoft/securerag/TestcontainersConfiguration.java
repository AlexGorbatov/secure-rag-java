package com.altronixsoft.securerag;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /**
     * The test profile has no real chat model (spring.ai.model.chat=none). This stub takes its place,
     * so the application context starts and no test can call a paid API.
     */
    @Bean
    StubChatModel chatModel() {
        return new StubChatModel();
    }

    @Bean
    @ServiceConnection
    PostgreSQLContainer pgvectorContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg17")
                .asCompatibleSubstituteFor("postgres"));
    }

}
