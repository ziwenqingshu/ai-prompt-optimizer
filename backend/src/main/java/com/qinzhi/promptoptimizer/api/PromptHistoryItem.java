package com.qinzhi.promptoptimizer.api;

import java.time.Instant;
import java.util.List;

public record PromptHistoryItem(
        String id,
        Instant createdAt,
        String originalPrompt,
        Scene scene,
        Style style,
        String optimizedPrompt,
        List<String> suggestions,
        String model,
        String mode,
        boolean degraded,
        int qualityScore,
        Integer rating
) {
    public OptimizeResponse asResponse() {
        return new OptimizeResponse(
                optimizedPrompt,
                suggestions,
                model,
                mode,
                degraded,
                id,
                createdAt,
                qualityScore
        );
    }
}
