package com.qinzhi.promptoptimizer.api;

import com.qinzhi.promptoptimizer.service.OptimizationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/prompts")
public class PromptController {
    private final OptimizationService optimizationService;

    public PromptController(OptimizationService optimizationService) {
        this.optimizationService = optimizationService;
    }

    @PostMapping("/optimize")
    public OptimizeResponse optimize(@Valid @RequestBody OptimizeRequest request) {
        return optimizationService.optimize(request);
    }
}
