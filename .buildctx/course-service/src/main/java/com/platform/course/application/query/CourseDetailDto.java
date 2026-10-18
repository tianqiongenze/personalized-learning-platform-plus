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
public record CourseDetailDto(
    String id,
    String tenantId,
    String code,
    String name,
    String description,
    String subject,
    String gradeLevel,
    String semester,
    String status,
    String coverUrl,
    Map<String, Object> settings,
    String creatorId,
    java.time.Instant createdAt,
    java.time.Instant updatedAt,
    java.time.Instant publishedAt,
    java.time.Instant archivedAt,
    java.time.Instant deletedAt,
    String deletedBy,
    String deleteReason
) {}

/**
 * 课程简要DTO
 */
