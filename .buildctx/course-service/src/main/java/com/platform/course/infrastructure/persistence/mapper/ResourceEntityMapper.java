package com.platform.course.infrastructure.persistence.mapper;

import com.platform.course.domain.model.Resource;
import com.platform.course.domain.model.valueobjects.ChapterId;
import com.platform.course.domain.model.valueobjects.ResourceId;
import com.platform.course.domain.model.valueobjects.ResourceStatus;
import com.platform.course.domain.model.valueobjects.ResourceType;
import com.platform.course.infrastructure.persistence.entity.ResourceEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 资源实体映射器(手工实现)
 */
@Component
public class ResourceEntityMapper {

    public ResourceEntity toEntity(Resource r) {
        ResourceEntity e = new ResourceEntity();
        e.setResourceId(r.getId());
        e.setCourseIdValue(r.getCourseId());
        e.setTenantIdValue(r.getTenantId());
        e.setChapterIdValue(r.getChapterId());
        e.setType(r.getType());
        e.setTitle(r.getTitle());
        e.setDescription(r.getDescription());
        e.setContentUrl(r.getContentUrl());
        e.setCoverUrl(r.getCoverUrl());
        e.setMetadataJson(toJson(r.getMetadata()));
        e.setDifficulty(r.getDifficulty());
        e.setTagSet(r.getTags());
        e.setStatus(r.getStatus());
        e.setVersion(r.getVersion());
        e.setCreatorId(r.getCreatorId());
        return e;
    }

    public Resource toDomain(ResourceEntity e) {
        return Resource.reconstruct(
            e.getResourceId(), e.getCourseIdValue(), e.getTenantIdValue(),
            e.getChapterIdValue(),
            e.getType(), e.getTitle(), e.getDescription(), e.getContentUrl(),
            e.getCoverUrl(), e.getMetadataJson() == null || e.getMetadataJson().isEmpty() ? Map.of() : parseMetadata(e.getMetadataJson()),
            e.getDifficulty(), e.getTagSet() == null ? Set.of() : e.getTagSet(),
            e.getStatus() == null ? ResourceStatus.DRAFT : e.getStatus(), e.getVersion(), e.getCreatorId(),
            e.getCreatedAt(), e.getUpdatedAt(), e.getDeletedAt(), e.getDeletedBy(), e.getDeleteReason());
    }

    private static ResourceStatus parseStatus(String status) {
        try {
            return status == null ? ResourceStatus.DRAFT : ResourceStatus.valueOf(status);
        } catch (IllegalArgumentException ex) {
            return ResourceStatus.DRAFT;
        }
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
