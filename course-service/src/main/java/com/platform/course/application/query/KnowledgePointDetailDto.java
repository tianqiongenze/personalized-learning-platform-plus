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
public record KnowledgePointDetailDto(
    String id,
    String courseId,
    String tenantId,
    String code,
    String name,
    String description,
    ChapterId chapterId,
    int difficulty,
    Set<KnowledgePointId> prerequisites,
    Set<KnowledgePointId> relatedPoints,
    Map<String, Object> metadata,
    java.time.Instant createdAt,
    java.time.Instant updatedAt,
    java.time.Instant deletedAt,
    String deletedBy,
    String deleteReason
) {}

/**
 * 知识点简要DTO
 */
