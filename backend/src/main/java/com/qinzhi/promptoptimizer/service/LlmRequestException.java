package com.qinzhi.promptoptimizer.service;

/** Safe, actionable messages; never expose upstream response bodies or credentials. */
public class LlmRequestException extends RuntimeException {
    private final String code;

    public LlmRequestException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() { return code; }
}
