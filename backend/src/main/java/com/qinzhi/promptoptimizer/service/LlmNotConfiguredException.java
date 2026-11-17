package com.qinzhi.promptoptimizer.service;

public class LlmNotConfiguredException extends RuntimeException {
    public LlmNotConfiguredException() {
        super("模型服务尚未配置");
    }

    public LlmNotConfiguredException(String message) {
        super(message);
    }
}
