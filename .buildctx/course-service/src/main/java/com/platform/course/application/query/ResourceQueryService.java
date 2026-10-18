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
 * 课程列表DTO
 */

public interface ResourceQueryService {
    ResourceDetailDto findById(ResourceId id);
    ResourceDetailDto findByCode(CourseId courseId, String code);
    PageResult<ResourceListDto> findPage(ResourcePageQuery query);
    List<ResourceSimpleDto> findByCourseId(CourseId courseId);
    List<ResourceSimpleDto> findByChapterId(ChapterId chapterId);
    List<ResourceSimpleDto> findByKpId(KnowledgePointId kpId);
    List<ResourceSimpleDto> findByType(ResourceType type);
}
