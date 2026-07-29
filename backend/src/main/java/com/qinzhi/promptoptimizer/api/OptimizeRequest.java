package com.qinzhi.promptoptimizer.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OptimizeRequest(
        @NotBlank(message = "原始提示词不能为空")
        @Size(max = 2000, message = "原始提示词不能超过 2000 个字符")
        String originalPrompt,
        @NotNull(message = "使用场景不能为空") Scene scene,
        @NotNull(message = "输出风格不能为空") Style style
) {
}
