package com.qinzhi.promptoptimizer.api;

import com.qinzhi.promptoptimizer.config.LlmProperties;
import com.qinzhi.promptoptimizer.service.OptimizationService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/model")
public class ModelController {
    private final LlmProperties properties;
    private final OptimizationService service;

    public ModelController(LlmProperties properties, OptimizationService service) {
        this.properties = properties;
        this.service = service;
    }

    public record Status(String mode, String model, boolean configured, String message) { }
    public record ConnectionTest(boolean available, String mode, String model, boolean degraded, long latencyMs) { }

    @GetMapping("/status")
    public Status status() {
        String error = properties.configurationError();
        return new Status(properties.isMock() ? "MOCK" : "LIVE", properties.model(), error == null,
                properties.isMock() ? "模拟模式：不会调用真实模型" : error == null
                        ? "配置已就绪，尚未验证连接" : error);
    }

    @PostMapping(value = "/test", consumes = "application/json")
    public ConnectionTest test() {
        if (properties.isMock()) return new ConnectionTest(false, "MOCK", properties.model(), false, 0);
        long started = System.nanoTime();
        OptimizeResponse response = service.optimize(new OptimizeRequest(
                "用一句话介绍提示词优化的作用", Scene.GENERAL, Style.CONCISE));
        return new ConnectionTest(true, response.mode(), response.model(), response.degraded(),
                (System.nanoTime() - started) / 1_000_000);
    }
}
