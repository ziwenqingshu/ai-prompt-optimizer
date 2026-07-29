package com.qinzhi.promptoptimizer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qinzhi.promptoptimizer.api.OptimizeRequest;
import com.qinzhi.promptoptimizer.api.OptimizeResponse;
import com.qinzhi.promptoptimizer.config.LlmProperties;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class OptimizationService {
    private final LlmProperties properties;
    private final PromptTemplateService promptTemplateService;
    private final PromptOutputParser outputParser;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public OptimizationService(
            LlmProperties properties,
            PromptTemplateService promptTemplateService,
            PromptOutputParser outputParser,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.promptTemplateService = promptTemplateService;
        this.outputParser = outputParser;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeoutSeconds() * 1000);
        requestFactory.setReadTimeout(properties.readTimeoutSeconds() * 1000);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public OptimizeResponse optimize(OptimizeRequest request) {
        if (properties.isMock()) {
            return mockResponse(request);
        }
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new LlmNotConfiguredException();
        }

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", properties.model());
        payload.put("temperature", 0.3);
        ArrayNode messages = payload.putArray("messages");
        messages.addObject().put("role", "system").put("content",
                promptTemplateService.systemPrompt(request.scene(), request.style()));
        messages.addObject().put("role", "user").put("content", request.originalPrompt().trim());
        if (properties.jsonMode()) {
            payload.putObject("response_format").put("type", "json_object");
        }

        try {
            JsonNode response = restClient.post()
                    .uri(normalizeBaseUrl(properties.baseUrl()) + "/chat/completions")
                    .headers(headers -> headers.setBearerAuth(properties.apiKey()))
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class);
            String content = response == null ? "" : response.path("choices").path(0)
                    .path("message").path("content").asText("").trim();
            if (content.isBlank()) {
                throw new LlmUnavailableException();
            }
            return outputParser.parse(content, properties.model(), "LIVE");
        } catch (RestClientResponseException exception) {
            throw new LlmUnavailableException();
        } catch (ResourceAccessException exception) {
            if (isTimeout(exception)) {
                throw new LlmTimeoutException();
            }
            throw new LlmUnavailableException();
        }
    }

    private OptimizeResponse mockResponse(OptimizeRequest request) {
        String optimizedPrompt = "你是一名" + request.scene().name() + "场景专家。请以"
                + request.style().name() + "风格处理以下任务：\n\n"
                + request.originalPrompt().trim()
                + "\n\n请先确认目标、约束和输出格式，再给出结果。";
        return new OptimizeResponse(optimizedPrompt, java.util.List.of(
                "明确任务目标与使用场景。",
                "补充输入信息、限制条件和禁止项。",
                "规定期望的输出格式。"
        ), properties.model(), "MOCK", false);
    }

    private String normalizeBaseUrl(String baseUrl) {
        return baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
    }

    private boolean isTimeout(Throwable exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message != null && message.toLowerCase().contains("timed out")) {
                return true;
            }
        }
        return false;
    }
}
