package com.framework.http;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@SpringBootApplication(scanBasePackages = "com.framework")
@EntityScan("com.framework")
public class FrameworkHttpApplication {
    public static void main(String[] args) {
        SpringApplication.run(FrameworkHttpApplication.class, args);
    }
}
