package com.platform.course.application.query;

import com.platform.course.domain.model.Course;
import com.platform.course.domain.model.Chapter;
import com.platform.course.domain.model.KnowledgePoint;
import com.platform.course.domain.model.Resource;
import com.platform.course.domain.model.valueobjects.*;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 课程查询服务接口
 */
public record ResourceDetailDto(
    String id,
    String courseId,
    String tenantId,
    ChapterId chapterId,
    Set<KnowledgePointId> kpIds,
    ResourceType type,
    String title,
    String description,
    String contentUrl,
    String coverUrl,
    Map<String, Object> metadata,
    int difficulty,
    Set<String> tags,
    String status,
    int version,
    String creatorId,
    java.time.Instant createdAt,
    java.time.Instant updatedAt,
    java.time.Instant deletedAt,
    String deletedBy,
    String deleteReason
) {}

/**
 * 资源简要DTO
 */
