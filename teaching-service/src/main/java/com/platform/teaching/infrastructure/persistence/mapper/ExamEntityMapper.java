package com.platform.teaching.infrastructure.persistence.mapper;

import com.platform.teaching.domain.model.Exam;
import com.platform.teaching.domain.model.valueobjects.ExamId;
import com.platform.teaching.infrastructure.persistence.entity.ExamEntity;
import org.springframework.stereotype.Component;

/**
 * 考试实体映射器
 */
@Component
public class ExamEntityMapper {

    public ExamEntity toEntity(Exam domain) {
        if (domain == null) return null;

        ExamEntity entity = new ExamEntity();
        entity.setId(domain.getId());
        entity.setTenantId(domain.getTenantId());
        entity.setCourseId(domain.getCourseId());
        entity.setLearningPathId(domain.getLearningPathId());
        entity.setStepId(domain.getStepId());
        entity.setTitle(domain.getTitle());
        entity.setDescription(domain.getDescription());
        entity.setInstructions(domain.getInstructions());
        entity.setStatus(domain.getStatus().name());
        entity.setDurationMinutes(domain.getDurationMinutes());
        entity.setMaxScore(domain.getMaxScore());
        entity.setPassingScore(domain.getPassingScore());
        entity.setMaxAttempts(domain.getMaxAttempts());
        entity.setShuffleQuestions(domain.getShuffleQuestions());
        entity.setShuffleOptions(domain.getShuffleOptions());
        entity.setShowResultsImmediately(domain.getShowResultsImmediately());
        entity.setStartAt(domain.getStartAt());
        entity.setEndAt(domain.getEndAt());
        entity.setCreatedBy(domain.getCreatedBy());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());
        entity.setPublishedAt(domain.getPublishedAt());
        return entity;
    }

    public Exam toDomain(ExamEntity entity) {
        if (entity == null) return null;

        Exam domain = new Exam();
        domain.setId(entity.getId());
        domain.setTenantId(entity.getTenantId());
        domain.setCourseId(entity.getCourseId());
        domain.setLearningPathId(entity.getLearningPathId());
        domain.setStepId(entity.getStepId());
        domain.setTitle(entity.getTitle());
        domain.setDescription(entity.getDescription());
        domain.setInstructions(entity.getInstructions());
        domain.setStatus(com.platform.teaching.domain.model.valueobjects.ExamStatus.valueOf(entity.getStatus()));
        domain.setDurationMinutes(entity.getDurationMinutes());
        domain.setMaxScore(entity.getMaxScore());
        domain.setPassingScore(entity.getPassingScore());
        domain.setMaxAttempts(entity.getMaxAttempts());
        domain.setShuffleQuestions(entity.getShuffleQuestions());
        domain.setShuffleOptions(entity.getShuffleOptions());
        domain.setShowResultsImmediately(entity.getShowResultsImmediately());
        domain.setStartAt(entity.getStartAt());
        domain.setEndAt(entity.getEndAt());
        domain.setCreatedBy(entity.getCreatedBy());
        domain.setCreatedAt(entity.getCreatedAt());
        domain.setUpdatedAt(entity.getUpdatedAt());
        domain.setPublishedAt(entity.getPublishedAt());
        return domain;
    }
}