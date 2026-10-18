package com.platform.teaching.infrastructure.persistence.mapper;

import com.platform.teaching.domain.model.LiveSession;
import com.platform.teaching.domain.model.valueobjects.LiveSessionId;
import com.platform.teaching.infrastructure.persistence.entity.LiveSessionEntity;
import org.springframework.stereotype.Component;

/**
 * 直播会话实体映射器
 */
@Component
public class LiveSessionEntityMapper {

    public LiveSessionEntity toEntity(LiveSession domain) {
        if (domain == null) return null;

        LiveSessionEntity entity = new LiveSessionEntity();
        entity.setId(domain.getId());
        entity.setTenantId(domain.getTenantId());
        entity.setCourseId(domain.getCourseId());
        entity.setLearningPathId(domain.getLearningPathId());
        entity.setStepId(domain.getStepId());
        entity.setTitle(domain.getTitle());
        entity.setDescription(domain.getDescription());
        entity.setInstructorId(domain.getInstructorId());
        entity.setInstructorName(domain.getInstructorName());
        entity.setStatus(domain.getStatus().name());
        entity.setStreamKey(domain.getStreamKey());
        entity.setPlaybackUrl(domain.getPlaybackUrl());
        entity.setMaxParticipants(domain.getMaxParticipants());
        entity.setCurrentParticipants(domain.getCurrentParticipants());
        entity.setScheduledStartAt(domain.getScheduledStartAt());
        entity.setScheduledEndAt(domain.getScheduledEndAt());
        entity.setActualStartAt(domain.getActualStartAt());
        entity.setActualEndAt(domain.getActualEndAt());
        entity.setRecordingEnabled(domain.getRecordingEnabled());
        entity.setChatEnabled(domain.getChatEnabled());
        entity.setCreatedBy(domain.getCreatedBy());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());
        return entity;
    }

    public LiveSession toDomain(LiveSessionEntity entity) {
        if (entity == null) return null;

        LiveSession domain = new LiveSession();
        domain.setId(entity.getId());
        domain.setTenantId(entity.getTenantId());
        domain.setCourseId(entity.getCourseId());
        domain.setLearningPathId(entity.getLearningPathId());
        domain.setStepId(entity.getStepId());
        domain.setTitle(entity.getTitle());
        domain.setDescription(entity.getDescription());
        domain.setInstructorId(entity.getInstructorId());
        domain.setInstructorName(entity.getInstructorName());
        domain.setStatus(com.platform.teaching.domain.model.valueobjects.LiveSessionStatus.valueOf(entity.getStatus()));
        domain.setStreamKey(entity.getStreamKey());
        domain.setPlaybackUrl(entity.getPlaybackUrl());
        domain.setMaxParticipants(entity.getMaxParticipants());
        domain.setCurrentParticipants(entity.getCurrentParticipants());
        domain.setScheduledStartAt(entity.getScheduledStartAt());
        domain.setScheduledEndAt(entity.getScheduledEndAt());
        domain.setActualStartAt(entity.getActualStartAt());
        domain.setActualEndAt(entity.getActualEndAt());
        domain.setRecordingEnabled(entity.getRecordingEnabled());
        domain.setChatEnabled(entity.getChatEnabled());
        domain.setCreatedBy(entity.getCreatedBy());
        domain.setCreatedAt(entity.getCreatedAt());
        domain.setUpdatedAt(entity.getUpdatedAt());
        return domain;
    }
}