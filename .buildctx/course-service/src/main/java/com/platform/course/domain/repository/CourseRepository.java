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
 * 知识点仓储接口
 */

public interface CourseRepository {
    Course save(Course course);
    Optional<Course> findById(CourseId id);
    Optional<Course> findByCode(String tenantId, String code);
    @org.springframework.data.jpa.repository.Query("SELECT CASE WHEN COUNT(c) > 0 THEN TRUE ELSE FALSE END FROM CourseEntity c WHERE c.code = :code AND c.id != :id")
    boolean existsByCodeAndIdNot(String code, CourseId id);

    boolean existsByCode(String tenantId, String code);
    List<Course> findByTenantId(String tenantId, int page, int size, String status, String keyword);
    long countByTenantId(String tenantId, String status, String keyword);
    List<Course> findByCreatorId(String creatorId);
    List<Course> findByIds(List<CourseId> ids);
    void deleteById(CourseId id);
}
