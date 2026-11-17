package com.qinzhi.promptoptimizer.service;

import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * A transparent, deterministic baseline score. It is a heuristic signal for
 * comparing saved versions, not a substitute for a task-specific evaluator.
 */
@Component
public class PromptQualityScorer {
    public int score(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return 0;
        }

        String normalized = prompt.toLowerCase(Locale.ROOT);
        int score = 35;
        if (prompt.length() >= 40) {
            score += 10;
        }
        if (containsAny(normalized, "目标", "目的", "task", "goal")) {
            score += 15;
        }
        if (containsAny(normalized, "约束", "限制", "禁止", "constraint", "must", "不要")) {
            score += 15;
        }
        if (containsAny(normalized, "格式", "输出", "结构", "format", "output")) {
            score += 15;
        }
        return Math.min(score, 100);
    }

    private boolean containsAny(String value, String... terms) {
        for (String term : terms) {
            if (value.contains(term)) {
                return true;
            }
        }
        return false;
    }
}
