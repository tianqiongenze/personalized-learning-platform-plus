package com.platform.course.interfaces.rest;

import com.platform.course.application.command.*;
import com.platform.course.application.query.*;
import com.platform.course.application.service.CourseApplicationService;
import com.platform.course.domain.model.valueobjects.*;
import com.platform.course.interfaces.rest.dto.ApiResponse;
import com.platform.course.interfaces.rest.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 知识图谱控制器 —— agent-service `query_knowledge_graph` 工具依赖此端点。
 * 重构前 course-service 无任何 HTTP 出口, 该调用必然 404, RAG 图谱链路实际断裂。
 */
@RestController
@RequestMapping("/knowledge")
@RequiredArgsConstructor
@Tag(name = "知识图谱", description = "知识点图谱查询 API(供 AI 生成引擎 / 前端可视化)")
public class KnowledgeGraphController {

    private final KnowledgeGraphQueryService knowledgeGraphQueryService;

    @GetMapping("/graph")
    @Operation(summary = "查询知识点图谱",
            description = "按实体名(知识点名称/编码/ID)返回一跳关联(先修 PREREQUISITE / 相关 RELATES_TO)")
    public ResponseEntity<ApiResponse<KnowledgeGraphQueryService.KnowledgeGraphDto>> queryGraph(
            @RequestParam String entity,
            @RequestParam(defaultValue = "1") int hops) {
        return ResponseEntity.ok(ApiResponse.success(
                knowledgeGraphQueryService.findGraph(entity, hops)));
    }
}
