package com.platform.ops.service;

import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 日志中心:
 *  1. Pod 实时日志: fabric8 client follow=true -> SSE 转发到运维平台前端
 *  2. 聚合日志检索: Loki /loki/api/v1/query_range (Promtail 已采集全部容器日志)
 *  3. 动态日志级别: 各服务 actuator /loggers (管理操作)
 */
@Service
public class LogsService {

    private final KubernetesClient client;
    private final RestClient loki;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public LogsService(KubernetesClient client,
                       @Value("${app.endpoints.loki}") String lokiUrl) {
        this.client = client;
        this.loki = RestClient.builder().baseUrl(lokiUrl).build();
    }

    /** Pod 实时日志 -> SSE(前端 EventSource 直接订阅) */
    public SseEmitter streamPodLog(String namespace, String pod, String container) {
        SseEmitter emitter = new SseEmitter(0L); // 不超时, 由前端断开
        executor.execute(() -> {
            try (var watch = client.pods().inNamespace(namespace).withName(pod)
                    .inContainer(container == null ? "" : container)
                    .tailingLines(100).watchLog()) {
                var in = new BufferedReader(new InputStreamReader(watch.getOutput(), StandardCharsets.UTF_8));
                String line;
                while ((line = in.readLine()) != null) {
                    emitter.send(SseEmitter.event().data(line));
                }
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    /** Loki 聚合日志查询(LogQL) */
    public Object queryRange(String logql, String start, String end, int limit) {
        return loki.get()
                .uri(uri -> uri.path("/loki/api/v1/query_range")
                        .queryParam("query", logql)
                        .queryParam("start", start == null ? String.valueOf(System.nanoTime() - 3600_000_000_000L) : start)
                        .queryParam("end", end == null ? String.valueOf(System.nanoTime()) : end)
                        .queryParam("limit", Math.min(limit, 1000))
                        .build())
                .retrieve().body(Object.class);
    }

    /** 日志级别动态调整(管理操作): 直连目标服务 actuator */
    public String setLogLevel(String serviceUrl, String loggerName, String level) {
        try {
            var client = HttpClient.newHttpClient();
            var body = "{\"configuredLevel\":\"" + level + "\"}";
            var req = HttpRequest.newBuilder()
                    .uri(URI.create(serviceUrl.replaceAll("/$", "") + "/actuator/loggers/" + loggerName))
                    .header("Content-Type", "application/vnd.spring-boot.actuator.v3+json")
                    .method("POST", HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() + ": " + resp.body();
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }
}
