package com.platform.teaching.infrastructure.persistence.mapper;

import com.platform.teaching.domain.model.Discussion;
import com.platform.teaching.domain.model.valueobjects.DiscussionId;
import com.platform.teaching.infrastructure.persistence.entity.DiscussionEntity;
import org.springframework.stereotype.Component;

/**
 * 讨论实体映射器
 */
@Component
public class DiscussionEntityMapper {

    public DiscussionEntity toEntity(Discussion domain) {
        if (domain == null) return null;

        DiscussionEntity entity = new DiscussionEntity();
        entity.setId(domain.getId());
        entity.setTenantId(domain.getTenantId());
        entity.setCourseId(domain.getCourseId());
        entity.setLearningPathId(domain.getLearningPathId());
        entity.setStepId(domain.getStepId());
        entity.setTitle(domain.getTitle());
        entity.setContent(domain.getContent());
        entity.setAuthorId(domain.getAuthorId());
        entity.setAuthorName(domain.getAuthorName());
        entity.setStatus(domain.getStatus().name());
        entity.setIsPinned(domain.getIsPinned());
        entity.setIsLocked(domain.getIsLocked());
        entity.setViewCount(domain.getViewCount());
        entity.setReplyCount(domain.getReplyCount());
        entity.setLastReplyAt(domain.getLastReplyAt());
        entity.setLastReplyBy(domain.getLastReplyBy());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());
        return entity;
    }

    public Discussion toDomain(DiscussionEntity entity) {
        if (entity == null) return null;

        Discussion domain = new Discussion();
        domain.setId(entity.getId());
        domain.setTenantId(entity.getTenantId());
        domain.setCourseId(entity.getCourseId());
        domain.setLearningPathId(entity.getLearningPathId());
        domain.setStepId(entity.getStepId());
        domain.setTitle(entity.getTitle());
        domain.setContent(entity.getContent());
        domain.setAuthorId(entity.getAuthorId());
        domain.setAuthorName(entity.getAuthorName());
        domain.setStatus(com.platform.teaching.domain.model.valueobjects.DiscussionStatus.valueOf(entity.getStatus()));
        domain.setIsPinned(entity.getIsPinned());
        domain.setIsLocked(entity.getIsLocked());
        domain.setViewCount(entity.getViewCount());
        domain.setReplyCount(entity.getReplyCount());
        domain.setLastReplyAt(entity.getLastReplyAt());
        domain.setLastReplyBy(entity.getLastReplyBy());
        domain.setCreatedAt(entity.getCreatedAt());
        domain.setUpdatedAt(entity.getUpdatedAt());
        return domain;
    }
}