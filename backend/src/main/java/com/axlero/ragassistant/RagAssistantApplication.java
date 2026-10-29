
package com.axlero.ragassistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RagAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagAssistantApplication.class, args);
    }
}

