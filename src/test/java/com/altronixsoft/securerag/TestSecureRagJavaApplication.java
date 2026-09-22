package com.altronixsoft.securerag;

import org.springframework.boot.SpringApplication;

public class TestSecureRagJavaApplication {

    public static void main(String[] args) {
        SpringApplication.from(SecureRagJavaApplication::main).with(TestcontainersConfiguration.class).withAdditionalProfiles("test").run(args);
    }

}
