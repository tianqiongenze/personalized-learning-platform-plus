package com.platform.ops.service;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.autoscaling.v2.HorizontalPodAutoscaler;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Kubernetes 工作负载巡检: Deployment/Pod/HPA/Event
 * 数据来源: fabric8 kubernetes-client, RBAC 最小权限(见 k8s/base/ops-rbac.yaml)
 */
@Service
@RequiredArgsConstructor
public class KubernetesInspectionService {

    private final KubernetesClient client;

    @Value("${app.k8s.namespaces:platform-plus}")
    private List<String> namespaces;

    /** 服务运行总览: 每个 Deployment 的副本健康度 */
    public List<Map<String, Object>> deployments() {
        return namespaces.stream()
                .flatMap(ns -> client.apps().deployments().inNamespace(ns).list().getItems().stream())
                .map(d -> Map.<String, Object>of(
                        "namespace", d.getMetadata().getNamespace(),
                        "name", d.getMetadata().getName(),
                        "replicas", d.getStatus().getReplicas() == null ? 0 : d.getStatus().getReplicas(),
                        "ready", d.getStatus().getReadyReplicas() == null ? 0 : d.getStatus().getReadyReplicas(),
                        "available", d.getStatus().getAvailableReplicas() == null ? 0 : d.getStatus().getAvailableReplicas(),
                        "image", d.getSpec().getTemplate().getSpec().getContainers().get(0).getImage(),
                        "healthy", healthy(d)))
                .collect(Collectors.toList());
    }

    private boolean healthy(Deployment d) {
        int want = d.getSpec().getReplicas() == null ? 1 : d.getSpec().getReplicas();
        int ready = d.getStatus().getReadyReplicas() == null ? 0 : d.getStatus().getReadyReplicas();
        return ready >= want;
    }

    /** HPA 扩缩容实时状态: 当前/期望副本 + 指标利用率 */
    public List<Map<String, Object>> hpas() {
        return namespaces.stream()
                .flatMap(ns -> client.autoscaling().v2().horizontalPodAutoscalers().inNamespace(ns).list()
                        .getItems().stream())
                .map(this::toMap)
                .collect(Collectors.toList());
    }

    private Map<String, Object> toMap(HorizontalPodAutoscaler h) {
        var status = h.getStatus();
        var currentMetrics = status.getCurrentMetrics() == null ? List.<Map<String, Object>>of()
                : status.getCurrentMetrics().stream()
                        .map(m -> {
                            if (m.getResource() == null) {
                                return Map.<String, Object>of("type", m.getType());
                            }
                            var cur = m.getResource().getCurrent() == null ? null : m.getResource().getCurrent().getAverageValue();
                            return Map.<String, Object>of(
                                    "type", m.getType(),
                                    "name", m.getResource().getName(),
                                    "current", cur != null ? cur.toString() : "?");
                        })
                        .collect(Collectors.toList());
        return Map.<String, Object>of(
                "namespace", h.getMetadata().getNamespace(),
                "name", h.getMetadata().getName(),
                "currentReplicas", status.getCurrentReplicas() == null ? 0 : status.getCurrentReplicas(),
                "desiredReplicas", status.getDesiredReplicas() == null ? 0 : status.getDesiredReplicas(),
                "minReplicas", h.getSpec().getMinReplicas() == null ? 0 : h.getSpec().getMinReplicas(),
                "maxReplicas", h.getSpec().getMaxReplicas() == null ? 0 : h.getSpec().getMaxReplicas(),
                "metrics", currentMetrics,
                "scalingActive", status.getConditions() == null ? "?" :
                        status.getConditions().stream()
                                .filter(c -> "ScalingActive".equals(c.getType())).findFirst()
                                .map(c -> c.getStatus()).orElse("?"));
    }

    /** 集群事件(最近的异常告警源) */
    public List<Map<String, Object>> recentEvents(String namespace, int limit) {
        return client.v1().events().inNamespace(namespace).list().getItems().stream()
                .filter(e -> "Warning".equals(e.getType()))
                .limit(limit)
                .map(e -> Map.<String, Object>of(
                        "namespace", e.getMetadata().getNamespace(),
                        "reason", e.getReason() == null ? "" : e.getReason(),
                        "message", e.getMessage() == null ? "" : e.getMessage(),
                        "object", e.getInvolvedObject() != null ? e.getInvolvedObject().getKind() + "/" + e.getInvolvedObject().getName() : "",
                        "lastTimestamp", String.valueOf(e.getLastTimestamp())))
                .collect(Collectors.toList());
    }

    /** Pod 实时日志(SSE 由控制器转发) */
    public String podLog(String namespace, String pod, String container, int tailLines) {
        return client.pods().inNamespace(namespace).withName(pod)
                .inContainer(container).tailingLines(tailLines).getLog();
    }
}
