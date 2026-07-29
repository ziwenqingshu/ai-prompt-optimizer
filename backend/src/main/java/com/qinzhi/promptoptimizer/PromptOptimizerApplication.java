package com.qinzhi.promptoptimizer;

import com.qinzhi.promptoptimizer.config.LlmProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(LlmProperties.class)
public class PromptOptimizerApplication {
    public static void main(String[] args) {
        SpringApplication.run(PromptOptimizerApplication.class, args);
    }
}
