package com.platform.ai.interfaces;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 内部检索接口: 供 vector-reranker 委托向量召回(agent-service 资源检索链路的真实召回源)。
 *
 * 直连实现(HTTP embed-proxy + Milvus REST v2), 不走 Spring AI VectorStore:
 * Spring AI 1.1.8 的 OpenAiEmbeddingModel.dimensions() 对 embed-proxy 的 200 响应
 * 解析为空(NoSuchElementException), 且探测无缓存导致每次 search 都打爆 ollama-embed
 * (实测 13 核满载风暴)。直连链路实测两端均稳定, 作为生产检索通道。
 */
@RestController
@RequestMapping("/internal")
public class InternalSearchController {

    private final RestClient embedClient;
    private final RestClient milvusClient;
    private final String embedModel;
    private final String collection;

    public InternalSearchController(
            @Value("${app.search.embedding-url:http://embed-proxy.ai-platform.svc.cluster.local:8088}") String embeddingUrl,
            @Value("${app.search.milvus-url:http://milvus.infra.svc.cluster.local:19530}") String milvusUrl,
            @Value("${spring.ai.openai.embedding.options.model:bge-m3}") String embedModel,
            @Value("${spring.ai.vectorstore.milvus.collectionName:edu_resources}") String collection) {
        this.embedModel = embedModel;
        this.collection = collection;
        this.embedClient = RestClient.builder()
                .baseUrl(embeddingUrl)
                .requestFactory(factory(Duration.ofSeconds(5), Duration.ofSeconds(120)))
                .build();
        this.milvusClient = RestClient.builder()
                .baseUrl(milvusUrl)
                .requestFactory(factory(Duration.ofSeconds(5), Duration.ofSeconds(30)))
                .build();
    }

    private static org.springframework.http.client.SimpleClientHttpRequestFactory factory(
            Duration connect, Duration read) {
        var f = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        f.setConnectTimeout((int) connect.toMillis());
        f.setReadTimeout((int) read.toMillis());
        return f;
    }

    public record VectorSearchRequest(String query, Integer topK) {}

    public record VectorHit(String id, double score, String text, Map<String, Object> tags) {}

    @PostMapping("/vector-search")
    @SuppressWarnings("unchecked")
    public List<VectorHit> vectorSearch(@RequestBody VectorSearchRequest req) {
        String query = req.query() == null ? "" : req.query().trim();
        if (query.isEmpty()) {
            return List.of();
        }
        int topK = req.topK() == null || req.topK() <= 0 ? 10 : Math.min(req.topK(), 50);

        // 1) embedding(单条; ollama 后端批量易 503)
        Map<String, Object> embedResp = embedClient.post()
                .uri("/v1/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("model", embedModel, "input", List.of(query)))
                .retrieve()
                .body(Map.class);
        List<List<Double>> data = embedResp == null ? null : (List<List<Double>>) embedResp.get("data");
        System.err.println("[direct-search] embed resp keys=" + (embedResp == null ? "null" : embedResp.keySet())
                + " dataLen=" + (data == null ? -1 : data.size()));
        if (data == null || data.isEmpty() || data.get(0) == null) {
            return List.of();
        }
        List<Double> vector = data.get(0);

        // 2) Milvus REST v2 search(COSINE, score=相似度)
        Map<String, Object> searchBody = Map.of(
                "collectionName", collection,
                "data", List.of(vector),
                "limit", topK,
                "outputFields", List.of("doc_id", "content", "metadata"));
        Map<String, Object> resp = milvusClient.post()
                .uri("/v2/vectordb/entities/search")
                .contentType(MediaType.APPLICATION_JSON)
                .body(searchBody)
                .retrieve()
                .body(Map.class);
        List<Map<String, Object>> results = resp == null ? null : (List<Map<String, Object>>) resp.get("data");
        System.err.println("[direct-search] milvus resp=" + (resp == null ? "null" : resp.keySet())
                + " results=" + (results == null ? -1 : results.size()));
        if (results == null) {
            return List.of();
        }
        List<VectorHit> out = new ArrayList<>(results.size());
        for (Map<String, Object> row : results) {
            Object id = row.get("doc_id");
            if (id == null) {
                continue;
            }
            Object score = row.getOrDefault("distance", row.get("score"));
            Map<String, Object> tags = row.get("metadata") instanceof Map<?, ?> m
                    ? new HashMap<>((Map<String, Object>) m) : Map.of();
            out.add(new VectorHit(String.valueOf(id),
                    score instanceof Number n ? n.doubleValue() : 0.0,
                    String.valueOf(row.getOrDefault("content", "")),
                    tags));
        }
        return out;
    }
}
