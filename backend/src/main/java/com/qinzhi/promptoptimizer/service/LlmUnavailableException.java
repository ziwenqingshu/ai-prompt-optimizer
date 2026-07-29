package com.qinzhi.promptoptimizer.service;

public class LlmUnavailableException extends RuntimeException {
    public LlmUnavailableException() {
        super("模型服务暂时不可用，请稍后重试");
    }
}
