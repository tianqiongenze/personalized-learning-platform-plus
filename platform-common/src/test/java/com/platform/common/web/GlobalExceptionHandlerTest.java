package com.platform.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.ResponseEntity;

import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GlobalExceptionHandler 单测: 错误码/HTTP 状态映射/不泄露内部细节/traceId 关联。
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest("GET", "/api/v1/test");
        request.setRequestURI("/api/v1/test");
        org.slf4j.MDC.clear();
    }

    @Test
    void illegalArgumentMapsTo400() {
        ResponseEntity<ApiErrorResponse> r =
                handler.handleIllegalArgument(new IllegalArgumentException("bad input"), request);
        assertEquals(400, r.getStatusCode().value());
        assertNotNull(r.getBody());
        assertEquals("ERR_ILLEGAL_ARGUMENT", r.getBody().code());
        assertEquals("bad input", r.getBody().message());
    }

    @Test
    void illegalStateMapsTo409() {
        ResponseEntity<ApiErrorResponse> r =
                handler.handleIllegalState(new IllegalStateException("conflict"), request);
        assertEquals(409, r.getStatusCode().value());
        assertEquals("ERR_ILLEGAL_STATE", r.getBody().code());
    }

    @Test
    void notFoundMapsTo404() {
        ResponseEntity<ApiErrorResponse> r =
                handler.handleNotFound(new NoSuchElementException("missing"), request);
        assertEquals(404, r.getStatusCode().value());
    }

    @Test
    void unexpectedExceptionHidesInternalDetailByDefault() {
        ResponseEntity<ApiErrorResponse> r =
                handler.handleUnexpected(new RuntimeException("secret internal state /etc/passwd"), request);
        assertEquals(500, r.getStatusCode().value());
        assertFalse(r.getBody().message().contains("secret"),
                "默认不暴露内部异常 message(安全合规)");
    }

    @Test
    void exposeMessageFlagShowsDetail() throws Exception {
        var f = GlobalExceptionHandler.class.getDeclaredField("exposeMessage");
        f.setAccessible(true);
        f.set(handler, true);
        ResponseEntity<ApiErrorResponse> r =
                handler.handleUnexpected(new RuntimeException("detail-shown"), request);
        assertEquals(500, r.getStatusCode().value());
        assertTrue(r.getBody().message().contains("detail-shown"));
    }

    @Test
    void traceIdFromMdcIsCarried() {
        org.slf4j.MDC.put("traceId", "tr-123");
        ResponseEntity<ApiErrorResponse> r =
                handler.handleIllegalArgument(new IllegalArgumentException("x"), request);
        assertEquals("tr-123", r.getBody().traceId());
        org.slf4j.MDC.clear();
    }

    @Test
    void traceIdDefaultsToDash() {
        ResponseEntity<ApiErrorResponse> r =
                handler.handleIllegalArgument(new IllegalArgumentException("x"), request);
        assertEquals("-", r.getBody().traceId());
    }

    @Test
    void validationExceptionCarriesFieldDetails() {
        var ex = org.mockito.Mockito.mock(MethodArgumentNotValidException.class);
        var br = new org.springframework.validation.BeanPropertyBindingResult(new Object(), "obj");
        br.addError(new org.springframework.validation.ObjectError("obj", "fieldX 不能为空"));
        org.mockito.Mockito.when(ex.getBindingResult()).thenReturn(br);
        org.mockito.Mockito.when(ex.getMessage()).thenReturn("validation failed");
        ResponseEntity<ApiErrorResponse> r = handler.handleValidation(ex, request);
        assertEquals(400, r.getStatusCode().value());
        assertFalse(r.getBody().message().isBlank());
    }
}
