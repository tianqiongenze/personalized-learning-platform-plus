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
public record KnowledgePointListDto(
    String id,
    String courseId,
    String code,
    String name,
    String description,
    ChapterId chapterId,
    int difficulty,
    String status,
    java.time.Instant createdAt
) {}

/**
 * 知识点详情DTO
 */
