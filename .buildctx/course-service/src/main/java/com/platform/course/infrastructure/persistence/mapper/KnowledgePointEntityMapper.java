package com.platform.course.infrastructure.persistence.mapper;

import com.platform.course.domain.model.KnowledgePoint;
import com.platform.course.domain.model.valueobjects.KnowledgePointId;
import com.platform.course.infrastructure.persistence.entity.KnowledgePointEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 知识点实体映射器(手工实现)
 */
@Component
public class KnowledgePointEntityMapper {

    public KnowledgePointEntity toEntity(KnowledgePoint kp) {
        KnowledgePointEntity e = new KnowledgePointEntity();
        e.setKpId(kp.getId());
        e.setCourseIdValue(kp.getCourseId());
        e.setTenantIdValue(kp.getTenantId());
        e.setCode(kp.getCode());
        e.setName(kp.getName());
        e.setDescription(kp.getDescription());
        e.setChapterIdValue(kp.getChapterId());
        e.setDifficulty(kp.getDifficulty());
        e.setPrerequisiteIdSet(kp.getPrerequisites());
        e.setRelatedPointIdSet(kp.getRelatedPoints());
        e.setMetadataJson(toJson(kp.getMetadata()));
        return e;
    }

    public KnowledgePoint toDomain(KnowledgePointEntity e) {
        return KnowledgePoint.reconstruct(
            e.getKpId(), e.getCourseIdValue(), e.getTenantIdValue(),
            e.getCode(), e.getName(), e.getDescription(),
            e.getChapterIdValue(),
            e.getDifficulty(), e.getPrerequisiteIdSet(), e.getRelatedPointIdSet(),
            e.getMetadataJson() == null || e.getMetadataJson().isEmpty() ? Map.of() : parseMetadata(e.getMetadataJson()),
            e.getCreatedAt(), e.getUpdatedAt(), e.getDeletedAt(), e.getDeletedBy(), e.getDeleteReason());
    }

    private static String toJson(Map<String, Object> metadata) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (Exception ex) {
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseMetadata(String json) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }
}
