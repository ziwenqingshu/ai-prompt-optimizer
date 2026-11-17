package com.qinzhi.promptoptimizer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qinzhi.promptoptimizer.api.OptimizeRequest;
import com.qinzhi.promptoptimizer.api.OptimizeResponse;
import com.qinzhi.promptoptimizer.api.Scene;
import com.qinzhi.promptoptimizer.api.Style;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PromptHistoryStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void savesReloadsAndDeletesHistory() throws Exception {
        PromptHistoryStore store = newStore(tempDir);
        OptimizeRequest request = new OptimizeRequest("帮我写登录接口", Scene.CODE, Style.PROFESSIONAL);
        OptimizeResponse response = new OptimizeResponse(
                "请先确认目标、约束，并以 JSON 格式输出登录接口设计。",
                List.of("明确目标", "补充约束", "规定格式"),
                "test-model",
                "MOCK",
                false
        );

        OptimizeResponse saved = store.save(request, response);

        assertThat(saved.historyId()).isNotBlank();
        assertThat(saved.qualityScore()).isGreaterThan(0);
        assertThat(Files.exists(tempDir.resolve("prompt-history.json"))).isTrue();

        PromptHistoryStore reloaded = newStore(tempDir);
        assertThat(reloaded.list()).hasSize(1);
        assertThat(reloaded.list().getFirst().originalPrompt()).isEqualTo("帮我写登录接口");
        assertThat(reloaded.rate(saved.historyId(), 5).rating()).isEqualTo(5);
        assertThat(reloaded.delete(saved.historyId())).isTrue();
        assertThat(reloaded.list()).isEmpty();
    }

    private PromptHistoryStore newStore(Path path) {
        PromptHistoryStore store = new PromptHistoryStore(new ObjectMapper(), new PromptQualityScorer(), path);
        store.initialize();
        return store;
    }
}
