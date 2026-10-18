package com.platform.teaching.infrastructure.persistence.mapper;

import com.platform.teaching.domain.model.LearningPath;
import com.platform.teaching.domain.model.LearningPathStep;
import com.platform.teaching.domain.model.valueobjects.LearningPathId;
import com.platform.teaching.domain.model.valueobjects.StepId;
import com.platform.teaching.infrastructure.persistence.entity.LearningPathEntity;
import com.platform.teaching.infrastructure.persistence.entity.LearningPathStepEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 学习路径实体映射器
 */
@Component
public class LearningPathEntityMapper {

    public LearningPathEntity toEntity(LearningPath domain) {
        if (domain == null) return null;

        LearningPathEntity entity = new LearningPathEntity();
        entity.setId(domain.getId());
        entity.setTenantId(domain.getTenantId());
        entity.setCourseId(domain.getCourseId());
        entity.setTitle(domain.getTitle());
        entity.setDescription(domain.getDescription());
        entity.setStatus(domain.getStatus().name());
        entity.setVersion(domain.getVersion());
        entity.setCreatedBy(domain.getCreatedBy());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());
        entity.setPublishedAt(domain.getPublishedAt());

        if (domain.getSteps() != null) {
            List<LearningPathStepEntity> stepEntities = domain.getSteps().stream()
                    .map(this::toStepEntity)
                    .collect(Collectors.toList());
            entity.setSteps(stepEntities);
            stepEntities.forEach(step -> step.setLearningPath(entity));
        }

        return entity;
    }

    public LearningPath toDomain(LearningPathEntity entity) {
        if (entity == null) return null;

        LearningPath domain = new LearningPath();
        domain.setId(entity.getId());
        domain.setTenantId(entity.getTenantId());
        domain.setCourseId(entity.getCourseId());
        domain.setTitle(entity.getTitle());
        domain.setDescription(entity.getDescription());
        domain.setStatus(com.platform.teaching.domain.model.LearningPathStatus.valueOf(entity.getStatus()));
        domain.setVersion(entity.getVersion());
        domain.setCreatedBy(entity.getCreatedBy());
        domain.setCreatedAt(entity.getCreatedAt());
        domain.setUpdatedAt(entity.getUpdatedAt());
        domain.setPublishedAt(entity.getPublishedAt());

        if (entity.getSteps() != null) {
            List<LearningPathStep> steps = entity.getSteps().stream()
                    .map(this::toStepDomain)
                    .collect(Collectors.toList());
            domain.setSteps(steps);
        }

        return domain;
    }

    private LearningPathStepEntity toStepEntity(LearningPathStep step) {
        LearningPathStepEntity entity = new LearningPathStepEntity();
        entity.setId(step.getId());
        entity.setSequence(step.getSequence());
        entity.setStepType(step.getStepType().name());
        entity.setTitle(step.getTitle());
        entity.setDescription(step.getDescription());
        entity.setContentId(step.getContentId());
        entity.setContentType(step.getContentType());
        entity.setEstimatedMinutes(step.getEstimatedMinutes());
        entity.setIsRequired(step.getIsRequired());
        entity.setPrerequisiteStepId(step.getPrerequisiteStepId());
        entity.setStatus(step.getStatus().name());
        entity.setStartedAt(step.getStartedAt());
        entity.setCompletedAt(step.getCompletedAt());
        entity.setCreatedAt(step.getCreatedAt());
        entity.setUpdatedAt(step.getUpdatedAt());
        return entity;
    }

    private LearningPathStep toStepDomain(LearningPathStepEntity entity) {
        LearningPathStep step = new LearningPathStep();
        step.setId(entity.getId());
        step.setSequence(entity.getSequence());
        step.setStepType(com.platform.teaching.domain.model.StepType.valueOf(entity.getStepType()));
        step.setTitle(entity.getTitle());
        step.setDescription(entity.getDescription());
        step.setContentId(entity.getContentId());
        step.setContentType(entity.getContentType());
        step.setEstimatedMinutes(entity.getEstimatedMinutes());
        step.setIsRequired(entity.getIsRequired());
        step.setPrerequisiteStepId(entity.getPrerequisiteStepId());
        step.setStatus(com.platform.teaching.domain.model.StepStatus.valueOf(entity.getStatus()));
        step.setStartedAt(entity.getStartedAt());
        step.setCompletedAt(entity.getCompletedAt());
        step.setCreatedAt(entity.getCreatedAt());
        step.setUpdatedAt(entity.getUpdatedAt());
        return step;
    }
}