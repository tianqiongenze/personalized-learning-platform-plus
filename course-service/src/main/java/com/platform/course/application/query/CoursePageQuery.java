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
public record CoursePageQuery(
    String tenantId,
    int page,
    int size,
    String status,
    String keyword,
    String subject,
    String gradeLevel,
    String sortBy,
    String sortOrder
) {
    public CoursePageQuery {
        page = Math.max(0, page);
        size = Math.min(Math.max(1, size), 100);
        sortBy = sortBy != null ? sortBy : "createdAt";
        sortOrder = sortOrder != null ? sortOrder : "desc";
    }
}
