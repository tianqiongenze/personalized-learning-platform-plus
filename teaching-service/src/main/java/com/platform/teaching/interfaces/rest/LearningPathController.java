package com.platform.teaching.interfaces.rest;

import com.platform.teaching.application.command.LearningPathCommands;
import com.platform.teaching.application.query.LearningPathQueries;
import com.platform.teaching.application.service.TeachingApplicationService;
import com.platform.teaching.interfaces.rest.assembler.LearningPathDtoAssembler;
import com.platform.teaching.interfaces.rest.dto.LearningPathDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 学习路径 REST 控制器
 */
@RestController
@RequestMapping("/learning-paths")
@RequiredArgsConstructor
@Tag(name = "学习路径管理", description = "学习路径的创建、查询、发布、归档等操作")
public class LearningPathController {

    private final TeachingApplicationService teachingApplicationService;
    private final LearningPathDtoAssembler dtoAssembler;

    @PostMapping
    @Operation(summary = "创建学习路径")
    public ResponseEntity<LearningPathDtos.LearningPathResponse> createLearningPath(
            @Valid @RequestBody LearningPathDtos.CreateLearningPathRequest request) {
        
        LearningPathCommands.CreateLearningPathCommand cmd = LearningPathCommands.CreateLearningPathCommand.builder()
                .tenantId(request.getTenantId())
                .courseId(request.getCourseId())
                .title(request.getTitle())
                .description(request.getDescription())
                .createdBy(java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .build();

        LearningPathDtos.LearningPathResponse response = dtoAssembler.toResponse(
                teachingApplicationService.createLearningPath(cmd));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取学习路径详情")
    public ResponseEntity<LearningPathDtos.LearningPathResponse> getLearningPath(@PathVariable UUID id) {
        LearningPathQueries.GetLearningPathByIdQuery query = LearningPathQueries.GetLearningPathByIdQuery.builder()
                .id(id)
                .build();

        return teachingApplicationService.getLearningPathById(query)
                .map(dtoAssembler::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    @Operation(summary = "分页查询学习路径列表")
    public ResponseEntity<LearningPathDtos.LearningPathPageResponse> listLearningPaths(
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) String status,
            Pageable pageable) {
        
        LearningPathQueries.ListLearningPathsQuery query = LearningPathQueries.ListLearningPathsQuery.builder()
                .tenantId(tenantId)
                .courseId(courseId)
                .status(status)
                .page(pageable.getPageNumber())
                .size(pageable.getPageSize())
                .sortBy(pageable.getSort().toString())
                .sortDirection(pageable.getSort().isEmpty() || pageable.getSort().iterator().next().getDirection().isAscending() ? "ASC" : "DESC")
                .build();

        return ResponseEntity.ok(dtoAssembler.toPageResponse(
                teachingApplicationService.listLearningPaths(query)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新学习路径")
    public ResponseEntity<LearningPathDtos.LearningPathResponse> updateLearningPath(
            @PathVariable UUID id,
            @Valid @RequestBody LearningPathDtos.UpdateLearningPathRequest request) {
        
        LearningPathCommands.UpdateLearningPathCommand cmd = LearningPathCommands.UpdateLearningPathCommand.builder()
                .id(com.platform.teaching.domain.model.valueobjects.LearningPathId.of(id))
                .title(request.getTitle())
                .description(request.getDescription())
                .build();

        return ResponseEntity.ok(dtoAssembler.toResponse(
                teachingApplicationService.updateLearningPath(cmd)));
    }

    @PostMapping("/{id}/publish")
    @Operation(summary = "发布学习路径")
    public ResponseEntity<LearningPathDtos.LearningPathResponse> publishLearningPath(@PathVariable UUID id) {
        LearningPathCommands.PublishLearningPathCommand cmd = LearningPathCommands.PublishLearningPathCommand.builder()
                .id(com.platform.teaching.domain.model.valueobjects.LearningPathId.of(id))
                .build();

        return ResponseEntity.ok(dtoAssembler.toResponse(
                teachingApplicationService.publishLearningPath(cmd)));
    }

    @PostMapping("/{id}/archive")
    @Operation(summary = "归档学习路径")
    public ResponseEntity<LearningPathDtos.LearningPathResponse> archiveLearningPath(@PathVariable UUID id) {
        LearningPathCommands.ArchiveLearningPathCommand cmd = LearningPathCommands.ArchiveLearningPathCommand.builder()
                .id(com.platform.teaching.domain.model.valueobjects.LearningPathId.of(id))
                .build();

        return ResponseEntity.ok(dtoAssembler.toResponse(
                teachingApplicationService.archiveLearningPath(cmd)));
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "完成学习路径")
    public ResponseEntity<LearningPathDtos.LearningPathResponse> completeLearningPath(@PathVariable UUID id) {
        LearningPathCommands.CompleteLearningPathCommand cmd = LearningPathCommands.CompleteLearningPathCommand.builder()
                .id(com.platform.teaching.domain.model.valueobjects.LearningPathId.of(id))
                .build();

        return ResponseEntity.ok(dtoAssembler.toResponse(
                teachingApplicationService.completeLearningPath(cmd)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除学习路径")
    public ResponseEntity<Void> deleteLearningPath(@PathVariable UUID id) {
        LearningPathCommands.DeleteLearningPathCommand cmd = LearningPathCommands.DeleteLearningPathCommand.builder()
                .id(com.platform.teaching.domain.model.valueobjects.LearningPathId.of(id))
                .build();

        teachingApplicationService.deleteLearningPath(cmd);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/steps")
    @Operation(summary = "添加学习步骤")
    public ResponseEntity<LearningPathDtos.StepResponse> addStep(
            @PathVariable UUID id,
            @Valid @RequestBody LearningPathDtos.AddStepRequest request) {
        
        LearningPathCommands.AddStepCommand cmd = LearningPathCommands.AddStepCommand.builder()
                .learningPathId(com.platform.teaching.domain.model.valueobjects.LearningPathId.of(id))
                .stepType(LearningPathCommands.StepType.valueOf(request.getStepType()))
                .title(request.getTitle())
                .description(request.getDescription())
                .contentId(request.getContentId())
                .contentType(request.getContentType())
                .estimatedMinutes(request.getEstimatedMinutes())
                .isRequired(request.getIsRequired())
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(
                dtoAssembler.toStepResponse(teachingApplicationService.addStep(cmd)));
    }

    @PutMapping("/{id}/steps/{stepId}")
    @Operation(summary = "更新学习步骤")
    public ResponseEntity<LearningPathDtos.StepResponse> updateStep(
            @PathVariable UUID id,
            @PathVariable UUID stepId,
            @Valid @RequestBody LearningPathDtos.UpdateStepRequest request) {
        
        LearningPathCommands.UpdateStepCommand cmd = LearningPathCommands.UpdateStepCommand.builder()
                .stepId(com.platform.teaching.domain.model.valueobjects.StepId.of(stepId))
                .title(request.getTitle())
                .description(request.getDescription())
                .estimatedMinutes(request.getEstimatedMinutes())
                .isRequired(request.getIsRequired())
                .build();

        return ResponseEntity.ok(dtoAssembler.toStepResponse(
                teachingApplicationService.updateStep(cmd)));
    }

    @DeleteMapping("/{id}/steps/{stepId}")
    @Operation(summary = "删除学习步骤")
    public ResponseEntity<Void> removeStep(@PathVariable UUID id, @PathVariable UUID stepId) {
        LearningPathCommands.RemoveStepCommand cmd = LearningPathCommands.RemoveStepCommand.builder()
                .stepId(com.platform.teaching.domain.model.valueobjects.StepId.of(stepId))
                .build();

        teachingApplicationService.removeStep(cmd);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/steps/reorder")
    @Operation(summary = "重新排序学习步骤")
    public ResponseEntity<Void> reorderSteps(
            @PathVariable UUID id,
            @Valid @RequestBody LearningPathDtos.ReorderStepsRequest request) {
        
        LearningPathCommands.ReorderStepsCommand cmd = LearningPathCommands.ReorderStepsCommand.builder()
                .learningPathId(com.platform.teaching.domain.model.valueobjects.LearningPathId.of(id))
                .stepOrders(request.getStepOrders().stream()
                        .map(o -> LearningPathCommands.ReorderStepsCommand.StepOrder.builder()
                                .stepId(com.platform.teaching.domain.model.valueobjects.StepId.of(o.getStepId()))
                                .sequence(o.getSequence())
                                .build())
                        .collect(java.util.stream.Collectors.toList()))
                .build();

        teachingApplicationService.reorderSteps(cmd);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/steps/{stepId}/start")
    @Operation(summary = "开始学习步骤")
    public ResponseEntity<Void> startStep(
            @PathVariable UUID id,
            @PathVariable UUID stepId,
            @Valid @RequestBody LearningPathDtos.StartStepRequest request) {
        
        LearningPathCommands.StartStepCommand cmd = LearningPathCommands.StartStepCommand.builder()
                .stepId(com.platform.teaching.domain.model.valueobjects.StepId.of(stepId))
                .userId(request.getUserId())
                .build();

        teachingApplicationService.startStep(cmd);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/steps/{stepId}/complete")
    @Operation(summary = "完成学习步骤")
    public ResponseEntity<Void> completeStep(
            @PathVariable UUID id,
            @PathVariable UUID stepId,
            @Valid @RequestBody LearningPathDtos.CompleteStepRequest request) {
        
        LearningPathCommands.CompleteStepCommand cmd = LearningPathCommands.CompleteStepCommand.builder()
                .stepId(com.platform.teaching.domain.model.valueobjects.StepId.of(stepId))
                .userId(request.getUserId())
                .build();

        teachingApplicationService.completeStep(cmd);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/steps/{stepId}/skip")
    @Operation(summary = "跳过学习步骤")
    public ResponseEntity<Void> skipStep(
            @PathVariable UUID id,
            @PathVariable UUID stepId,
            @Valid @RequestBody LearningPathDtos.SkipStepRequest request) {
        
        LearningPathCommands.SkipStepCommand cmd = LearningPathCommands.SkipStepCommand.builder()
                .stepId(com.platform.teaching.domain.model.valueobjects.StepId.of(stepId))
                .userId(request.getUserId())
                .build();

        teachingApplicationService.skipStep(cmd);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/steps")
    @Operation(summary = "获取学习路径的所有步骤")
    public ResponseEntity<java.util.List<LearningPathDtos.StepResponse>> getSteps(@PathVariable UUID id) {
        LearningPathQueries.GetLearningPathStepsQuery query = LearningPathQueries.GetLearningPathStepsQuery.builder()
                .learningPathId(com.platform.teaching.domain.model.valueobjects.LearningPathId.of(id))
                .build();

        return ResponseEntity.ok(teachingApplicationService.getLearningPathSteps(query).stream()
                .map(dtoAssembler::toStepResponse)
                .collect(java.util.stream.Collectors.toList()));
    }

    @GetMapping("/{id}/steps/{stepId}")
    @Operation(summary = "获取学习步骤详情")
    public ResponseEntity<LearningPathDtos.StepResponse> getStep(@PathVariable UUID id, @PathVariable UUID stepId) {
        LearningPathQueries.GetStepByIdQuery query = LearningPathQueries.GetStepByIdQuery.builder()
                .stepId(stepId)
                .build();

        return teachingApplicationService.getStepById(query)
                .map(dtoAssembler::toStepResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}