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
public interface ChapterRepository {
    Chapter save(Chapter chapter);
    Optional<Chapter> findById(ChapterId id);
    Optional<Chapter> findByCode(CourseId courseId, String code);
    boolean existsByCodeAndIdNot(String code, ChapterId id);

    @org.springframework.data.jpa.repository.Query("SELECT CASE WHEN COUNT(c) > 0 THEN TRUE ELSE FALSE END FROM ChapterEntity c WHERE c.code = :code AND c.courseId = :courseId")
    boolean existsByCodeAndCourseId(String code, CourseId courseId);

    @org.springframework.data.jpa.repository.Query("SELECT CASE WHEN COUNT(c) > 0 THEN TRUE ELSE FALSE END FROM ChapterEntity c WHERE c.code = :code AND c.courseId = :courseId AND c.id != :id")
    boolean existsByCodeAndCourseIdAndIdNot(String code, CourseId courseId, ChapterId id);

    boolean existsByCode(CourseId courseId, String code);
    List<Chapter> findByCourseId(CourseId courseId, int page, int size, String status, String keyword);
    long countByCourseId(CourseId courseId, String status, String keyword);
    List<Chapter> findByParentId(ChapterId parentId);
    List<Chapter> findByIds(List<ChapterId> ids);
    void deleteById(ChapterId id);
}
