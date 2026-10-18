package com.platform.course.domain.repository;

import com.platform.course.domain.model.Course;
import com.platform.course.domain.model.Chapter;
import com.platform.course.domain.model.KnowledgePoint;
import com.platform.course.domain.model.Resource;
import com.platform.course.domain.model.valueobjects.*;
import java.util.List;
import java.util.Optional;

/**
 * 课程仓储接口
 */

/**
 * 资源仓储接口
 */

public interface ResourceRepository {
    Resource save(Resource resource);
    Optional<Resource> findById(ResourceId id);
    Optional<Resource> findByCode(CourseId courseId, String code);
    boolean existsByCodeAndIdNot(String code, ResourceId id);

    boolean existsByCodeAndCourseId(String code, CourseId courseId);

    boolean existsByCode(CourseId courseId, String code);
    List<Resource> findByCourseId(CourseId courseId, int page, int size, String status, String keyword, String type);
    long countByCourseId(CourseId courseId, String status, String keyword, String type);
    List<Resource> findByChapterId(ChapterId chapterId);
    List<Resource> findByKpId(KnowledgePointId kpId);
    List<Resource> findByType(ResourceType type);
    List<Resource> findByIds(List<ResourceId> ids);
    void deleteById(ResourceId id);
}
