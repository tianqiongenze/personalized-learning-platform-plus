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
 * 资源分页查询参数
 */

public interface KnowledgePointQueryService {
    KnowledgePointDetailDto findById(KnowledgePointId id);
    KnowledgePointDetailDto findByCode(CourseId courseId, String code);
    PageResult<KnowledgePointListDto> findPage(KnowledgePointPageQuery query);
    List<KnowledgePointSimpleDto> findByCourseId(CourseId courseId);
    List<KnowledgePointSimpleDto> findByChapterId(ChapterId chapterId);
}
