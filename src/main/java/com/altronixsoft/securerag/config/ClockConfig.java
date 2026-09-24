package com.altronixsoft.securerag.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Time is a dependency: services take it from this bean ({@code Instant.now(clock)}) so tests can
 * replace it with {@link Clock#fixed}.
 */
@Configuration(proxyBeanMethods = false)
class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

}
