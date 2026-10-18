package com.platform.course.interfaces.rest;

import com.platform.course.application.command.*;
import com.platform.course.application.query.*;
import com.platform.course.application.service.CourseApplicationService;
import com.platform.course.domain.model.valueobjects.CourseId;
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
 * 课程资源控制器 —— 重构前 course-service 的 interfaces/rest 为空, 无任何 HTTP 出口,
 * 导致 AI 生成引擎与前端均无法访问课程/知识点/图谱数据(知识图谱链路断裂)。
 * 本控制器及 Chapter/KnowledgePoint/Resource/KnowledgeGraph 控制器补齐该缺口。
 *
 * <p>鉴权说明: 统一由 API 网关完成认证并透传 X-User-Id / X-Tenant-Id 头,
 * 服务间通过网关白名单保护; 细粒度 RBAC(课程创建者/教师/管理员)在 P1 阶段补齐
 * (参见 docs/IP_SUPPORT_REFACTOR_PLAN.md)。
 */
@RestController
@RequestMapping("/courses")
@RequiredArgsConstructor
@Tag(name = "课程资源", description = "课程 / 章节 / 知识点 / 资源全生命周期管理 API")
public class CourseController {

    private final CourseApplicationService courseService;

    private String tenantId(HttpServletRequest request) {
        String t = request.getHeader("X-Tenant-Id");
        return (t == null || t.isBlank()) ? "default" : t;
    }

    private String currentUser(HttpServletRequest request) {
        String u = request.getHeader("X-User-Id");
        return (u == null || u.isBlank()) ? "system" : u;
    }

    // ========== 创建 ==========

    @PostMapping
    @Operation(summary = "创建课程")
    public ResponseEntity<ApiResponse<String>> createCourse(
            @Valid @RequestBody CourseCreateRequest request,
            HttpServletRequest http) {
        CourseId id = courseService.createCourse(new CreateCourseCommand(
                request.code(), request.name(), request.description(), request.subject(),
                request.gradeLevel(), request.semester(), request.coverUrl(),
                request.settings(), currentUser(http)));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("课程创建成功", id.getValue().toString()));
    }

    // ========== 查询 ==========

    @GetMapping("/{id}")
    @Operation(summary = "获取课程详情")
    public ResponseEntity<ApiResponse<CourseDetailDto>> getCourse(
            @PathVariable String id, HttpServletRequest http) {
        CourseDetailDto dto = courseService.findCourseById(CourseId.of(id));
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @GetMapping("/code/{code}")
    @Operation(summary = "根据编码获取课程详情")
    public ResponseEntity<ApiResponse<CourseDetailDto>> getCourseByCode(
            @PathVariable String code, HttpServletRequest http) {
        CourseDetailDto dto = courseService.findCourseByCode(tenantId(http), code);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @GetMapping
    @Operation(summary = "分页查询课程列表")
    public ResponseEntity<ApiResponse<PageResponse<CourseListDto>>> listCourses(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String gradeLevel,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortOrder,
            HttpServletRequest http) {
        CoursePageQuery query = new CoursePageQuery(
                tenantId(http), page, size, status, keyword, subject, gradeLevel, sortBy, sortOrder);
        PageResult<CourseListDto> result = courseService.findCoursePage(query);
        return ResponseEntity.ok(ApiResponse.success(toPage(result)));
    }

    @GetMapping("/mine")
    @Operation(summary = "查询当前用户创建的课程")
    public ResponseEntity<ApiResponse<List<CourseSimpleDto>>> listMyCourses(HttpServletRequest http) {
        return ResponseEntity.ok(ApiResponse.success(
                courseService.findCoursesByCreatorId(currentUser(http))));
    }

    @GetMapping("/statistics")
    @Operation(summary = "课程统计信息")
    public ResponseEntity<ApiResponse<CourseStatisticsDto>> statistics(HttpServletRequest http) {
        return ResponseEntity.ok(ApiResponse.success(courseService.courseStatistics(tenantId(http))));
    }

    // ========== 更新与状态流转 ==========

    @PutMapping("/{id}/info")
    @Operation(summary = "更新课程基本信息")
    public ResponseEntity<ApiResponse<Void>> updateCourseInfo(
            @PathVariable String id,
            @Valid @RequestBody CourseInfoUpdateRequest request,
            HttpServletRequest http) {
        courseService.updateCourseInfo(new UpdateCourseInfoCommand(
                CourseId.of(id), request.name(), request.description(), request.subject(),
                request.gradeLevel(), request.semester(), request.coverUrl(), currentUser(http)));
        return ResponseEntity.ok(ApiResponse.success("课程信息更新成功", null));
    }

    @PutMapping("/{id}/code")
    @Operation(summary = "更新课程编码")
    public ResponseEntity<ApiResponse<Void>> updateCourseCode(
            @PathVariable String id,
            @Valid @RequestBody CourseCodeUpdateRequest request,
            HttpServletRequest http) {
        courseService.updateCourseCode(new UpdateCourseCodeCommand(
                CourseId.of(id), request.code(), currentUser(http)));
        return ResponseEntity.ok(ApiResponse.success("课程编码更新成功", null));
    }

    @PutMapping("/{id}/settings")
    @Operation(summary = "更新课程配置")
    public ResponseEntity<ApiResponse<Void>> updateCourseSettings(
            @PathVariable String id,
            @Valid @RequestBody CourseSettingsUpdateRequest request,
            HttpServletRequest http) {
        courseService.updateCourseSettings(new UpdateCourseSettingsCommand(
                CourseId.of(id), request.settings(), currentUser(http)));
        return ResponseEntity.ok(ApiResponse.success("课程配置更新成功", null));
    }

    @PostMapping("/{id}/publish")
    @Operation(summary = "发布课程")
    public ResponseEntity<ApiResponse<Void>> publishCourse(@PathVariable String id, HttpServletRequest http) {
        courseService.publishCourse(new PublishCourseCommand(CourseId.of(id), currentUser(http)));
        return ResponseEntity.ok(ApiResponse.success("课程发布成功", null));
    }

    @PostMapping("/{id}/archive")
    @Operation(summary = "归档课程")
    public ResponseEntity<ApiResponse<Void>> archiveCourse(@PathVariable String id, HttpServletRequest http) {
        courseService.archiveCourse(new ArchiveCourseCommand(CourseId.of(id), currentUser(http)));
        return ResponseEntity.ok(ApiResponse.success("课程归档成功", null));
    }

    @PostMapping("/{id}/restore")
    @Operation(summary = "恢复课程发布态")
    public ResponseEntity<ApiResponse<Void>> restoreCourse(@PathVariable String id, HttpServletRequest http) {
        courseService.restoreCourse(new RestoreCourseCommand(CourseId.of(id), currentUser(http)));
        return ResponseEntity.ok(ApiResponse.success("课程恢复成功", null));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除课程(软删除)")
    public ResponseEntity<ApiResponse<Void>> deleteCourse(
            @PathVariable String id,
            @Valid @RequestBody CourseDeleteRequest request,
            HttpServletRequest http) {
        courseService.deleteCourse(new DeleteCourseCommand(
                CourseId.of(id), currentUser(http), request.reason()));
        return ResponseEntity.ok(ApiResponse.success("课程删除成功", null));
    }

    // ========== 请求体定义 ==========

    public record CourseCreateRequest(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(min = 2, max = 50) String code,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 200) String name,
            @jakarta.validation.constraints.Size(max = 2000) String description,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 50) String subject,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 20) String gradeLevel,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 20) String semester,
            String coverUrl,
            java.util.Map<String, Object> settings) {}

    public record CourseInfoUpdateRequest(
            String name, String description, String subject, String gradeLevel,
            String semester, String coverUrl) {}

    public record CourseCodeUpdateRequest(
            @jakarta.validation.constraints.NotBlank String code) {}

    public record CourseSettingsUpdateRequest(
            java.util.Map<String, Object> settings) {}

    public record CourseDeleteRequest(String reason) {}

    static PageResponse toPage(PageResult<?> result) {
        return new PageResponse<>(result.content(), result.page(), result.size(),
                result.totalElements(), result.totalPages(), result.first(), result.last());
    }
}
