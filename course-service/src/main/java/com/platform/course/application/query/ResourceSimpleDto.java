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

/**
 * 课程分页查询参数
 */

public record ResourceSimpleDto(
    String id,
    String title,
    ResourceType type
) {}

/**
 * 分页结果
 */
