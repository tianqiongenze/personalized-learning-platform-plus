package com.platform.teaching.interfaces.rest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;

import java.util.Map;

/**
 * 知识追踪(KT)代理: teaching-service 对外统一暴露 /kt/*, 内部转发 kt-service(infra)。
 * BKT(EM 参数学习) + DKT(LSTM) 融合掌握度, 对接申报书研究内容②。
 */
@RestController
@RequestMapping("/kt")
public class KtProxyController {

    private final RestClient kt;

    public KtProxyController(@Value("${app.kt.service-url:http://kt-service.infra:8086}") String ktUrl) {
        // 超时: 掌握度查询为预计算索引读取, 2s 足够; 防止下游异常拖垮 tomcat 线程
        this.kt = RestClient.builder()
                .baseUrl(ktUrl)
                .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
                    setConnectTimeout(1000);
                    setReadTimeout(2000);
                }})
                .build();
    }

    private Map<String, Object> fallback(Exception e) {
        return Map.of("available", false, "reason", "kt-service 不可用(熔断降级)");
    }

    @GetMapping("/mastery")
    @CircuitBreaker(name = "ktService", fallbackMethod = "fallback")
    @RateLimiter(name = "ktProxy")
    public ResponseEntity<Map<String, Object>> mastery(
            @RequestParam String studentId,
            @RequestParam(required = false) String courseId,
            @RequestParam(defaultValue = "10") int topWeak) {
        Map<String, Object> result = kt.get()
                .uri(uri -> uri.path("/kt/mastery")
                        .queryParam("studentId", studentId)
                        .queryParam("courseId", courseId)
                        .queryParam("top_weak", topWeak).build())
                .retrieve().body(Map.class);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/predict")
    @CircuitBreaker(name = "ktService", fallbackMethod = "fallback")
    @RateLimiter(name = "ktProxy")
    public ResponseEntity<Map<String, Object>> predict(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = kt.post().uri("/kt/predict")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(body).retrieve().body(Map.class);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/model/info")
    public ResponseEntity<Map<String, Object>> modelInfo() {
        Map<String, Object> result = kt.get().uri("/model/info").retrieve().body(Map.class);
        return ResponseEntity.ok(result);
    }
}
