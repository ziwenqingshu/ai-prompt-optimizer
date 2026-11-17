package com.qinzhi.promptoptimizer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qinzhi.promptoptimizer.api.ApiExceptionHandler;
import com.qinzhi.promptoptimizer.api.ModelController;
import com.qinzhi.promptoptimizer.config.LlmProperties;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ModelStatusTest {
    private MockMvc api(String mode) {
        var properties = new LlmProperties(mode, "https://example.invalid/v1", "", "example-model", true, 1, 1);
        var mapper = new ObjectMapper();
        var service = new OptimizationService(properties, new PromptTemplateService(), new PromptOutputParser(mapper), mapper);
        return MockMvcBuilders.standaloneSetup(new ModelController(properties, service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test void missingKeyIsVisibleAndProbeFailsBeforeNetwork() throws Exception {
        var mvc = api("live");
        mvc.perform(get("/api/model/status")).andExpect(jsonPath("$.configured").value(false));
        mvc.perform(post("/api/model/test").contentType("application/json").content("{}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("LLM_NOT_CONFIGURED"));
    }

    @Test void mockModeDoesNotClaimARealConnection() throws Exception {
        var mvc = api("mock");
        mvc.perform(get("/api/model/status")).andExpect(jsonPath("$.mode").value("MOCK"));
        mvc.perform(post("/api/model/test").contentType("application/json").content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false));
    }

    @Test void probeRequiresJsonSoCrossOriginSimplePostsCannotTriggerInference() throws Exception {
        api("live").perform(post("/api/model/test").contentType("text/plain").content("{}"))
                .andExpect(status().isUnsupportedMediaType());
    }
}
