package com.platform.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * 全局异常处理器 —— 重构前所有服务均无 @ControllerAdvice, 异常直接抛 500 + 堆栈。
 * 本类由 {@link WebErrorAutoConfiguration} 自动装配, 对全部 Java 服务生效。
 *
 * 设计要点(对应审核清单):
 *  1. 统一错误信封, 不泄露内部细节(安全合规)
 *  2. 携带 traceId, 与 Loki / Jaeger 日志关联(可观测)
 *  3. 业务异常 -> 4xx, 未预期异常 -> 500(容错性)
 *  4. 参数校验失败返回字段级明细(易用性)
 */
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 是否在响应体中暴露异常 message(生产环境建议 false, 只暴露 traceId) */
    @Value("${app.error.expose-message:false}")
    private boolean exposeMessage;

    private String traceId() {
        String tid = MDC.get("traceId");
        if (tid == null) {
            tid = MDC.get("requestId");
        }
        return tid == null ? "-" : tid;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest req) {
        return build("ERR_ILLEGAL_ARGUMENT", HttpStatus.BAD_REQUEST, ex, req, true);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalState(
            IllegalStateException ex, HttpServletRequest req) {
        return build("ERR_ILLEGAL_STATE", HttpStatus.CONFLICT, ex, req, true);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(
            NoSuchElementException ex, HttpServletRequest req) {
        return build("ERR_NOT_FOUND", HttpStatus.NOT_FOUND, ex, req, true);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest req) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        ApiErrorResponse body = ApiErrorResponse.of(
                "ERR_VALIDATION", "参数校验失败: " + detail,
                req.getRequestURI(), traceId());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiErrorResponse> handleBind(BindException ex, HttpServletRequest req) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        ApiErrorResponse body = ApiErrorResponse.of(
                "ERR_BIND", "请求参数绑定失败: " + detail,
                req.getRequestURI(), traceId());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest req) {
        return build("ERR_MALFORMED_JSON", HttpStatus.BAD_REQUEST, ex, req, true);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParam(
            MissingServletRequestParameterException ex, HttpServletRequest req) {
        return build("ERR_MISSING_PARAM", HttpStatus.BAD_REQUEST, ex, req, true);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        return build("ERR_TYPE_MISMATCH", HttpStatus.BAD_REQUEST, ex, req, true);
    }

    /** 兜底: 任何未预期异常都不外泄堆栈, 只记 ERROR 日志并返回 traceId */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest req) {
        log.error("[traceId={}] unhandled exception at {}: {}",
                traceId(), req.getRequestURI(), ex.getMessage(), ex);
        String msg = exposeMessage ? ex.getMessage() : "服务内部错误, 请联系管理员并提供 traceId=" + traceId();
        ApiErrorResponse body = ApiErrorResponse.of(
                "ERR_INTERNAL", msg, req.getRequestURI(), traceId());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private ResponseEntity<ApiErrorResponse> build(String code, HttpStatus status,
                                                   Exception ex,
                                                   HttpServletRequest req,
                                                   boolean business) {
        if (!business) {
            log.error("[traceId={}] {} at {}", traceId(), code, req.getRequestURI(), ex);
        }
        String msg = ex.getMessage() == null ? status.getReasonPhrase() : ex.getMessage();
        ApiErrorResponse body = ApiErrorResponse.of(code, msg, req.getRequestURI(), traceId());
        return ResponseEntity.status(status).body(body);
    }
}
