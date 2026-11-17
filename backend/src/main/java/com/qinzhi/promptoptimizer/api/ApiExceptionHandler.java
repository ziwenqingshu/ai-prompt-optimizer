package com.qinzhi.promptoptimizer.api;

import com.qinzhi.promptoptimizer.service.LlmNotConfiguredException;
import com.qinzhi.promptoptimizer.service.LlmTimeoutException;
import com.qinzhi.promptoptimizer.service.LlmUnavailableException;
import com.qinzhi.promptoptimizer.service.LlmRequestException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> unsupportedMediaType() {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "请使用 application/json 请求格式", "VALIDATION_ERROR");
    }

    @ExceptionHandler(LlmRequestException.class)
    public ResponseEntity<ApiError> modelRequest(LlmRequestException exception) {
        return error(HttpStatus.BAD_GATEWAY, exception.getMessage(), exception.code());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception) {
        FieldError fieldError = exception.getBindingResult().getFieldError();
        String message = fieldError == null ? "请求参数不合法" : fieldError.getDefaultMessage();
        return error(HttpStatus.BAD_REQUEST, message, "VALIDATION_ERROR");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> malformedRequest() {
        return error(HttpStatus.BAD_REQUEST, "请求参数格式不正确", "VALIDATION_ERROR");
    }

    @ExceptionHandler(LlmNotConfiguredException.class)
    public ResponseEntity<ApiError> notConfigured(LlmNotConfiguredException exception) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage(), "LLM_NOT_CONFIGURED");
    }

    @ExceptionHandler(LlmTimeoutException.class)
    public ResponseEntity<ApiError> timeout(LlmTimeoutException exception) {
        return error(HttpStatus.GATEWAY_TIMEOUT, exception.getMessage(), "LLM_TIMEOUT");
    }

    @ExceptionHandler(LlmUnavailableException.class)
    public ResponseEntity<ApiError> unavailable(LlmUnavailableException exception) {
        return error(HttpStatus.BAD_GATEWAY, exception.getMessage(), "LLM_UPSTREAM_ERROR");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unknown() {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "服务异常，请稍后重试", "INTERNAL_ERROR");
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String message, String code) {
        return ResponseEntity.status(status).body(new ApiError(message, code));
    }
}
