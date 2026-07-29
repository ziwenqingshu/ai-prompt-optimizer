package com.qinzhi.promptoptimizer.api;

import java.util.List;

public record OptimizeResponse(
        String optimizedPrompt,
        List<String> suggestions,
        String model,
        String mode,
        boolean degraded
) {
}
