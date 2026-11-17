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
import org.springframework.web.client.RestClientException;
import org.springframework.http.MediaType;

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
        String configurationError = properties.configurationError();
        if (configurationError != null) {
            throw new LlmNotConfiguredException(configurationError);
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
                    .uri(properties.completionUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        if (properties.apiKey() != null && !properties.apiKey().isBlank()) {
                            headers.setBearerAuth(properties.apiKey().trim());
                        }
                    })
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode choice = response == null ? objectMapper.createObjectNode() : response.path("choices").path(0);
            if ("length".equals(choice.path("finish_reason").asText())) {
                throw new LlmRequestException("LLM_TRUNCATED", "模型输出被截断，请缩短输入或调整服务商的输出长度限制");
            }
            if ("content_filter".equals(choice.path("finish_reason").asText())
                    || !choice.path("message").path("refusal").asText("").isBlank()) {
                throw new LlmRequestException("LLM_REFUSED", "模型拒绝了本次请求，请修改提示词后重试");
            }
            String content = response == null ? "" : response.path("choices").path(0)
                    .path("message").path("content").asText("").trim();
            if (content.isBlank()) {
                throw new LlmUnavailableException();
            }
            return outputParser.parse(content, properties.model(), "LIVE");
        } catch (RestClientResponseException exception) {
            throw switch (exception.getStatusCode().value()) {
                case 401, 403 -> new LlmRequestException("LLM_AUTH_ERROR", "模型鉴权失败，请检查 API Key 和模型访问权限");
                case 402 -> new LlmRequestException("LLM_BALANCE_ERROR", "模型服务余额不足，请检查服务商账户额度");
                case 404 -> new LlmRequestException("LLM_MODEL_NOT_FOUND", "模型或接口不存在，请检查 LLM_MODEL 和 LLM_BASE_URL");
                case 429 -> new LlmRequestException("LLM_RATE_LIMIT", "模型服务限流或额度耗尽，请稍后重试并检查额度");
                case 400, 422 -> new LlmRequestException("LLM_BAD_REQUEST", "模型不接受当前参数，请检查模型名；若不支持 JSON 模式，设置 LLM_JSON_MODE=false 后重启");
                default -> new LlmRequestException("LLM_UPSTREAM_ERROR", "模型服务暂时不可用，请稍后重试");
            };
        } catch (ResourceAccessException exception) {
            if (isTimeout(exception)) {
                throw new LlmTimeoutException();
            }
            throw new LlmUnavailableException();
        } catch (RestClientException exception) {
            if (isTimeout(exception)) throw new LlmTimeoutException();
            throw new LlmRequestException("LLM_INVALID_RESPONSE", "模型接口未返回有效响应，请检查接口地址和服务商兼容性");
        }
    }

    private OptimizeResponse mockResponse(OptimizeRequest request) {
        String role = switch (request.scene()) {
            case SUMMARY -> "擅长提炼核心观点的文本总结专家";
            case CODE -> "重视可运行性与边界条件的软件工程师";
            case RESUME -> "重视真实经历与岗位匹配度的简历顾问";
            case GENERAL -> "擅长澄清目标与约束的专业助手";
        };
        String style = switch (request.style()) {
            case CONCISE -> "简洁、直击要点";
            case PROFESSIONAL -> "严谨、专业、可执行";
            case DETAILED -> "结构清晰、细节充分";
        };
        String optimizedPrompt = "角色与表达\n你是一位" + role + "，请保持" + style + "的表达。\n\n"
                + "任务\n" + request.originalPrompt().trim()
                + "\n\n工作要求\n1. 明确任务目标和已有信息；缺少必要信息时先提出问题。"
                + "\n2. 遵守给定约束，不编造事实或数据。"
                + "\n3. 使用清晰的标题或列表组织输出，并给出可检查的完成标准。";
        return new OptimizeResponse(optimizedPrompt, java.util.List.of(
                "明确任务目标与使用场景。",
                "补充输入信息、限制条件和禁止项。",
                "规定期望的输出格式。"
        ), properties.model(), "MOCK", false);
    }

    private boolean isTimeout(Throwable exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current instanceof java.net.SocketTimeoutException) return true;
            String message = current.getMessage();
            if (message != null && message.toLowerCase().contains("timed out")) {
                return true;
            }
        }
        return false;
    }
}
