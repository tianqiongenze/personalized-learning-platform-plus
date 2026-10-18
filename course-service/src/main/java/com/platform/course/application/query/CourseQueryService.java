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
 * 章节分页查询参数
 */

public interface CourseQueryService {
    CourseDetailDto findById(CourseId id);
    CourseDetailDto findByCode(String tenantId, String code);
    PageResult<CourseListDto> findPage(CoursePageQuery query);
    List<CourseSimpleDto> findByCreatorId(String creatorId);
    CourseStatisticsDto statistics(String tenantId);
}
