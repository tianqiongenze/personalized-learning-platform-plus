package com.platform.ai.interfaces;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 内部检索接口: 供 vector-reranker 委托向量召回(agent-service 资源检索链路的真实召回源)。
 * 信任网络边界(K8s NetworkPolicy), 与 generation 服务的 /generation/search 复用同一 VectorStore。
 */
@RestController
@RequestMapping("/internal")
public class InternalSearchController {

    private final com.platform.ai.generation.GenerationService generationService;

    public InternalSearchController(com.platform.ai.generation.GenerationService generationService) {
        this.generationService = generationService;
    }

    public record VectorSearchRequest(String query, Integer topK) {}

    public record VectorHit(String id, double score, String text, Map<String, Object> tags) {}

    @PostMapping("/vector-search")
    public List<VectorHit> vectorSearch(@RequestBody VectorSearchRequest req) {
        String query = req.query() == null ? "" : req.query().trim();
        if (query.isEmpty()) {
            return List.of();
        }
        int topK = req.topK() == null || req.topK() <= 0 ? 10 : Math.min(req.topK(), 50);
        return generationService.search(query, topK).stream()
                .map(d -> new VectorHit(
                        d.getId(),
                        d.getScore() == null ? 0.0 : d.getScore(),
                        d.getText() == null ? "" : d.getText(),
                        d.getMetadata()))
                .toList();
    }
}
