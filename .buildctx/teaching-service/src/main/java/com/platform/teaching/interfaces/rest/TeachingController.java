package com.platform.teaching.interfaces.rest;

import com.platform.teaching.application.command.TeachingCommands;
import com.platform.teaching.application.query.TeachingQueries;
import com.platform.teaching.application.service.TeachingApplicationService;
import com.platform.teaching.interfaces.rest.assembler.TeachingDtoAssembler;
import com.platform.teaching.interfaces.rest.dto.TeachingDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 教学互动 REST 控制器 - 作业、考试、讨论、直播
 */
@RestController
@RequestMapping("/api/v1/teaching")
@RequiredArgsConstructor
@Tag(name = "教学互动管理", description = "作业、考试、讨论、直播等教学互动功能")
public class TeachingController {

    private final TeachingApplicationService teachingApplicationService;
    private final TeachingDtoAssembler dtoAssembler;

    // ==========================================
    // Assignment Endpoints
    // ==========================================

    @PostMapping("/assignments")
    @Operation(summary = "创建作业")
    public ResponseEntity<TeachingDtos.AssignmentResponse> createAssignment(
            @Valid @RequestBody TeachingDtos.CreateAssignmentRequest request) {
        
        TeachingCommands.CreateAssignmentCommand cmd = TeachingCommands.CreateAssignmentCommand.builder()
                .tenantId(request.getTenantId())
                .courseId(request.getCourseId())
                .learningPathId(request.getLearningPathId())
                .stepId(request.getStepId())
                .title(request.getTitle())
                .description(request.getDescription())
                .instructions(request.getInstructions())
                .maxScore(request.getMaxScore())
                .passingScore(request.getPassingScore())
                .allowLateSubmission(request.getAllowLateSubmission())
                .latePenaltyPercent(request.getLatePenaltyPercent())
                .maxAttempts(request.getMaxAttempts())
                .startAt(request.getStartAt())
                .dueAt(request.getDueAt())
                .createdBy(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(
                dtoAssembler.toResponse(teachingApplicationService.createAssignment(cmd)));
    }

    @GetMapping("/assignments/{id}")
    @Operation(summary = "获取作业详情")
    public ResponseEntity<TeachingDtos.AssignmentResponse> getAssignment(@PathVariable UUID id) {
        TeachingQueries.GetAssignmentByIdQuery query = TeachingQueries.GetAssignmentByIdQuery.builder()
                .id(id)
                .build();

        return teachingApplicationService.getAssignmentById(query)
                .map(dtoAssembler::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/assignments")
    @Operation(summary = "分页查询作业列表")
    public ResponseEntity<TeachingDtos.AssignmentPageResponse> listAssignments(
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) UUID learningPathId,
            @RequestParam(required = false) UUID stepId,
            @RequestParam(required = false) String status,
            Pageable pageable) {
        
        TeachingQueries.ListAssignmentsQuery query = TeachingQueries.ListAssignmentsQuery.builder()
                .tenantId(tenantId)
                .courseId(courseId)
                .learningPathId(learningPathId)
                .stepId(stepId)
                .status(status)
                .page(pageable.getPageNumber())
                .size(pageable.getPageSize())
                .sortBy(pageable.getSort().toString())
                .sortDirection(pageable.getSort().isEmpty() || pageable.getSort().iterator().next().getDirection().isAscending() ? "ASC" : "DESC")
                .build();

        return ResponseEntity.ok(dtoAssembler.toAssignmentPageResponse(
                teachingApplicationService.listAssignments(query)));
    }

    @GetMapping("/assignments/open")
    @Operation(summary = "获取可提交的作业列表")
    public ResponseEntity<java.util.List<TeachingDtos.AssignmentResponse>> getOpenAssignments(
            @RequestParam UUID tenantId,
            @RequestParam UUID userId,
            @RequestParam(required = false) LocalDateTime now) {
        
        TeachingQueries.GetOpenAssignmentsQuery query = TeachingQueries.GetOpenAssignmentsQuery.builder()
                .tenantId(tenantId)
                .userId(userId)
                .now(now != null ? now : LocalDateTime.now())
                .build();

        return ResponseEntity.ok(teachingApplicationService.getOpenAssignments(query).stream()
                .map(dtoAssembler::toResponse)
                .collect(java.util.stream.Collectors.toList()));
    }

    @PutMapping("/assignments/{id}")
    @Operation(summary = "更新作业")
    public ResponseEntity<TeachingDtos.AssignmentResponse> updateAssignment(
            @PathVariable UUID id,
            @Valid @RequestBody TeachingDtos.UpdateAssignmentRequest request) {
        
        TeachingCommands.UpdateAssignmentCommand cmd = TeachingCommands.UpdateAssignmentCommand.builder()
                .id(com.platform.teaching.domain.model.valueobjects.AssignmentId.of(id))
                .title(request.getTitle())
                .description(request.getDescription())
                .instructions(request.getInstructions())
                .maxScore(request.getMaxScore())
                .passingScore(request.getPassingScore())
                .allowLateSubmission(request.getAllowLateSubmission())
                .latePenaltyPercent(request.getLatePenaltyPercent())
                .maxAttempts(request.getMaxAttempts())
                .startAt(request.getStartAt())
                .dueAt(request.getDueAt())
                .build();

        return ResponseEntity.ok(dtoAssembler.toResponse(
                teachingApplicationService.updateAssignment(cmd)));
    }

    @PostMapping("/assignments/{id}/publish")
    @Operation(summary = "发布作业")
    public ResponseEntity<TeachingDtos.AssignmentResponse> publishAssignment(@PathVariable UUID id) {
        TeachingCommands.PublishAssignmentCommand cmd = TeachingCommands.PublishAssignmentCommand.builder()
                .id(com.platform.teaching.domain.model.valueobjects.AssignmentId.of(id))
                .build();

        return ResponseEntity.ok(dtoAssembler.toResponse(
                teachingApplicationService.publishAssignment(cmd)));
    }

    @PostMapping("/assignments/{id}/start")
    @Operation(summary = "开始作业")
    public ResponseEntity<TeachingDtos.AssignmentResponse> startAssignment(@PathVariable UUID id) {
        TeachingCommands.StartAssignmentCommand cmd = TeachingCommands.StartAssignmentCommand.builder()
                .id(com.platform.teaching.domain.model.valueobjects.AssignmentId.of(id))
                .build();

        return ResponseEntity.ok(dtoAssembler.toResponse(
                teachingApplicationService.startAssignment(cmd)));
    }

    @PostMapping("/assignments/{id}/end")
    @Operation(summary = "结束作业")
    public ResponseEntity<TeachingDtos.AssignmentResponse> endAssignment(@PathVariable UUID id) {
        TeachingCommands.EndAssignmentCommand cmd = TeachingCommands.EndAssignmentCommand.builder()
                .id(com.platform.teaching.domain.model.valueobjects.AssignmentId.of(id))
                .build();

        return ResponseEntity.ok(dtoAssembler.toResponse(
                teachingApplicationService.endAssignment(cmd)));
    }

    @PostMapping("/assignments/{id}/archive")
    @Operation(summary = "归档作业")
    public ResponseEntity<TeachingDtos.AssignmentResponse> archiveAssignment(@PathVariable UUID id) {
        TeachingCommands.ArchiveAssignmentCommand cmd = TeachingCommands.ArchiveAssignmentCommand.builder()
                .id(com.platform.teaching.domain.model.valueobjects.AssignmentId.of(id))
                .build();

        return ResponseEntity.ok(dtoAssembler.toResponse(
                teachingApplicationService.archiveAssignment(cmd)));
    }

    @DeleteMapping("/assignments/{id}")
    @Operation(summary = "删除作业")
    public ResponseEntity<Void> deleteAssignment(@PathVariable UUID id) {
        TeachingCommands.DeleteAssignmentCommand cmd = TeachingCommands.DeleteAssignmentCommand.builder()
                .id(com.platform.teaching.domain.model.valueobjects.AssignmentId.of(id))
                .build();

        teachingApplicationService.deleteAssignment(cmd);
        return ResponseEntity.noContent().build();
    }

    // ==========================================
    // Exam Endpoints
    // ==========================================

    @PostMapping("/exams")
    @Operation(summary = "创建考试")
    public ResponseEntity<TeachingDtos.ExamResponse> createExam(
            @Valid @RequestBody TeachingDtos.CreateExamRequest request) {
        
        TeachingCommands.CreateExamCommand cmd = TeachingCommands.CreateExamCommand.builder()
                .tenantId(request.getTenantId())
                .courseId(request.getCourseId())
                .learningPathId(request.getLearningPathId())
                .stepId(request.getStepId())
                .title(request.getTitle())
                .description(request.getDescription())
                .instructions(request.getInstructions())
                .durationMinutes(request.getDurationMinutes())
                .maxScore(request.getMaxScore())
                .passingScore(request.getPassingScore())
                .maxAttempts(request.getMaxAttempts())
                .shuffleQuestions(request.getShuffleQuestions())
                .shuffleOptions(request.getShuffleOptions())
                .showResultsImmediately(request.getShowResultsImmediately())
                .startAt(request.getStartAt())
                .endAt(request.getEndAt())
                .createdBy(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(
                dtoAssembler.toResponse(teachingApplicationService.createExam(cmd)));
    }

    @GetMapping("/exams/{id}")
    @Operation(summary = "获取考试详情")
    public ResponseEntity<TeachingDtos.ExamResponse> getExam(@PathVariable UUID id) {
        TeachingQueries.GetExamByIdQuery query = TeachingQueries.GetExamByIdQuery.builder()
                .id(id)
                .build();

        return teachingApplicationService.getExamById(query)
                .map(dtoAssembler::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/exams")
    @Operation(summary = "分页查询考试列表")
    public ResponseEntity<TeachingDtos.ExamPageResponse> listExams(
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) UUID learningPathId,
            @RequestParam(required = false) UUID stepId,
            @RequestParam(required = false) String status,
            Pageable pageable) {
        
        TeachingQueries.ListExamsQuery query = TeachingQueries.ListExamsQuery.builder()
                .tenantId(tenantId)
                .courseId(courseId)
                .learningPathId(learningPathId)
                .stepId(stepId)
                .status(status)
                .page(pageable.getPageNumber())
                .size(pageable.getPageSize())
                .sortBy(pageable.getSort().toString())
                .sortDirection(pageable.getSort().isEmpty() || pageable.getSort().iterator().next().getDirection().isAscending() ? "ASC" : "DESC")
                .build();

        return ResponseEntity.ok(dtoAssembler.toExamPageResponse(
                teachingApplicationService.listExams(query)));
    }

    @GetMapping("/exams/available")
    @Operation(summary = "获取可参加的考试列表")
    public ResponseEntity<java.util.List<TeachingDtos.ExamResponse>> getAvailableExams(
            @RequestParam UUID tenantId,
            @RequestParam UUID userId,
            @RequestParam(required = false) LocalDateTime now) {
        
        TeachingQueries.GetAvailableExamsQuery query = TeachingQueries.GetAvailableExamsQuery.builder()
                .tenantId(tenantId)
                .userId(userId)
                .now(now != null ? now : LocalDateTime.now())
                .build();

        return ResponseEntity.ok(teachingApplicationService.getAvailableExams(query).stream()
                .map(dtoAssembler::toResponse)
                .collect(java.util.stream.Collectors.toList()));
    }

    @PutMapping("/exams/{id}")
    @Operation(summary = "更新考试")
    public ResponseEntity<TeachingDtos.ExamResponse> updateExam(
            @PathVariable UUID id,
            @Valid @RequestBody TeachingDtos.UpdateExamRequest request) {
        
        TeachingCommands.UpdateExamCommand cmd = TeachingCommands.UpdateExamCommand.builder()
                .id(com.platform.teaching.domain.model.valueobjects.ExamId.of(id))
                .title(request.getTitle())
                .description(request.getDescription())
                .instructions(request.getInstructions())
                .durationMinutes(request.getDurationMinutes())
                .maxScore(request.getMaxScore())
                .passingScore(request.getPassingScore())
                .maxAttempts(request.getMaxAttempts())
                .shuffleQuestions(request.getShuffleQuestions())
                .shuffleOptions(request.getShuffleOptions())
                .showResultsImmediately(request.getShowResultsImmediately())
                .startAt(request.getStartAt())
                .endAt(request.getEndAt())
                .build();

        return ResponseEntity.ok(dtoAssembler.toResponse(
                teachingApplicationService.updateExam(cmd)));
    }

    @PostMapping("/exams/{id}/publish")
    @Operation(summary = "发布考试")
    public ResponseEntity<TeachingDtos.ExamResponse> publishExam(@PathVariable UUID id) {
        TeachingCommands.PublishExamCommand cmd = TeachingCommands.PublishExamCommand.builder()
                .id(com.platform.teaching.domain.model.valueobjects.ExamId.of(id))
                .build();

        return ResponseEntity.ok(dtoAssembler.toResponse(
                teachingApplicationService.publishExam(cmd)));
    }

    @PostMapping("/exams/{id}/start")
    @Operation(summary = "开始考试")
    public ResponseEntity<TeachingDtos.ExamResponse> startExam(@PathVariable UUID id) {
        TeachingCommands.StartExamCommand cmd = TeachingCommands.StartExamCommand.builder()
                .id(com.platform.teaching.domain.model.valueobjects.ExamId.of(id))
                .build();

        return ResponseEntity.ok(dtoAssembler.toResponse(
                teachingApplicationService.startExam(cmd)));
    }

    @PostMapping("/exams/{id}/end")
    @Operation(summary = "结束考试")
    public ResponseEntity<TeachingDtos.ExamResponse> endExam(@PathVariable UUID id) {
        TeachingCommands.EndExamCommand cmd = TeachingCommands.EndExamCommand.builder()
                .id(com.platform.teaching.domain.model.valueobjects.ExamId.of(id))
                .build();

        return ResponseEntity.ok(dtoAssembler.toResponse(
                teachingApplicationService.endExam(cmd)));
    }

    @PostMapping("/exams/{id}/archive")
    @Operation(summary = "归档考试")
    public ResponseEntity<TeachingDtos.ExamResponse> archiveExam(@PathVariable UUID id) {
        TeachingCommands.ArchiveExamCommand cmd = TeachingCommands.ArchiveExamCommand.builder()
                .id(com.platform.teaching.domain.model.valueobjects.ExamId.of(id))
                .build();

        return ResponseEntity.ok(dtoAssembler.toResponse(
                teachingApplicationService.archiveExam(cmd)));
    }

    @DeleteMapping("/exams/{id}")
    @Operation(summary = "删除考试")
    public ResponseEntity<Void> deleteExam(@PathVariable UUID id) {
        TeachingCommands.DeleteExamCommand cmd = TeachingCommands.DeleteExamCommand.builder()
                .id(com.platform.teaching.domain.model.valueobjects.ExamId.of(id))
                .build();

        teachingApplicationService.deleteExam(cmd);
        return ResponseEntity.noContent().build();
    }

    // ==========================================
    // Discussion Endpoints
    // ==========================================

    @PostMapping("/discussions")
    @Operation(summary = "创建讨论")
    public ResponseEntity<TeachingDtos.DiscussionResponse> createDiscussion(
            @Valid @RequestBody TeachingDtos.CreateDiscussionRequest request) {
        
        TeachingCommands.CreateDiscussionCommand cmd = TeachingCommands.CreateDiscussionCommand.builder()
                .tenantId(request.getTenantId())
                .courseId(request.getCourseId())
                .learningPathId(request.getLearningPathId())
                .stepId(request.getStepId())
                .title(request.getTitle())
                .content(request.getContent())
                .authorId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .authorName("current-user")
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(
                dtoAssembler.toResponse(teachingApplicationService.createDiscussion(cmd)));
    }

    @GetMapping("/discussions/{id}")
    @Operation(summary = "获取讨论详情")
    public ResponseEntity<TeachingDtos.DiscussionResponse> getDiscussion(@PathVariable UUID id) {
        TeachingQueries.GetDiscussionByIdQuery query = TeachingQueries.GetDiscussionByIdQuery.builder()
                .id(id)
                .build();

        return teachingApplicationService.getDiscussionById(query)
                .map(dtoAssembler::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/discussions")
    @Operation(summary = "分页查询讨论列表")
    public ResponseEntity<TeachingDtos.DiscussionPageResponse> listDiscussions(
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) UUID learningPathId,
            @RequestParam(required = false) UUID stepId,
            @RequestParam(required = false) UUID authorId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean pinnedOnly,
            Pageable pageable) {
        
        TeachingQueries.ListDiscussionsQuery query = TeachingQueries.ListDiscussionsQuery.builder()
                .tenantId(tenantId)
                .courseId(courseId)
                .learningPathId(learningPathId)
                .stepId(stepId)
                .authorId(authorId)
                .status(status)
                .page(pageable.getPageNumber())
                .size(pageable.getPageSize())
                .sortBy(pageable.getSort().toString())
                .sortDirection(pageable.getSort().isEmpty() || pageable.getSort().iterator().next().getDirection().isAscending() ? "ASC" : "DESC")
                .build();

        return ResponseEntity.ok(dtoAssembler.toDiscussionPageResponse(
                teachingApplicationService.listDiscussions(query)));
    }

    @GetMapping("/discussions/pinned")
    @Operation(summary = "获取置顶讨论")
    public ResponseEntity<java.util.List<TeachingDtos.DiscussionResponse>> getPinnedDiscussions(
            @RequestParam UUID tenantId) {
        
        TeachingQueries.GetPinnedDiscussionsQuery query = TeachingQueries.GetPinnedDiscussionsQuery.builder()
                .tenantId(tenantId)
                .build();

        return ResponseEntity.ok(teachingApplicationService.getPinnedDiscussions(query).stream()
                .map(dtoAssembler::toResponse)
                .collect(java.util.stream.Collectors.toList()));
    }

    @PutMapping("/discussions/{id}")
    @Operation(summary = "更新讨论")
    public ResponseEntity<TeachingDtos.DiscussionResponse> updateDiscussion(
            @PathVariable UUID id,
            @Valid @RequestBody TeachingDtos.UpdateDiscussionRequest request) {
        
        TeachingCommands.UpdateDiscussionCommand cmd = TeachingCommands.UpdateDiscussionCommand.builder()
                .id(com.platform.teaching.domain.model.valueobjects.DiscussionId.of(id))
                .title(request.getTitle())
                .content(request.getContent())
                .build();

        return ResponseEntity.ok(dtoAssembler.toResponse(
                teachingApplicationService.updateDiscussion(cmd)));
    }

    @PostMapping("/discussions/{id}/close")
    @Operation(summary = "关闭讨论")
    public ResponseEntity<TeachingDtos.DiscussionResponse> closeDiscussion(@PathVariable UUID id) {
        TeachingCommands.CloseDiscussionCommand cmd = TeachingCommands.CloseDiscussionCommand.builder()
                .id(com.platform.teaching.domain.model.valueobjects.DiscussionId.of(id))
                .build();

        return ResponseEntity.ok(dtoAssembler.toResponse(
                teachingApplicationService.closeDiscussion(cmd)));
    }

    @PostMapping("/discussions/{id}/reopen")
    @Operation(summary = "重新打开讨论")
    public ResponseEntity<TeachingDtos.DiscussionResponse> reopenDiscussion(@PathVariable UUID id) {
        TeachingCommands.ReopenDiscussionCommand cmd = TeachingCommands.ReopenDiscussionCommand.builder()
                .id(com.platform.teaching.domain.model.valueobjects.DiscussionId.of(id))
                .build();

        return ResponseEntity.ok(dtoAssembler.toResponse(
                teachingApplicationService.reopenDiscussion(cmd)));
    }

    @PostMapping("/discussions/{id}/pin")
    @Operation(summary = "置顶讨论")
    public ResponseEntity<TeachingDtos.DiscussionResponse> pinDiscussion(@PathVariable UUID id) {
        TeachingCommands.PinDiscussionCommand cmd = TeachingCommands.PinDiscussionCommand.builder()
                .id(com.platform.teaching.domain.model.valueobjects.DiscussionId.of(id))
                .build();

        return ResponseEntity.ok(dtoAssembler.toResponse(
                teachingApplicationService.pinDiscussion(cmd)));
    }

    @PostMapping("/discussions/{id}/unpin")
    @Operation(summary = "取消置顶讨论")
    public ResponseEntity<TeachingDtos.DiscussionResponse> unpinDiscussion(@PathVariable UUID id) {
        TeachingCommands.UnpinDiscussionCommand cmd = TeachingCommands.UnpinDiscussionCommand.builder()
                .id(com.platform.teaching.domain.model.valueobjects.DiscussionId.of(id))
                .build();

        return ResponseEntity.ok(dtoAssembler.toResponse(
                teachingApplicationService.unpinDiscussion(cmd)));
    }

    @PostMapping("/discussions/{id}/lock")
    @Operation(summary = "锁定讨论")
    public ResponseEntity<TeachingDtos.DiscussionResponse> lockDiscussion(@PathVariable UUID id) {
        TeachingCommands.LockDiscussionCommand cmd = TeachingCommands.LockDiscussionCommand.builder()
                .id(com.platform.teaching.domain.model.valueobjects.DiscussionId.of(id))
                .build();

        return ResponseEntity.ok(dtoAssembler.toResponse(
                teachingApplicationService.lockDiscussion(cmd)));
    }

    @PostMapping("/discussions/{id}/unlock")
    @Operation(summary = "解锁讨论")
    public ResponseEntity<TeachingDtos.DiscussionResponse> unlockDiscussion(@PathVariable UUID id) {
        TeachingCommands.UnlockDiscussionCommand cmd = TeachingCommands.UnlockDiscussionCommand.builder()
                .id(com.platform.teaching.domain.model.valueobjects.DiscussionId.of(id))
                .build();

        return ResponseEntity.ok(dtoAssembler.toResponse(
                teachingApplicationService.unlockDiscussion(cmd)));
    }

    @DeleteMapping("/discussions/{id}")
    @Operation(summary = "删除讨论")
    public ResponseEntity<Void> deleteDiscussion(@PathVariable UUID id) {
        TeachingCommands.DeleteDiscussionCommand cmd = TeachingCommands.DeleteDiscussionCommand.builder()
                .id(com.platform.teaching.domain.model.valueobjects.DiscussionId.of(id))
                .build();

        teachingApplicationService.deleteDiscussion(cmd);
        return ResponseEntity.noContent().build();
    }

    // ==========================================
    // Live Session Endpoints
    // ==========================================

    @PostMapping("/live-sessions")
    @Operation(summary = "创建直播会话")
    public ResponseEntity<TeachingDtos.LiveSessionResponse> createLiveSession(
            @Valid @RequestBody TeachingDtos.CreateLiveSessionRequest request) {
        
        TeachingCommands.CreateLiveSessionCommand cmd = TeachingCommands.CreateLiveSessionCommand.builder()
                .tenantId(request.getTenantId())
                .courseId(request.getCourseId())
                .learningPathId(request.getLearningPathId())
                .stepId(request.getStepId())
                .title(request.getTitle())
                .description(request.getDescription())
                .instructorId(request.getInstructorId())
                .instructorName(request.getInstructorName())
                .scheduledStartAt(request.getScheduledStartAt())
                .scheduledEndAt(request.getScheduledEndAt())
                .maxParticipants(request.getMaxParticipants())
                .recordingEnabled(request.getRecordingEnabled())
                .chatEnabled(request.getChatEnabled())
                .createdBy(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(
                dtoAssembler.toResponse(teachingApplicationService.createLiveSession(cmd)));
    }

    @GetMapping("/live-sessions/{id}")
    @Operation(summary = "获取直播会话详情")
    public ResponseEntity<TeachingDtos.LiveSessionResponse> getLiveSession(@PathVariable UUID id) {
        TeachingQueries.GetLiveSessionByIdQuery query = TeachingQueries.GetLiveSessionByIdQuery.builder()
                .id(id)
                .build();

        return teachingApplicationService.getLiveSessionById(query)
                .map(dtoAssembler::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/live-sessions")
    @Operation(summary = "分页查询直播会话列表")
    public ResponseEntity<TeachingDtos.LiveSessionPageResponse> listLiveSessions(
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) UUID learningPathId,
            @RequestParam(required = false) UUID instructorId,
            @RequestParam(required = false) String status,
            Pageable pageable) {
        
        TeachingQueries.ListLiveSessionsQuery query = TeachingQueries.ListLiveSessionsQuery.builder()
                .tenantId(tenantId)
                .courseId(courseId)
                .learningPathId(learningPathId)
                .instructorId(instructorId)
                .status(status)
                .page(pageable.getPageNumber())
                .size(pageable.getPageSize())
                .sortBy(pageable.getSort().toString())
                .sortDirection(pageable.getSort().isEmpty() || pageable.getSort().iterator().next().getDirection().isAscending() ? "ASC" : "DESC")
                .build();

        return ResponseEntity.ok(dtoAssembler.toLiveSessionPageResponse(
                teachingApplicationService.listLiveSessions(query)));
    }

    @GetMapping("/live-sessions/upcoming")
    @Operation(summary = "获取即将开始的直播")
    public ResponseEntity<java.util.List<TeachingDtos.LiveSessionResponse>> getUpcomingSessions(
            @RequestParam UUID tenantId,
            @RequestParam LocalDateTime from,
            @RequestParam LocalDateTime to) {
        
        TeachingQueries.GetUpcomingSessionsQuery query = TeachingQueries.GetUpcomingSessionsQuery.builder()
                .tenantId(tenantId)
                .from(from)
                .to(to)
                .build();

        return ResponseEntity.ok(teachingApplicationService.getUpcomingSessions(query).stream()
                .map(dtoAssembler::toResponse)
                .collect(java.util.stream.Collectors.toList()));
    }

    @GetMapping("/live-sessions/live")
    @Operation(summary = "获取正在进行的直播")
    public ResponseEntity<java.util.List<TeachingDtos.LiveSessionResponse>> getLiveSessions(
            @RequestParam UUID tenantId) {
        
        TeachingQueries.GetLiveSessionsQuery query = TeachingQueries.GetLiveSessionsQuery.builder()
                .tenantId(tenantId)
                .build();

        return ResponseEntity.ok(teachingApplicationService.getLiveSessions(query).stream()
                .map(dtoAssembler::toResponse)
                .collect(java.util.stream.Collectors.toList()));
    }

    @PutMapping("/live-sessions/{id}")
    @Operation(summary = "更新直播会话")
    public ResponseEntity<TeachingDtos.LiveSessionResponse> updateLiveSession(
            @PathVariable UUID id,
            @Valid @RequestBody TeachingDtos.UpdateLiveSessionRequest request) {
        
        TeachingCommands.UpdateLiveSessionCommand cmd = TeachingCommands.UpdateLiveSessionCommand.builder()
                .id(com.platform.teaching.domain.model.valueobjects.LiveSessionId.of(id))
                .title(request.getTitle())
                .description(request.getDescription())
                .scheduledStartAt(request.getScheduledStartAt())
                .scheduledEndAt(request.getScheduledEndAt())
                .maxParticipants(request.getMaxParticipants())
                .recordingEnabled(request.getRecordingEnabled())
                .chatEnabled(request.getChatEnabled())
                .build();

        return ResponseEntity.ok(dtoAssembler.toResponse(
                teachingApplicationService.updateLiveSession(cmd)));
    }

    @PostMapping("/live-sessions/{id}/prepare")
    @Operation(summary = "准备直播")
    public ResponseEntity<TeachingDtos.LiveSessionResponse> prepareLiveSession(@PathVariable UUID id) {
        TeachingCommands.PrepareLiveSessionCommand cmd = TeachingCommands.PrepareLiveSessionCommand.builder()
                .id(com.platform.teaching.domain.model.valueobjects.LiveSessionId.of(id))
                .build();

        return ResponseEntity.ok(dtoAssembler.toResponse(
                teachingApplicationService.prepareLiveSession(cmd)));
    }

    @PostMapping("/live-sessions/{id}/start")
    @Operation(summary = "开始直播")
    public ResponseEntity<TeachingDtos.LiveSessionResponse> startLiveSession(@PathVariable UUID id) {
        TeachingCommands.StartLiveSessionCommand cmd = TeachingCommands.StartLiveSessionCommand.builder()
                .id(com.platform.teaching.domain.model.valueobjects.LiveSessionId.of(id))
                .build();

        return ResponseEntity.ok(dtoAssembler.toResponse(
                teachingApplicationService.startLiveSession(cmd)));
    }

    @PostMapping("/live-sessions/{id}/end")
    @Operation(summary = "结束直播")
    public ResponseEntity<TeachingDtos.LiveSessionResponse> endLiveSession(@PathVariable UUID id) {
        TeachingCommands.EndLiveSessionCommand cmd = TeachingCommands.EndLiveSessionCommand.builder()
                .id(com.platform.teaching.domain.model.valueobjects.LiveSessionId.of(id))
                .build();

        return ResponseEntity.ok(dtoAssembler.toResponse(
                teachingApplicationService.endLiveSession(cmd)));
    }

    @PostMapping("/live-sessions/{id}/cancel")
    @Operation(summary = "取消直播")
    public ResponseEntity<TeachingDtos.LiveSessionResponse> cancelLiveSession(@PathVariable UUID id) {
        TeachingCommands.CancelLiveSessionCommand cmd = TeachingCommands.CancelLiveSessionCommand.builder()
                .id(com.platform.teaching.domain.model.valueobjects.LiveSessionId.of(id))
                .build();

        return ResponseEntity.ok(dtoAssembler.toResponse(
                teachingApplicationService.cancelLiveSession(cmd)));
    }

    @PostMapping("/live-sessions/{id}/join")
    @Operation(summary = "加入直播")
    public ResponseEntity<TeachingDtos.LiveSessionResponse> joinLiveSession(
            @PathVariable UUID id,
            @Valid @RequestBody TeachingDtos.JoinLiveSessionRequest request) {
        
        TeachingCommands.JoinLiveSessionCommand cmd = TeachingCommands.JoinLiveSessionCommand.builder()
                .id(com.platform.teaching.domain.model.valueobjects.LiveSessionId.of(id))
                .participantId(request.getParticipantId())
                .build();

        return ResponseEntity.ok(dtoAssembler.toResponse(
                teachingApplicationService.joinLiveSession(cmd)));
    }

    @PostMapping("/live-sessions/{id}/leave")
    @Operation(summary = "离开直播")
    public ResponseEntity<TeachingDtos.LiveSessionResponse> leaveLiveSession(
            @PathVariable UUID id,
            @Valid @RequestBody TeachingDtos.JoinLiveSessionRequest request) {
        
        TeachingCommands.LeaveLiveSessionCommand cmd = TeachingCommands.LeaveLiveSessionCommand.builder()
                .id(com.platform.teaching.domain.model.valueobjects.LiveSessionId.of(id))
                .participantId(request.getParticipantId())
                .build();

        return ResponseEntity.ok(dtoAssembler.toResponse(
                teachingApplicationService.leaveLiveSession(cmd)));
    }

    @PostMapping("/live-sessions/{id}/playback-url")
    @Operation(summary = "设置回放地址")
    public ResponseEntity<TeachingDtos.LiveSessionResponse> setPlaybackUrl(
            @PathVariable UUID id,
            @Valid @RequestBody TeachingDtos.SetPlaybackUrlRequest request) {
        
        TeachingCommands.SetPlaybackUrlCommand cmd = TeachingCommands.SetPlaybackUrlCommand.builder()
                .id(com.platform.teaching.domain.model.valueobjects.LiveSessionId.of(id))
                .playbackUrl(request.getPlaybackUrl())
                .build();

        return ResponseEntity.ok(dtoAssembler.toResponse(
                teachingApplicationService.setPlaybackUrl(cmd)));
    }

    @DeleteMapping("/live-sessions/{id}")
    @Operation(summary = "删除直播会话")
    public ResponseEntity<Void> deleteLiveSession(@PathVariable UUID id) {
        TeachingCommands.DeleteLiveSessionCommand cmd = TeachingCommands.DeleteLiveSessionCommand.builder()
                .id(com.platform.teaching.domain.model.valueobjects.LiveSessionId.of(id))
                .build();

        teachingApplicationService.deleteLiveSession(cmd);
        return ResponseEntity.noContent().build();
    }
}