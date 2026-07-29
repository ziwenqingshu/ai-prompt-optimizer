package com.qinzhi.promptoptimizer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qinzhi.promptoptimizer.api.OptimizeResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PromptOutputParserTest {
    private final PromptOutputParser parser = new PromptOutputParser(new ObjectMapper());

    @Test
    void parsesJsonInsideCodeFenceAndNormalizesSuggestions() {
        String raw = """
                ```json
                {"optimizedPrompt":"请总结输入内容", "suggestions":["给出目标"]}
                ```
                """;

        OptimizeResponse response = parser.parse(raw, "test-model", "LIVE");

        assertThat(response.optimizedPrompt()).isEqualTo("请总结输入内容");
        assertThat(response.suggestions()).hasSize(3);
        assertThat(response.degraded()).isTrue();
    }

    @Test
    void extractsBalancedJsonObjectWithoutBreakingBracesInsideText() {
        String raw = "说明文字 {\"optimizedPrompt\":\"处理 {name} 变量\",\"suggestions\":[\"明确变量含义\",\"补充输入\",\"限定输出\"]} 结束";

        OptimizeResponse response = parser.parse(raw, "test-model", "LIVE");

        assertThat(response.optimizedPrompt()).isEqualTo("处理 {name} 变量");
        assertThat(response.suggestions()).containsExactly("明确变量含义", "补充输入", "限定输出");
        assertThat(response.degraded()).isFalse();
    }

    @Test
    void fallsBackWhenModelTextIsNotJson() {
        OptimizeResponse response = parser.parse("请按以下步骤改写提示词。", "test-model", "LIVE");

        assertThat(response.optimizedPrompt()).isEqualTo("请按以下步骤改写提示词。");
        assertThat(response.suggestions()).hasSize(3);
        assertThat(response.degraded()).isTrue();
    }
}
