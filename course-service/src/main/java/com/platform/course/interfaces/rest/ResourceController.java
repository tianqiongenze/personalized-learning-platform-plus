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

import java.util.List;

/**
 * 教学资源控制器(资源库入库通道, 支撑申报书"颗粒化资源库 ≥500 个"指标的落库路径)。
 */
@RestController
@RequestMapping("/resources")
@RequiredArgsConstructor
@Tag(name = "教学资源", description = "教学资源管理 API")
public class ResourceController {

    private final CourseApplicationService courseService;

    private String currentUser(HttpServletRequest request) {
        String u = request.getHeader("X-User-Id");
        return (u == null || u.isBlank()) ? "system" : u;
    }

    @PostMapping
    @Operation(summary = "创建教学资源")
    public ResponseEntity<ApiResponse<String>> createResource(
            @Valid @RequestBody ResourceCreateRequest request, HttpServletRequest http) {
        ResourceId id = courseService.createResource(new CreateResourceCommand(
                CourseId.of(request.courseId()),
                request.chapterId() == null ? null : ChapterId.of(request.chapterId()),
                ResourceType.valueOf(request.type().toUpperCase()),
                request.title(), request.description(), request.contentUrl(), request.coverUrl(),
                request.metadata(), request.difficulty(), request.tags(), currentUser(http)));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("资源创建成功", id.getValue().toString()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取资源详情")
    public ResponseEntity<ApiResponse<ResourceDetailDto>> getResource(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(courseService.findResourceById(ResourceId.of(id))));
    }

    @GetMapping
    @Operation(summary = "分页查询资源")
    public ResponseEntity<ApiResponse<PageResponse<ResourceListDto>>> listResources(
            @RequestParam String courseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String chapterId,
            @RequestParam(required = false) String kpId,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortOrder) {
        PageResult<ResourceListDto> result = courseService.findResourcePage(new ResourcePageQuery(
                CourseId.of(courseId), page, size, status, keyword, type,
                chapterId == null ? null : ChapterId.of(chapterId),
                kpId == null ? null : KnowledgePointId.of(kpId), sortBy, sortOrder));
        return ResponseEntity.ok(ApiResponse.success(toPage(result)));
    }

    @GetMapping("/by-course/{courseId}")
    @Operation(summary = "查询课程下全部资源")
    public ResponseEntity<ApiResponse<List<ResourceSimpleDto>>> listByCourse(@PathVariable String courseId) {
        return ResponseEntity.ok(ApiResponse.success(
                courseService.findResourcesByCourseId(CourseId.of(courseId))));
    }

    @GetMapping("/by-knowledge-point/{kpId}")
    @Operation(summary = "查询知识点关联资源(供 RAG 检索候选集)")
    public ResponseEntity<ApiResponse<List<ResourceSimpleDto>>> listByKp(@PathVariable String kpId) {
        return ResponseEntity.ok(ApiResponse.success(
                courseService.findResourcesByKpId(KnowledgePointId.of(kpId))));
    }

    @GetMapping("/by-type/{type}")
    @Operation(summary = "按资源类型查询")
    public ResponseEntity<ApiResponse<List<ResourceSimpleDto>>> listByType(@PathVariable String type) {
        return ResponseEntity.ok(ApiResponse.success(
                courseService.findResourcesByType(ResourceType.valueOf(type.toUpperCase()))));
    }

    @PutMapping("/{id}/info")
    @Operation(summary = "更新资源信息")
    public ResponseEntity<ApiResponse<Void>> updateResourceInfo(
            @PathVariable String id, @Valid @RequestBody ResourceInfoUpdateRequest request,
            HttpServletRequest http) {
        courseService.updateResourceInfo(new UpdateResourceInfoCommand(
                ResourceId.of(id), request.title(), request.description(),
                request.contentUrl(), request.coverUrl(), request.difficulty(),
                request.tags(), currentUser(http)));
        return ResponseEntity.ok(ApiResponse.success("资源更新成功", null));
    }

    @PutMapping("/{id}/metadata")
    @Operation(summary = "更新资源元数据")
    public ResponseEntity<ApiResponse<Void>> updateResourceMetadata(
            @PathVariable String id, @RequestBody ResourceMetadataUpdateRequest request,
            HttpServletRequest http) {
        courseService.updateResourceMetadata(new UpdateResourceMetadataCommand(
                ResourceId.of(id), request.metadata(), currentUser(http)));
        return ResponseEntity.ok(ApiResponse.success("资源元数据更新成功", null));
    }

    @PostMapping("/{id}/publish")
    @Operation(summary = "发布资源")
    public ResponseEntity<ApiResponse<Void>> publishResource(@PathVariable String id, HttpServletRequest http) {
        courseService.publishResource(new PublishResourceCommand(ResourceId.of(id), currentUser(http)));
        return ResponseEntity.ok(ApiResponse.success("资源发布成功", null));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除资源")
    public ResponseEntity<ApiResponse<Void>> deleteResource(
            @PathVariable String id, @RequestBody DeleteRequest request, HttpServletRequest http) {
        courseService.deleteResource(new DeleteResourceCommand(
                ResourceId.of(id), currentUser(http), request.reason()));
        return ResponseEntity.ok(ApiResponse.success("资源删除成功", null));
    }

    public record ResourceCreateRequest(
            @jakarta.validation.constraints.NotBlank String courseId,
            String chapterId,
            @jakarta.validation.constraints.NotBlank String type,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 300) String title,
            @jakarta.validation.constraints.Size(max = 2000) String description,
            @jakarta.validation.constraints.NotBlank String contentUrl,
            String coverUrl,
            java.util.Map<String, Object> metadata,
            int difficulty,
            java.util.Set<String> tags) {}

    public record ResourceInfoUpdateRequest(String title, String description, String contentUrl,
                                            String coverUrl, int difficulty,
                                            java.util.Set<String> tags) {}

    public record ResourceMetadataUpdateRequest(java.util.Map<String, Object> metadata) {}

    public record DeleteRequest(String reason) {}

    static PageResponse toPage(PageResult<?> result) {
        return new PageResponse<>(result.content(), result.page(), result.size(),
                result.totalElements(), result.totalPages(), result.first(), result.last());
    }
}
