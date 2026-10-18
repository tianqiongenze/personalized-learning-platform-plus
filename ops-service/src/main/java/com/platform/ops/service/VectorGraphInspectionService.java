package com.platform.ops.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Milvus 向量库巡检(RESTful v2, 与 gRPC 同端口 19530)
 *   POST /v2/vectordb/collections/list
 *   POST /v2/vectordb/collections/describe
 *   POST /v2/vectordb/collections/get_stats
 * NebulaGraph 图库巡检: HTTP stats(graphd 19669 / storaged 19779)
 *   (nGQL 查询经 nebula-http-gateway; 未部署时降级为仅指标视图)
 */
@Service
public class VectorGraphInspectionService {

    private final RestClient milvus;
    private final RestClient nebula;
    private final String nebulaBase;

    public VectorGraphInspectionService(
            @Value("${app.endpoints.milvus}") String milvusHostPort,
            @Value("${app.endpoints.nebula-graphd}") String nebulaGraphd) {
        this.milvus = RestClient.builder()
                .baseUrl("http://" + milvusHostPort).build();
        this.nebulaBase = "http://" + nebulaGraphd;
        this.nebula = RestClient.builder().baseUrl(this.nebulaBase).build();
    }

    // ---------- Milvus ----------

    @SuppressWarnings("unchecked")
    public Object collections() {
        return post("/v2/vectordb/collections/list", Map.of("dbName", "default"));
    }

    public Object collectionStats(String collection) {
        return post("/v2/vectordb/collections/get_stats",
                Map.of("dbName", "default", "collectionName", collection));
    }

    public Object collectionDescribe(String collection) {
        return post("/v2/vectordb/collections/describe",
                Map.of("dbName", "default", "collectionName", collection));
    }

    @SuppressWarnings("unchecked")
    private Object post(String path, Map<String, Object> body) {
        try {
            return milvus.post().uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body).retrieve().body(Object.class);
        } catch (Exception e) {
            return Map.of("code", -1, "error", String.valueOf(e.getMessage()));
        }
    }

    // ---------- NebulaGraph ----------

    /** graphd/storage 指标快照(文本 -> 行) */
    public List<Map<String, String>> nebulaMetrics() {
        try {
            String stats = nebula.get().uri("/stats").retrieve().body(String.class);
            return stats.lines()
                    .filter(l -> l.contains("="))
                    .limit(100)
                    .map(l -> Map.of("metric", l.split("=")[0], "value", l.split("=", 2)[1]))
                    .toList();
        } catch (Exception e) {
            return List.of(Map.of("metric", "error", "value", String.valueOf(e.getMessage())));
        }
    }
}
