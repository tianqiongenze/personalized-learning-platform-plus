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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 知识点控制器。
 */
@RestController
@RequestMapping("/knowledge-points")
@RequiredArgsConstructor
@Tag(name = "知识点管理", description = "知识点管理 API")
public class KnowledgePointController {

    private final CourseApplicationService courseService;

    private String currentUser(HttpServletRequest request) {
        String u = request.getHeader("X-User-Id");
        return (u == null || u.isBlank()) ? "system" : u;
    }

    @PostMapping
    @Operation(summary = "创建知识点")
    public ResponseEntity<ApiResponse<String>> createKnowledgePoint(
            @Valid @RequestBody KpCreateRequest request, HttpServletRequest http) {
        KnowledgePointId id = courseService.createKnowledgePoint(new CreateKnowledgePointCommand(
                CourseId.of(request.courseId()), request.code(), request.name(),
                request.description(),
                request.chapterId() == null ? null : ChapterId.of(request.chapterId()),
                request.difficulty(), currentUser(http)));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("知识点创建成功", id.getValue().toString()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取知识点详情(含前置/相关)")
    public ResponseEntity<ApiResponse<KnowledgePointDetailDto>> getKnowledgePoint(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(
                courseService.findKnowledgePointById(KnowledgePointId.of(id))));
    }

    @GetMapping
    @Operation(summary = "分页查询知识点")
    public ResponseEntity<ApiResponse<PageResponse<KnowledgePointListDto>>> listKnowledgePoints(
            @RequestParam String courseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String chapterId,
            @RequestParam(defaultValue = "code") String sortBy,
            @RequestParam(defaultValue = "asc") String sortOrder) {
        PageResult<KnowledgePointListDto> result = courseService.findKnowledgePointPage(
                new KnowledgePointPageQuery(
                        CourseId.of(courseId), page, size, status, keyword,
                        chapterId == null ? null : ChapterId.of(chapterId), sortBy, sortOrder));
        return ResponseEntity.ok(ApiResponse.success(toPage(result)));
    }

    @GetMapping("/by-course/{courseId}")
    @Operation(summary = "查询课程下全部知识点")
    public ResponseEntity<ApiResponse<java.util.List<KnowledgePointSimpleDto>>> listByCourse(
            @PathVariable String courseId) {
        return ResponseEntity.ok(ApiResponse.success(
                courseService.findKnowledgePointsByCourseId(CourseId.of(courseId))));
    }

    @GetMapping("/by-chapter/{chapterId}")
    @Operation(summary = "查询章节下全部知识点")
    public ResponseEntity<ApiResponse<java.util.List<KnowledgePointSimpleDto>>> listByChapter(
            @PathVariable String chapterId) {
        return ResponseEntity.ok(ApiResponse.success(
                courseService.findKnowledgePointsByChapterId(ChapterId.of(chapterId))));
    }

    @PutMapping("/{id}/info")
    @Operation(summary = "更新知识点信息")
    public ResponseEntity<ApiResponse<Void>> updateKnowledgePointInfo(
            @PathVariable String id, @Valid @RequestBody KpInfoUpdateRequest request,
            HttpServletRequest http) {
        courseService.updateKnowledgePointInfo(new UpdateKnowledgePointInfoCommand(
                KnowledgePointId.of(id), request.name(), request.description(),
                request.difficulty(), currentUser(http)));
        return ResponseEntity.ok(ApiResponse.success("知识点更新成功", null));
    }

    @PostMapping("/{id}/prerequisites/{prerequisiteId}")
    @Operation(summary = "添加前置知识点(构成图谱 PREREQUISITE 边)")
    public ResponseEntity<ApiResponse<Void>> addPrerequisite(
            @PathVariable String id, @PathVariable String prerequisiteId, HttpServletRequest http) {
        courseService.addKnowledgePointPrerequisite(new AddKnowledgePointPrerequisiteCommand(
                KnowledgePointId.of(id), KnowledgePointId.of(prerequisiteId), currentUser(http)));
        return ResponseEntity.ok(ApiResponse.success("前置知识点添加成功", null));
    }

    @DeleteMapping("/{id}/prerequisites/{prerequisiteId}")
    @Operation(summary = "移除前置知识点")
    public ResponseEntity<ApiResponse<Void>> removePrerequisite(
            @PathVariable String id, @PathVariable String prerequisiteId, HttpServletRequest http) {
        courseService.removeKnowledgePointPrerequisite(new RemoveKnowledgePointPrerequisiteCommand(
                KnowledgePointId.of(id), KnowledgePointId.of(prerequisiteId), currentUser(http)));
        return ResponseEntity.ok(ApiResponse.success("前置知识点移除成功", null));
    }

    @PostMapping("/{id}/related/{relatedId}")
    @Operation(summary = "添加相关知识点(构成图谱 RELATES_TO 边)")
    public ResponseEntity<ApiResponse<Void>> addRelated(
            @PathVariable String id, @PathVariable String relatedId, HttpServletRequest http) {
        courseService.addKnowledgePointRelatedPoint(new AddKnowledgePointRelatedCommand(
                KnowledgePointId.of(id), KnowledgePointId.of(relatedId), currentUser(http)));
        return ResponseEntity.ok(ApiResponse.success("相关知识点添加成功", null));
    }

    @DeleteMapping("/{id}/related/{relatedId}")
    @Operation(summary = "移除相关知识点")
    public ResponseEntity<ApiResponse<Void>> removeRelated(
            @PathVariable String id, @PathVariable String relatedId, HttpServletRequest http) {
        courseService.removeKnowledgePointRelatedPoint(new RemoveKnowledgePointRelatedCommand(
                KnowledgePointId.of(id), KnowledgePointId.of(relatedId), currentUser(http)));
        return ResponseEntity.ok(ApiResponse.success("相关知识点移除成功", null));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除知识点")
    public ResponseEntity<ApiResponse<Void>> deleteKnowledgePoint(
            @PathVariable String id, @RequestBody DeleteRequest request, HttpServletRequest http) {
        courseService.deleteKnowledgePoint(new DeleteKnowledgePointCommand(
                KnowledgePointId.of(id), currentUser(http),
                request == null ? "未填写" : request.reason()));
        return ResponseEntity.ok(ApiResponse.success("知识点删除成功", null));
    }

    public record KpCreateRequest(
            @jakarta.validation.constraints.NotBlank String courseId,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(min = 2, max = 50) String code,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 200) String name,
            @jakarta.validation.constraints.Size(max = 1000) String description,
            String chapterId, int difficulty) {}

    public record KpInfoUpdateRequest(String name, String description, int difficulty) {}

    public record DeleteRequest(String reason) {}

    static PageResponse toPage(PageResult<?> result) {
        return new PageResponse<>(result.content(), result.page(), result.size(),
                result.totalElements(), result.totalPages(), result.first(), result.last());
    }
}
