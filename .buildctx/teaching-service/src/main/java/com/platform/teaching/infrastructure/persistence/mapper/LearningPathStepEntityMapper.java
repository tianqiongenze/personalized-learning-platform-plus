package com.platform.teaching.infrastructure.persistence.mapper;

import com.platform.teaching.domain.model.LearningPathStep;
import com.platform.teaching.domain.model.StepStatus;
import com.platform.teaching.domain.model.StepType;
import com.platform.teaching.infrastructure.persistence.entity.LearningPathStepEntity;
import org.springframework.stereotype.Component;

/**
 * 学习路径步骤实体映射器(手工实现)
 */
@Component
public class LearningPathStepEntityMapper {

    public LearningPathStepEntity toStepEntity(LearningPathStep step) {
        LearningPathStepEntity e = new LearningPathStepEntity();
        e.setId(step.getId());
        e.setSequence(step.getSequence());
        e.setStepType(step.getStepType() != null ? step.getStepType().name() : null);
        e.setTitle(step.getTitle());
        e.setDescription(step.getDescription());
        e.setContentId(step.getContentId());
        e.setContentType(step.getContentType());
        e.setEstimatedMinutes(step.getEstimatedMinutes());
        e.setIsRequired(step.getIsRequired());
        e.setPrerequisiteStepId(step.getPrerequisiteStepId());
        e.setStatus(step.getStatus() != null ? step.getStatus().name() : "NOT_STARTED");
        e.setStartedAt(step.getStartedAt());
        e.setCompletedAt(step.getCompletedAt());
        e.setCreatedAt(step.getCreatedAt());
        e.setUpdatedAt(step.getUpdatedAt());
        return e;
    }

    public LearningPathStep toStepDomain(LearningPathStepEntity e) {
        LearningPathStep step = new LearningPathStep();
        step.setId(e.getId());
        step.setSequence(e.getSequence());
        step.setStepType(e.getStepType() != null ? StepType.valueOf(e.getStepType()) : null);
        step.setTitle(e.getTitle());
        step.setDescription(e.getDescription());
        step.setContentId(e.getContentId());
        step.setContentType(e.getContentType());
        step.setEstimatedMinutes(e.getEstimatedMinutes());
        step.setIsRequired(e.getIsRequired());
        step.setPrerequisiteStepId(e.getPrerequisiteStepId());
        step.setStatus(e.getStatus() != null ? StepStatus.valueOf(e.getStatus()) : StepStatus.NOT_STARTED);
        step.setStartedAt(e.getStartedAt());
        step.setCompletedAt(e.getCompletedAt());
        step.setCreatedAt(e.getCreatedAt());
        step.setUpdatedAt(e.getUpdatedAt());
        return step;
    }
}
