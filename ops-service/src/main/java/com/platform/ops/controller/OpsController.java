package com.platform.ops.controller;

import com.platform.ops.service.GrafanaService;
import com.platform.ops.service.KubernetesInspectionService;
import com.platform.ops.service.LogsService;
import com.platform.ops.service.PrometheusService;
import com.platform.ops.service.PulsarAdminService;
import com.platform.ops.service.RedisClusterInspectionService;
import com.platform.ops.service.VectorGraphInspectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * 统一运维监测平台 API(前端 ops-web 的全部数据出口)
 * 权限: 网关注入的 JWT; 仅 ADMIN 角色可访问(见 SecurityConfig)
 */
@RestController
@RequestMapping("/")
@RequiredArgsConstructor
public class OpsController {

    private final KubernetesInspectionService k8s;
    private final PrometheusService prometheus;
    private final PulsarAdminService pulsar;
    private final RedisClusterInspectionService redis;
    private final VectorGraphInspectionService vectorGraph;
    private final GrafanaService grafana;
    private final LogsService logs;

    // ================= 总览 =================

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        return Map.of(
                "deployments", k8s.deployments(),
                "hpas", k8s.hpas(),
                "serviceRuntime", prometheus.serviceRuntimeStatus(),
                "alerts", prometheus.alerts(),
                "grafanaEmbeds", grafana.embedUrls());
    }

    // ================= 服务/工作负载/HPA =================

    @GetMapping("/services")
    public Object services() {
        return k8s.deployments();
    }

    @GetMapping("/hpa")
    public Object hpa() {
        return k8s.hpas();
    }

    @GetMapping("/events")
    public Object events(@RequestParam(defaultValue = "platform-plus") String namespace,
                         @RequestParam(defaultValue = "50") int limit) {
        return k8s.recentEvents(namespace, limit);
    }

    // ================= 多级缓存(Redis/向量库/图库) =================

    @GetMapping("/cache/redis")
    public Object redisCluster() {
        return redis.clusterInspection();
    }

    @PostMapping("/cache/redis/config")
    public Map<String, String> redisConfig(@RequestParam String param, @RequestParam String value) {
        return Map.of("result", redis.configSet(param, value));
    }

    @GetMapping("/cache/vector/collections")
    public Object milvusCollections() {
        return vectorGraph.collections();
    }

    @GetMapping("/cache/vector/stats")
    public Object milvusStats(@RequestParam String collection) {
        return vectorGraph.collectionStats(collection);
    }

    @GetMapping("/cache/graph/metrics")
    public Object nebulaMetrics() {
        return vectorGraph.nebulaMetrics();
    }

    // ================= Pulsar 消息 =================

    @GetMapping("/pulsar/tenants")
    public Object pulsarTenants() {
        return pulsar.tenants();
    }

    @GetMapping("/pulsar/namespaces")
    public Object pulsarNamespaces(@RequestParam String tenant) {
        return pulsar.namespaces(tenant);
    }

    @GetMapping("/pulsar/topics")
    public Object pulsarTopics(@RequestParam String tenant,
                               @RequestParam String namespace) {
        return pulsar.topics(tenant, namespace);
    }

    @GetMapping("/pulsar/topics/stats")
    public Object pulsarTopicStats(@RequestParam String tenant,
                                   @RequestParam String namespace,
                                   @RequestParam String topic) {
        return pulsar.topicStats(tenant, namespace, topic);
    }

    @GetMapping("/pulsar/topics/subscriptions")
    public Object pulsarSubscriptions(@RequestParam String tenant,
                                      @RequestParam String namespace,
                                      @RequestParam String topic) {
        return pulsar.subscriptions(tenant, namespace, topic);
    }

    /** 查看具体消息内容(peek 不消费) */
    @GetMapping("/pulsar/topics/peek")
    public Object pulsarPeek(@RequestParam String tenant,
                             @RequestParam String namespace,
                             @RequestParam String topic,
                             @RequestParam String subscription,
                             @RequestParam(defaultValue = "10") int count) {
        return pulsar.peekMessages(tenant, namespace, topic, subscription, count);
    }

    // ================= 指标/告警 =================

    @GetMapping("/metrics/query")
    public Object query(@RequestParam String promql) {
        return prometheus.query(promql);
    }

    @GetMapping("/metrics/range")
    public Object range(@RequestParam String promql,
                        @RequestParam String start,
                        @RequestParam String end,
                        @RequestParam(defaultValue = "60s") String step) {
        return prometheus.range(promql, start, end, step);
    }

    @GetMapping("/alerts")
    public Object alerts() {
        return prometheus.alerts();
    }

    // ================= Grafana 集成 =================

    @GetMapping("/grafana/dashboards")
    public Object grafanaDashboards() {
        return grafana.dashboards();
    }

    @GetMapping("/grafana/embeds")
    public Object grafanaEmbeds() {
        return grafana.embedUrls();
    }

    @PostMapping("/grafana/ds/query")
    public Object grafanaDsQuery(@RequestBody Map<String, String> body) {
        return grafana.dsQuery(body.getOrDefault("datasourceUid", "prometheus"),
                body.get("promql"), body.get("from"), body.get("to"));
    }

    // ================= 日志中心 =================

    @GetMapping("/logs/loki")
    public Object lokiQuery(@RequestParam(defaultValue = "{namespace=\"platform-plus\"}") String logql,
                            @RequestParam(required = false) String start,
                            @RequestParam(required = false) String end,
                            @RequestParam(defaultValue = "200") int limit) {
        return logs.queryRange(logql, start, end, limit);
    }

    /** Pod 实时日志 SSE: 前端 EventSource("/api/v1/ops/logs/pod/stream?ns=&pod=") */
    @GetMapping(value = "/logs/pod/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter podLogStream(@RequestParam String ns, @RequestParam String pod,
                                   @RequestParam(required = false) String container) {
        return logs.streamPodLog(ns, pod, container);
    }

    /** 动态日志级别(管理操作) */
    @PostMapping("/logs/level")
    public Map<String, String> setLogLevel(@RequestBody Map<String, String> body) {
        return Map.of("result", logs.setLogLevel(
                body.get("serviceUrl"), body.get("logger"), body.get("level")));
    }
}
