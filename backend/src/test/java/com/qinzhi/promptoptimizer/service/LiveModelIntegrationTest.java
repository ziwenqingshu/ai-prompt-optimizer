package com.qinzhi.promptoptimizer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qinzhi.promptoptimizer.api.*;
import com.qinzhi.promptoptimizer.config.LlmProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"llm.mode=live", "llm.api-key=test-secret-never-return", "llm.model=fixture-model",
        "llm.read-timeout-seconds=1", "llm.connect-timeout-seconds=1"})
@AutoConfigureMockMvc
class LiveModelIntegrationTest {
    static final ObjectMapper JSON = new ObjectMapper();
    static final HttpServer UPSTREAM;
    static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    static volatile int upstreamStatus;
    static volatile String response;
    static volatile long delay;
    static volatile JsonNode captured;
    static volatile String authorization;
    @TempDir static Path dataDir;
    @Autowired MockMvc mvc;
    @Autowired PromptHistoryStore history;

    static {
        try {
            UPSTREAM = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            UPSTREAM.createContext("/v1/chat/completions", exchange -> {
                int currentStatus = upstreamStatus;
                String currentResponse = response;
                captured = JSON.readTree(exchange.getRequestBody());
                authorization = exchange.getRequestHeaders().getFirst("Authorization");
                try { Thread.sleep(delay); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                byte[] bytes = currentResponse.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                try {
                    exchange.sendResponseHeaders(currentStatus, bytes.length);
                    exchange.getResponseBody().write(bytes);
                } finally { exchange.close(); }
            });
            UPSTREAM.setExecutor(EXECUTOR);
            UPSTREAM.start();
        } catch (Exception exception) { throw new ExceptionInInitializerError(exception); }
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("llm.base-url", () -> "http://127.0.0.1:" + UPSTREAM.getAddress().getPort() + "/v1/");
        registry.add("app.data-dir", () -> dataDir.toString());
    }
    @BeforeEach void setup() throws Exception {
        upstreamStatus = 200; delay = 0; captured = null;
        response = completion("{\"optimizedPrompt\":\"明确目标与约束，以 Markdown 格式输出实施步骤。\",\"suggestions\":[\"明确目标\",\"增加约束\",\"指定格式\"]}", "stop");
        for (var item : history.list()) history.delete(item.id());
    }
    @AfterAll static void stop() { UPSTREAM.stop(0); EXECUTOR.shutdownNow(); }
    static String completion(String content, String finish) throws Exception {
        var root = JSON.createObjectNode();
        var choice = root.putArray("choices").addObject();
        choice.put("finish_reason", finish);
        choice.putObject("message").put("content", content);
        return JSON.writeValueAsString(root);
    }
    org.springframework.test.web.servlet.ResultActions optimize() throws Exception {
        return mvc.perform(post("/api/prompts/optimize").contentType("application/json")
                .content("{\"originalPrompt\":\" 写一个 Java 排序函数 \",\"scene\":\"CODE\",\"style\":\"PROFESSIONAL\"}"));
    }
    @Test void fullWorkflowSendsCorrectRequestAndPersistsFeedback() throws Exception {
        String body = optimize().andExpect(status().isOk()).andExpect(jsonPath("$.mode").value("LIVE"))
                .andExpect(jsonPath("$.degraded").value(false)).andReturn().getResponse().getContentAsString();
        assertThat(captured.path("model").asText()).isEqualTo("fixture-model");
        assertThat(captured.path("messages").get(0).path("role").asText()).isEqualTo("system");
        assertThat(captured.path("messages").get(1).path("content").asText()).isEqualTo("写一个 Java 排序函数");
        assertThat(captured.path("response_format").path("type").asText()).isEqualTo("json_object");
        assertThat(authorization).isEqualTo("Bearer test-secret-never-return");
        String id = JSON.readTree(body).path("historyId").asText();
        mvc.perform(put("/api/prompts/history/" + id + "/feedback").contentType("application/json").content("{\"rating\":5}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rating").value(5));
        var restored = new PromptHistoryStore(JSON, new PromptQualityScorer(), dataDir);
        restored.initialize();
        assertThat(restored.list().getFirst().rating()).isEqualTo(5);
        mvc.perform(get("/api/prompts/history")).andExpect(jsonPath("$[0].id").value(id));
        mvc.perform(delete("/api/prompts/history/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/prompts/history")).andExpect(content().json("[]"));
    }
    @Test void statusAndProbeNeverExposeKeyOrWriteHistory() throws Exception {
        mvc.perform(get("/api/model/status")).andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("test-secret"))));
        mvc.perform(post("/api/model/test").contentType("application/json").content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(true));
        assertThat(history.list()).isEmpty();
    }
    @ParameterizedTest
    @CsvSource({"401,LLM_AUTH_ERROR", "403,LLM_AUTH_ERROR", "402,LLM_BALANCE_ERROR", "404,LLM_MODEL_NOT_FOUND",
            "429,LLM_RATE_LIMIT", "400,LLM_BAD_REQUEST", "422,LLM_BAD_REQUEST", "500,LLM_UPSTREAM_ERROR"})
    void upstreamErrorsAreActionableAndNeverStored(int statusCode, String code) throws Exception {
        upstreamStatus = statusCode; response = "{\"error\":\"test-secret-never-return\"}";
        optimize().andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value(code))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("test-secret"))));
        assertThat(history.list()).isEmpty();
    }
    @Test void timeoutIsReportedWithoutMockFallback() throws Exception {
        delay = 1500;
        optimize().andExpect(status().isGatewayTimeout()).andExpect(jsonPath("$.code").value("LLM_TIMEOUT"));
        assertThat(history.list()).isEmpty();
    }
    @ParameterizedTest @CsvSource({"length,LLM_TRUNCATED", "content_filter,LLM_REFUSED"})
    void incompleteResponsesAreRejected(String reason, String code) throws Exception {
        response = completion("partial", reason);
        optimize().andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value(code));
        assertThat(history.list()).isEmpty();
    }
    @Test void malformedEnvelopeIsReported() throws Exception {
        response = "<html>upstream unavailable</html>";
        optimize().andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("LLM_INVALID_RESPONSE"));
        assertThat(history.list()).isEmpty();
    }
    @Test void emptyCompletionIsRejected() throws Exception {
        response = completion("", "stop");
        optimize().andExpect(status().isBadGateway());
        assertThat(history.list()).isEmpty();
    }
    @Test void plainTextOutputIsHonestlyMarkedDegraded() throws Exception {
        response = completion("请明确目标与约束，并规定输出格式。", "stop");
        optimize().andExpect(status().isOk()).andExpect(jsonPath("$.degraded").value(true));
    }
    @Test void localKeylessModelAndDisabledJsonModeWork() {
        var properties = new LlmProperties("live", "http://127.0.0.1:" + UPSTREAM.getAddress().getPort()
                + "/v1/chat/completions/", "", "fixture-model", false, 1, 1);
        var service = new OptimizationService(properties, new PromptTemplateService(), new PromptOutputParser(JSON), JSON);
        assertThat(service.optimize(new OptimizeRequest("测试", Scene.GENERAL, Style.CONCISE)).mode()).isEqualTo("LIVE");
        assertThat(authorization).isNull();
        assertThat(captured.has("response_format")).isFalse();
    }
    @ParameterizedTest
    @CsvSource({"live,https://example.com/v1", "typo,http://127.0.0.1/v1", "live,ftp://example.com", "live,https://example.com?key=secret"})
    void invalidConfigurationFailsBeforeNetwork(String mode, String base) {
        var properties = new LlmProperties(mode, base, "", "fixture-model", false, 1, 1);
        var service = new OptimizationService(properties, new PromptTemplateService(), new PromptOutputParser(JSON), JSON);
        assertThatThrownBy(() -> service.optimize(new OptimizeRequest("测试", Scene.GENERAL, Style.CONCISE)))
                .isInstanceOf(LlmNotConfiguredException.class);
        assertThat(captured).isNull();
    }
    @Test void invalidInputDoesNotCallModel() throws Exception {
        mvc.perform(post("/api/prompts/optimize").contentType("application/json")
                .content("{\"originalPrompt\":\" \",\"scene\":\"CODE\",\"style\":\"PROFESSIONAL\"}"))
                .andExpect(status().isBadRequest());
        assertThat(captured).isNull();
    }
}
