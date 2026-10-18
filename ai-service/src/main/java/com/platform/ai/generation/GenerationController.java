package com.platform.ai.generation;

import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 资源生成 REST 接口
 */
@RestController
@RequestMapping("/generation")
public class GenerationController {

    private final GenerationService generationService;
    private final AsyncGenerationService asyncGenerationService;

    public GenerationController(GenerationService generationService,
                                AsyncGenerationService asyncGenerationService) {
        this.generationService = generationService;
        this.asyncGenerationService = asyncGenerationService;
    }

    public record GenerateRequest(
            @NotBlank String resourceType,
            @NotBlank String knowledgePoint,
            String industryTags,
            String requirements,
            String studentId,
            String courseId) {}

    public record GenerateResponse(
            String generationId, String resourceType, String content,
            String status, Instant generatedAt,
            boolean cacheHit, String cacheLayer) {}

    @PostMapping
    public ResponseEntity<GenerateResponse> generate(@RequestBody GenerateRequest req) {
        GenerationService.GenerationResult r = generationService.generateWithMeta(
                req.resourceType(), req.knowledgePoint(),
                req.industryTags() == null ? "智能制造" : req.industryTags(),
                req.requirements());
        String id = UUID.randomUUID().toString();
        return ResponseEntity.status(HttpStatus.CREATED).body(new GenerateResponse(
                id, req.resourceType(), r.content(), "COMPLETED", Instant.now(),
                r.cacheHit(), r.cacheHit() ? "exact" : "none"));
    }

    /** 异步提交(1000 并发削峰): 毫秒级返回 taskId, 完成后经 realtime-gateway 推送 */
    @PostMapping("/async")
    public ResponseEntity<java.util.Map<String, String>> submitAsync(
            @RequestBody GenerateRequest req,
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "public") String tenantId) {
        String taskId = asyncGenerationService.submit(tenantId,
                req.studentId() == null ? "guest" : req.studentId(),
                req.resourceType(), req.knowledgePoint(),
                req.industryTags(), req.requirements());
        return ResponseEntity.accepted().body(java.util.Map.of(
                "taskId", taskId, "status", "QUEUED"));
    }

    @PostMapping("/search")
    public java.util.List<Map<String, Object>> search(@RequestBody Map<String, Object> body) {
        String query = String.valueOf(body.getOrDefault("query", ""));
        int topK = Integer.parseInt(String.valueOf(body.getOrDefault("topK", "5")));
        return generationService.search(query, topK).stream()
                .map(d -> Map.<String, Object>of(
                        "id", d.getId(),
                        "text", d.getText() == null ? "" : d.getText(),
                        "metadata", d.getMetadata()))
                .toList();
    }

    @PostMapping("/index")
    public Map<String, Object> index(@RequestBody Map<String, Object> body) {
        generationService.indexResource(
                String.valueOf(body.get("resourceId")),
                String.valueOf(body.getOrDefault("title", "")),
                String.valueOf(body.getOrDefault("content", "")),
                (Map<String, Object>) body.get("metadata"));
        return Map.of("indexed", true);
    }
}
