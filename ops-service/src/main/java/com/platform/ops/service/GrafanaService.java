package com.platform.ops.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Grafana 集成(双通道):
 *  1. HTTP API 通道: Service Account Token 调 /api/ds/query、/api/search、/api/alertmanager
 *     在运维平台前端自渲染指标与告警
 *  2. 嵌入通道: allow_embedding 已开启, 前端 iframe 拼接
 *     {base}/d/{uid}?kiosk=tv —— 统一登录由网关注入 JWT/匿名 Viewer 保证内网可用
 */
@Service
public class GrafanaService {

    private final RestClient anonymous;
    private final RestClient tokenAuthed;
    private final String base;

    public GrafanaService(@Value("${app.endpoints.grafana}") String grafanaBase,
                          @Value("${app.grafana.sa-token:}") String saToken) {
        this.base = grafanaBase.replaceAll("/$", "");
        this.anonymous = RestClient.builder().baseUrl(this.base).build();
        this.tokenAuthed = RestClient.builder().baseUrl(this.base)
                .defaultHeader(HttpHeaders.AUTHORIZATION,
                        "Bearer " + (saToken == null ? "" : saToken)).build();
    }

    /** 看板列表(Platform Plus 文件夹) */
    public Object dashboards() {
        try {
            return tokenAuthed.get()
                    .uri(uri -> uri.path("/api/search")
                            .queryParam("folderTitle", "Platform Plus").build())
                    .retrieve().body(Object.class);
        } catch (Exception e) {
            return java.util.List.of(java.util.Map.of("error", String.valueOf(e.getMessage())));
        }
    }

    /** 指标代理查询: 前端把 PromQL 发给运维平台, 平台带 token 转 Grafana /api/ds/query */
    public Object dsQuery(String datasourceUid, String promql, String from, String to) {
        var body = Map.of(
                "queries", java.util.List.of(Map.of(
                        "refId", "A",
                        "datasource", Map.of("type", "prometheus", "uid", datasourceUid),
                        "expr", promql,
                        "format", "time_series")),
                "from", from == null ? "now-1h" : from,
                "to", to == null ? "now" : to);
        return tokenAuthed.post().uri("/api/ds/query")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().body(Object.class);
    }

    /** kiosk 嵌入地址(前端 iframe src) */
    public java.util.Map<String, String> embedUrls() {
        return java.util.Map.of(
                "platformOverview", base + "/d/platform-overview?kiosk=tv&refresh=30s",
                "servicesJvm", base + "/d/services-jvm?kiosk=tv&refresh=30s",
                "cacheMonitor", base + "/d/cache-monitor?kiosk=tv&refresh=30s",
                "pulsarMonitor", base + "/d/pulsar-monitor?kiosk=tv&refresh=30s",
                "hostResources", base + "/d/host-resources?kiosk=tv&refresh=30s",
                "logs", base + "/d/platform-logs?kiosk=tv");
    }
}
