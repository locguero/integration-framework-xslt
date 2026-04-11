package com.framework.http;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.framework")
public class FrameworkHttpApplication {
    public static void main(String[] args) {
        SpringApplication.run(FrameworkHttpApplication.class, args);
    }
}
