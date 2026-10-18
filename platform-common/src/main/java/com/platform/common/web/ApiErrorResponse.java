package com.platform.common.web;

/**
 * 统一错误响应体(GB/T 9385 / GB/T 25000.51 易用性与容错性要求)。
 * 对外只暴露 traceId + 可读 message, 不泄露内部堆栈与 SQL。
 */
public record ApiErrorResponse(
    boolean success,
    String code,
    String message,
    String path,
    String traceId,
    long timestamp
) {
    public static ApiErrorResponse of(String code, String message, String path, String traceId) {
        return new ApiErrorResponse(false, code, message, path, traceId, System.currentTimeMillis());
    }
}
