package com.platform.teaching.infrastructure.persistence.mapper;

import com.platform.teaching.domain.model.Assignment;
import com.platform.teaching.domain.model.valueobjects.AssignmentId;
import com.platform.teaching.infrastructure.persistence.entity.AssignmentEntity;
import org.springframework.stereotype.Component;

/**
 * 作业实体映射器
 */
@Component
public class AssignmentEntityMapper {

    public AssignmentEntity toEntity(Assignment domain) {
        if (domain == null) return null;

        AssignmentEntity entity = new AssignmentEntity();
        entity.setId(domain.getId());
        entity.setTenantId(domain.getTenantId());
        entity.setCourseId(domain.getCourseId());
        entity.setLearningPathId(domain.getLearningPathId());
        entity.setStepId(domain.getStepId());
        entity.setTitle(domain.getTitle());
        entity.setDescription(domain.getDescription());
        entity.setInstructions(domain.getInstructions());
        entity.setStatus(domain.getStatus().name());
        entity.setMaxScore(domain.getMaxScore());
        entity.setPassingScore(domain.getPassingScore());
        entity.setAllowLateSubmission(domain.getAllowLateSubmission());
        entity.setLatePenaltyPercent(domain.getLatePenaltyPercent());
        entity.setMaxAttempts(domain.getMaxAttempts());
        entity.setStartAt(domain.getStartAt());
        entity.setDueAt(domain.getDueAt());
        entity.setCreatedBy(domain.getCreatedBy());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());
        entity.setPublishedAt(domain.getPublishedAt());
        return entity;
    }

    public Assignment toDomain(AssignmentEntity entity) {
        if (entity == null) return null;

        Assignment domain = new Assignment();
        domain.setId(entity.getId());
        domain.setTenantId(entity.getTenantId());
        domain.setCourseId(entity.getCourseId());
        domain.setLearningPathId(entity.getLearningPathId());
        domain.setStepId(entity.getStepId());
        domain.setTitle(entity.getTitle());
        domain.setDescription(entity.getDescription());
        domain.setInstructions(entity.getInstructions());
        domain.setStatus(com.platform.teaching.domain.model.valueobjects.AssignmentStatus.valueOf(entity.getStatus()));
        domain.setMaxScore(entity.getMaxScore());
        domain.setPassingScore(entity.getPassingScore());
        domain.setAllowLateSubmission(entity.getAllowLateSubmission());
        domain.setLatePenaltyPercent(entity.getLatePenaltyPercent());
        domain.setMaxAttempts(entity.getMaxAttempts());
        domain.setStartAt(entity.getStartAt());
        domain.setDueAt(entity.getDueAt());
        domain.setCreatedBy(entity.getCreatedBy());
        domain.setCreatedAt(entity.getCreatedAt());
        domain.setUpdatedAt(entity.getUpdatedAt());
        domain.setPublishedAt(entity.getPublishedAt());
        return domain;
    }
}