package com.platform.ops.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Prometheus 数据访问: 统一运维平台所有指标型视图的统一查询入口
 * (主机负载/内存/磁盘/网络/线程、JVM、熔断限流状态、缓存命中率、Pulsar 积压)
 */
@Service
public class PrometheusService {

    private final RestClient client;

    public PrometheusService(@Value("${app.endpoints.prometheus}") String prometheusUrl) {
        this.client = RestClient.builder().baseUrl(prometheusUrl).build();
    }

    @SuppressWarnings("unchecked")
    public Object query(String promql) {
        // RestClient uri 模板 {q} 展开时会对变量值再做一次 encode —— 传入预编码串
        // 会导致双重编码(%5B→%255B), Prometheus 解码一次后仍是 %7B 等 PromQL 非法
        // 字符(报 1:4 bad number)。正确做法: 传原始 PromQL, 由模板展开编码一次。
        return client.get()
                .uri("/api/v1/query?query={q}", promql)
                .retrieve().body(Map.class);
    }

    @SuppressWarnings("unchecked")
    public Object range(String promql, String start, String end, String step) {
        return client.get()
                .uri("/api/v1/query_range?query={q}&start={s}&end={e}&step={st}",
                        promql, start, end, step)
                .retrieve().body(Map.class);
    }

    /** 告警规则当前触发状态 */
    public Object alerts() {
        return client.get().uri("/api/v1/alerts").retrieve().body(Map.class);
    }

    // ---------- 面向运维平台的常用 PromQL 封装 ----------

    public List<Map<String, String>> hostMetrics() {
        String[] queries = {
                "node_load1", "node_load5",
                "(1 - node_memory_MemAvailable_bytes/node_memory_MemTotal_bytes) * 100",
                "(1 - node_filesystem_avail_bytes{fstype!~'tmpfs|overlay'}/node_filesystem_size_bytes{fstype!~'tmpfs|overlay'})*100",
                "rate(node_network_receive_bytes_total{device!~'lo|veth.*'}[5m])",
                "rate(node_network_transmit_bytes_total{device!~'lo|veth.*'}[5m])"
        };
        return java.util.Arrays.stream(queries)
                .map(this::query)
                .filter(Map.class::isInstance)
                .map(q -> (Map<String, Object>) q)
                .map(m -> Map.<String, String>of(
                        "query", String.valueOf(((Map<?, ?>) m).get("data")),
                        "result", "ok"))
                .toList();
    }

    /** 服务/API 实时状态: 正常率/限流(429)/熔断状态(Resilience4j)/P95延迟 */
    public Map<String, Object> serviceRuntimeStatus() {
        return Map.of(
                "serviceHealth", query("up{namespace=\"platform-plus\"}"),
                "rateLimited429", query("sum(rate(nginx_ingress_controller_requests{status='429'}[5m])) by (service)"),
                "circuitBreakerStates", query("resilience4j_circuitbreaker_state"),
                "rateLimiterThrottled", query("sum(rate(resilience4j_ratelimiter_available_permits[5m])) by (name)"),
                "p95Latency", query("histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le, application))"),
                "errorRate5xx", query("sum(rate(http_server_requests_seconds_count{status='500'}[5m])) by (application)")
        );
    }
}
