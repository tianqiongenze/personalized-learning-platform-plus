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
 * 章节控制器。
 */
@RestController
@RequestMapping("/chapters")
@RequiredArgsConstructor
@Tag(name = "章节管理", description = "课程章节管理 API")
public class ChapterController {

    private final CourseApplicationService courseService;

    private String currentUser(HttpServletRequest request) {
        String u = request.getHeader("X-User-Id");
        return (u == null || u.isBlank()) ? "system" : u;
    }

    @PostMapping
    @Operation(summary = "创建章节")
    public ResponseEntity<ApiResponse<String>> createChapter(
            @Valid @RequestBody ChapterCreateRequest request, HttpServletRequest http) {
        ChapterId id = courseService.createChapter(new CreateChapterCommand(
                CourseId.of(request.courseId()), request.code(), request.name(),
                request.description(),
                request.parentId() == null ? null : ChapterId.of(request.parentId()),
                request.order(), request.difficulty(), request.estimatedHours(),
                currentUser(http)));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("章节创建成功", id.getValue().toString()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取章节详情")
    public ResponseEntity<ApiResponse<ChapterDetailDto>> getChapter(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(
                courseService.findChapterById(ChapterId.of(id))));
    }

    @GetMapping
    @Operation(summary = "分页查询章节")
    public ResponseEntity<ApiResponse<PageResponse<ChapterListDto>>> listChapters(
            @RequestParam String courseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "order") String sortBy,
            @RequestParam(defaultValue = "asc") String sortOrder) {
        PageResult<ChapterListDto> result = courseService.findChapterPage(new ChapterPageQuery(
                CourseId.of(courseId), page, size, status, keyword, sortBy, sortOrder));
        return ResponseEntity.ok(ApiResponse.success(toPage(result)));
    }

    @GetMapping("/by-course/{courseId}")
    @Operation(summary = "查询课程下全部章节")
    public ResponseEntity<ApiResponse<List<ChapterSimpleDto>>> listByCourse(@PathVariable String courseId) {
        return ResponseEntity.ok(ApiResponse.success(
                courseService.findChaptersByCourseId(CourseId.of(courseId))));
    }

    @PutMapping("/{id}/info")
    @Operation(summary = "更新章节信息")
    public ResponseEntity<ApiResponse<Void>> updateChapterInfo(
            @PathVariable String id, @Valid @RequestBody ChapterInfoUpdateRequest request,
            HttpServletRequest http) {
        courseService.updateChapterInfo(new UpdateChapterInfoCommand(
                ChapterId.of(id), request.name(), request.description(),
                request.difficulty(), request.estimatedHours(), currentUser(http)));
        return ResponseEntity.ok(ApiResponse.success("章节更新成功", null));
    }

    @PutMapping("/{id}/order")
    @Operation(summary = "更新章节排序")
    public ResponseEntity<ApiResponse<Void>> updateChapterOrder(
            @PathVariable String id, @RequestBody OrderUpdateRequest request, HttpServletRequest http) {
        courseService.updateChapterOrder(new UpdateChapterOrderCommand(
                ChapterId.of(id), request.order(), currentUser(http)));
        return ResponseEntity.ok(ApiResponse.success("章节排序更新成功", null));
    }

    @PostMapping("/{id}/knowledge-points/{kpId}")
    @Operation(summary = "章节关联知识点")
    public ResponseEntity<ApiResponse<Void>> addKnowledgePoint(
            @PathVariable String id, @PathVariable String kpId, HttpServletRequest http) {
        courseService.addChapterKnowledgePoint(new AddChapterKnowledgePointCommand(
                ChapterId.of(id), KnowledgePointId.of(kpId), currentUser(http)));
        return ResponseEntity.ok(ApiResponse.success("知识点关联成功", null));
    }

    @DeleteMapping("/{id}/knowledge-points/{kpId}")
    @Operation(summary = "章节移除知识点")
    public ResponseEntity<ApiResponse<Void>> removeKnowledgePoint(
            @PathVariable String id, @PathVariable String kpId, HttpServletRequest http) {
        courseService.removeChapterKnowledgePoint(new RemoveChapterKnowledgePointCommand(
                ChapterId.of(id), KnowledgePointId.of(kpId), currentUser(http)));
        return ResponseEntity.ok(ApiResponse.success("知识点移除成功", null));
    }

    @PostMapping("/{id}/publish")
    @Operation(summary = "发布章节")
    public ResponseEntity<ApiResponse<Void>> publishChapter(@PathVariable String id, HttpServletRequest http) {
        courseService.publishChapter(new PublishChapterCommand(ChapterId.of(id), currentUser(http)));
        return ResponseEntity.ok(ApiResponse.success("章节发布成功", null));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除章节")
    public ResponseEntity<ApiResponse<Void>> deleteChapter(
            @PathVariable String id, @RequestBody DeleteRequest request, HttpServletRequest http) {
        courseService.deleteChapter(new DeleteChapterCommand(
                ChapterId.of(id), currentUser(http), request.reason()));
        return ResponseEntity.ok(ApiResponse.success("章节删除成功", null));
    }

    public record ChapterCreateRequest(
            @jakarta.validation.constraints.NotBlank String courseId,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(min = 2, max = 50) String code,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 200) String name,
            @jakarta.validation.constraints.Size(max = 1000) String description,
            String parentId, int order, int difficulty, int estimatedHours) {}

    public record ChapterInfoUpdateRequest(String name, String description,
                                           int difficulty, int estimatedHours) {}

    public record OrderUpdateRequest(int order) {}

    public record DeleteRequest(String reason) {}

    static PageResponse toPage(PageResult<?> result) {
        return new PageResponse<>(result.content(), result.page(), result.size(),
                result.totalElements(), result.totalPages(), result.first(), result.last());
    }
}
