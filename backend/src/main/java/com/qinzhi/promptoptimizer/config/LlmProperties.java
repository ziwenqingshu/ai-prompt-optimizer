package com.qinzhi.promptoptimizer.config;

import java.net.URI;
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

    public String configurationError() {
        if (isMock()) return null;
        if (!"live".equalsIgnoreCase(mode)) return "LLM_MODE 只能为 mock 或 live";
        if (model == null || model.isBlank()) return "请配置 LLM_MODEL";
        try {
            URI uri = URI.create(baseUrl == null ? "" : baseUrl.trim());
            if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null) {
                return "LLM_BASE_URL 必须为不含凭据、查询参数的 HTTP(S) 地址";
            }
            boolean local = java.util.Set.of("localhost", "127.0.0.1", "[::1]").contains(uri.getHost());
            if (!local && (apiKey == null || apiKey.isBlank())) return "请在 .env 中设置 LLM_API_KEY 后重启服务";
        } catch (IllegalArgumentException exception) {
            return "LLM_BASE_URL 格式不正确";
        }
        return null;
    }

    public String completionUrl() {
        String base = baseUrl.trim().replaceAll("/+$", "");
        return base.endsWith("/chat/completions") ? base : base + "/chat/completions";
    }
}
