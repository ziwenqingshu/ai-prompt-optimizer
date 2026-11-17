package com.qinzhi.promptoptimizer.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PromptQualityScorerTest {
    private final PromptQualityScorer scorer = new PromptQualityScorer();

    @Test
    void rewardsPromptsWithGoalsConstraintsAndOutputFormat() {
        int score = scorer.score("请明确目标和约束，并以 JSON 格式输出结果。");

        assertThat(score).isGreaterThanOrEqualTo(80);
    }

    @Test
    void returnsZeroForBlankPrompt() {
        assertThat(scorer.score("  ")).isZero();
    }
}
