package com.qinzhi.promptoptimizer.service;

public class LlmTimeoutException extends RuntimeException {
    public LlmTimeoutException() {
        super("模型响应超时，请重试");
    }
}
