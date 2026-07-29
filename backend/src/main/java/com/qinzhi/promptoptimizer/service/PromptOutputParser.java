package com.qinzhi.promptoptimizer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qinzhi.promptoptimizer.api.OptimizeResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class PromptOutputParser {
    private static final List<String> DEFAULT_SUGGESTIONS = List.of(
            "明确任务目标、使用场景和已有上下文。",
            "补充边界条件、限制项和禁止项。",
            "规定期望输出格式，并根据结果迭代提示词。"
    );
    private final ObjectMapper objectMapper;

    public PromptOutputParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public OptimizeResponse parse(String raw, String model, String mode) {
        for (String candidate : List.of(raw, removeCodeFence(raw), extractFirstObject(raw))) {
            JsonNode node = parseObject(candidate);
            if (node == null) {
                continue;
            }
            String optimizedPrompt = text(node, "optimizedPrompt");
            if (optimizedPrompt == null) {
                continue;
            }
            List<String> suggestions = normalizeSuggestions(node.path("suggestions"));
            boolean degraded = !node.path("suggestions").isArray() || node.path("suggestions").size() != 3;
            return new OptimizeResponse(optimizedPrompt, suggestions, model, mode, degraded);
        }

        return new OptimizeResponse(raw.trim(), DEFAULT_SUGGESTIONS, model, mode, true);
    }

    private JsonNode parseObject(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(candidate.trim());
            return node != null && node.isObject() ? node : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() && !value.asText().isBlank() ? value.asText().trim() : null;
    }

    private List<String> normalizeSuggestions(JsonNode source) {
        Set<String> unique = new LinkedHashSet<>();
        if (source.isArray()) {
            source.forEach(item -> {
                if (item.isTextual() && !item.asText().isBlank()) {
                    unique.add(item.asText().trim());
                }
            });
        }
        List<String> result = new ArrayList<>(unique);
        for (String fallback : DEFAULT_SUGGESTIONS) {
            if (result.size() == 3) {
                break;
            }
            if (!result.contains(fallback)) {
                result.add(fallback);
            }
        }
        return result.subList(0, 3);
    }

    private String removeCodeFence(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (!value.startsWith("```")) {
            return value;
        }
        int firstNewline = value.indexOf('\n');
        if (firstNewline < 0) {
            return value;
        }
        value = value.substring(firstNewline + 1);
        return value.endsWith("```") ? value.substring(0, value.length() - 3).trim() : value.trim();
    }

    private String extractFirstObject(String raw) {
        if (raw == null) {
            return "";
        }
        int start = -1;
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int index = 0; index < raw.length(); index++) {
            char character = raw.charAt(index);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (character == '\\') {
                    escaped = true;
                } else if (character == '"') {
                    inString = false;
                }
                continue;
            }
            if (character == '"') {
                inString = true;
            } else if (character == '{') {
                if (depth++ == 0) {
                    start = index;
                }
            } else if (character == '}' && depth > 0 && --depth == 0) {
                return raw.substring(start, index + 1);
            }
        }
        return "";
    }
}
