package com.platform.teaching.interfaces.rest.assembler;

import com.platform.teaching.domain.model.LearningPath;
import com.platform.teaching.domain.model.LearningPathStep;
import com.platform.teaching.interfaces.rest.dto.LearningPathDtos;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 学习路径 DTO 组装器
 */
@Component
public class LearningPathDtoAssembler {

    public LearningPathDtos.LearningPathResponse toResponse(LearningPath learningPath) {
        if (learningPath == null) return null;

        LearningPathDtos.LearningPathResponse response = LearningPathDtos.LearningPathResponse.builder()
                .id(learningPath.getId())
                .tenantId(learningPath.getTenantId())
                .courseId(learningPath.getCourseId())
                .title(learningPath.getTitle())
                .description(learningPath.getDescription())
                .status(learningPath.getStatus().name())
                .version(learningPath.getVersion())
                .createdBy(learningPath.getCreatedBy())
                .createdAt(learningPath.getCreatedAt())
                .updatedAt(learningPath.getUpdatedAt())
                .publishedAt(learningPath.getPublishedAt())
                .build();

        if (learningPath.getSteps() != null) {
            List<LearningPathDtos.StepResponse> stepResponses = learningPath.getSteps().stream()
                    .map(this::toStepResponse)
                    .collect(Collectors.toList());
            response.setSteps(stepResponses);
        }

        return response;
    }

    public LearningPathDtos.StepResponse toStepResponse(LearningPathStep step) {
        if (step == null) return null;

        return LearningPathDtos.StepResponse.builder()
                .id(step.getId())
                .sequence(step.getSequence())
                .stepType(step.getStepType().name())
                .title(step.getTitle())
                .description(step.getDescription())
                .contentId(step.getContentId())
                .contentType(step.getContentType())
                .estimatedMinutes(step.getEstimatedMinutes())
                .isRequired(step.getIsRequired())
                .status(step.getStatus().name())
                .startedAt(step.getStartedAt())
                .completedAt(step.getCompletedAt())
                .build();
    }

    public LearningPathDtos.LearningPathPageResponse toPageResponse(org.springframework.data.domain.Page<LearningPath> page) {
        if (page == null) return null;

        List<LearningPathDtos.LearningPathResponse> content = page.getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return LearningPathDtos.LearningPathPageResponse.builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }
}