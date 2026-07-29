package com.qinzhi.promptoptimizer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "llm")
public record LlmProperties(
        String mode,
        String baseUrl,
        String apiKey,
        String model,
        boolean jsonMode,
        int connectTimeoutSeconds,
        int readTimeoutSeconds
) {
    public boolean isMock() {
        return "mock".equalsIgnoreCase(mode);
    }
}
