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
public record CourseStatisticsDto(
    long total,
    long draft,
    long published,
    long archived,
    long deleted
) {}
