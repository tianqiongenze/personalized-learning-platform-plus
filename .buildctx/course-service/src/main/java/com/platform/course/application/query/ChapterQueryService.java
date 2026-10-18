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
 * 知识点分页查询参数
 */

public interface ChapterQueryService {
    ChapterDetailDto findById(ChapterId id);
    ChapterDetailDto findByCode(CourseId courseId, String code);
    PageResult<ChapterListDto> findPage(ChapterPageQuery query);
    List<ChapterSimpleDto> findByCourseId(CourseId courseId);
    List<ChapterSimpleDto> findByParentId(ChapterId parentId);
}
