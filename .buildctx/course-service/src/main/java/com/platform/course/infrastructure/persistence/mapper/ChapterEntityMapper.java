package com.platform.course.infrastructure.persistence.mapper;

import com.platform.course.domain.model.Chapter;
import com.platform.course.domain.model.valueobjects.ChapterId;
import com.platform.course.domain.model.valueobjects.ChapterStatus;
import com.platform.course.infrastructure.persistence.entity.ChapterEntity;
import org.springframework.stereotype.Component;

/**
 * 章节实体映射器(手工实现: 领域模型构造器受限, MapStruct 不适用)
 */
@Component
public class ChapterEntityMapper {

    public ChapterEntity toEntity(Chapter c) {
        ChapterEntity e = new ChapterEntity();
        e.setChapterId(c.getId());
        e.setCourseIdValue(c.getCourseId());
        e.setTenantIdValue(c.getTenantId());
        e.setCode(c.getCode());
        e.setName(c.getName());
        e.setDescription(c.getDescription());
        e.setParentId(c.getParentId() != null ? c.getParentId().getValue() : null);
        e.setOrder(c.getOrder());
        e.setDifficulty(c.getDifficulty());
        e.setEstimatedHours(c.getEstimatedHours());
        e.setStatus(c.getStatus());
        e.setKnowledgePointIdSet(c.getKnowledgePointIds());
        return e;
    }

    public Chapter toDomain(ChapterEntity e) {
        return Chapter.reconstruct(
            e.getChapterId(), e.getCourseIdValue(), e.getTenantIdValue(),
            e.getCode(), e.getName(), e.getDescription(),
            (e.getParentId() == null ? null : ChapterId.of(e.getParentId())),
            e.getOrder(), e.getDifficulty(), e.getEstimatedHours(),
            e.getKnowledgePointIdSet(), e.getStatus() == null ? ChapterStatus.DRAFT : e.getStatus(),
            e.getCreatedAt(), e.getUpdatedAt(), e.getDeletedAt(), e.getDeletedBy(), e.getDeleteReason());
    }

    private static ChapterStatus parseStatus(String status) {
        try {
            return status == null ? ChapterStatus.DRAFT : ChapterStatus.valueOf(status);
        } catch (IllegalArgumentException ex) {
            return ChapterStatus.DRAFT;
        }
    }
}
