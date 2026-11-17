package com.qinzhi.promptoptimizer.api;

import com.qinzhi.promptoptimizer.service.OptimizationService;
import com.qinzhi.promptoptimizer.service.PromptHistoryStore;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/prompts")
public class PromptController {
    private final OptimizationService optimizationService;
    private final PromptHistoryStore historyStore;

    public PromptController(
            OptimizationService optimizationService,
            PromptHistoryStore historyStore
    ) {
        this.optimizationService = optimizationService;
        this.historyStore = historyStore;
    }

    @PostMapping("/optimize")
    public OptimizeResponse optimize(@Valid @RequestBody OptimizeRequest request) {
        return historyStore.save(request, optimizationService.optimize(request));
    }
}
