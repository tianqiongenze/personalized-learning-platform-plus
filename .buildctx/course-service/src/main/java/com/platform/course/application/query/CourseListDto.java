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
public record CourseListDto(
    String id,
    String tenantId,
    String code,
    String name,
    String subject,
    String gradeLevel,
    String semester,
    String status,
    String coverUrl,
    String creatorId,
    java.time.Instant createdAt
) {}

/**
 * 课程详情DTO
 */
