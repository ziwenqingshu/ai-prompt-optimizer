package com.qinzhi.promptoptimizer.api;

import java.util.List;
import java.time.Instant;

public record OptimizeResponse(
        String optimizedPrompt,
        List<String> suggestions,
        String model,
        String mode,
        boolean degraded,
        String historyId,
        Instant createdAt,
        int qualityScore
) {
    public OptimizeResponse(
            String optimizedPrompt,
            List<String> suggestions,
            String model,
            String mode,
            boolean degraded
    ) {
        this(optimizedPrompt, suggestions, model, mode, degraded, null, null, 0);
    }

    public OptimizeResponse withHistory(String id, Instant timestamp, int score) {
        return new OptimizeResponse(optimizedPrompt, suggestions, model, mode, degraded, id, timestamp, score);
    }
}
