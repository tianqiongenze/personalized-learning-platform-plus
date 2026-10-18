package com.platform.course.application.query;

import com.platform.course.domain.model.Course;
import com.platform.course.domain.model.Chapter;
import com.platform.course.domain.model.KnowledgePoint;
import com.platform.course.domain.model.Resource;
import com.platform.course.domain.model.valueobjects.*;
import java.util.List;
import java.util.Set;

/**
 * 课程查询服务接口
 */
public record ResourceListDto(
    String id,
    String courseId,
    String title,
    String description,
    ResourceType type,
    String status,
    int difficulty,
    Set<String> tags,
    int version,
    java.time.Instant createdAt
) {}

/**
 * 资源详情DTO
 */
