package com.framework.http;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.framework")
@EntityScan("com.framework")
@EnableJpaRepositories(basePackages = "com.framework")
public class FrameworkHttpApplication {
    public static void main(String[] args) {
        SpringApplication.run(FrameworkHttpApplication.class, args);
    }
}
