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
public record ChapterListDto(
    String id,
    String courseId,
    String code,
    String name,
    String description,
    ChapterId parentId,
    int order,
    int difficulty,
    int estimatedHours,
    String status,
    java.time.Instant createdAt
) {}

/**
 * 章节详情DTO
 */
