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
 * 资源查询服务接口
 */

public record KnowledgePointSimpleDto(
    String id,
    String code,
    String name
) {}

/**
 * 资源列表DTO
 */
