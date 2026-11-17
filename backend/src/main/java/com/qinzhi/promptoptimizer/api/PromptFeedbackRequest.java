package com.qinzhi.promptoptimizer.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PromptFeedbackRequest(
        @NotNull(message = "评分不能为空")
        @Min(value = 1, message = "评分最低为 1")
        @Max(value = 5, message = "评分最高为 5")
        Integer rating
) {
}
