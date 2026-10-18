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
 * 章节仓储接口
 */

public interface KnowledgePointRepository {
    KnowledgePoint save(KnowledgePoint kp);
    Optional<KnowledgePoint> findById(KnowledgePointId id);
    Optional<KnowledgePoint> findByCode(CourseId courseId, String code);
    boolean existsByCodeAndIdNot(String code, KnowledgePointId id);

    @org.springframework.data.jpa.repository.Query("SELECT CASE WHEN COUNT(k) > 0 THEN TRUE ELSE FALSE END FROM KnowledgePointEntity k WHERE k.code = :code AND k.courseId = :courseId")
    boolean existsByCodeAndCourseId(String code, CourseId courseId);

    @org.springframework.data.jpa.repository.Query("SELECT CASE WHEN COUNT(k) > 0 THEN TRUE ELSE FALSE END FROM KnowledgePointEntity k WHERE k.code = :code AND k.courseId = :courseId AND k.id != :id")
    boolean existsByCodeAndCourseIdAndIdNot(String code, CourseId courseId, KnowledgePointId id);

    boolean existsByCode(CourseId courseId, String code);
    List<KnowledgePoint> findByCourseId(CourseId courseId, int page, int size, String status, String keyword);
    long countByCourseId(CourseId courseId, String status, String keyword);
    List<KnowledgePoint> findByChapterId(ChapterId chapterId);
    List<KnowledgePoint> findByIds(List<KnowledgePointId> ids);
    void deleteById(KnowledgePointId id);
}
