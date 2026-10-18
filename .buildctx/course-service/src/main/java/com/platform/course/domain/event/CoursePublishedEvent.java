package com.platform.course.domain.event;

import com.platform.course.domain.model.valueobjects.*;
import java.time.Instant;
import java.util.UUID;

/**
 * 领域事件基类
 */

/**
 * 章节创建事件
 */

public final class CoursePublishedEvent extends DomainEvent {
    private final CourseId courseId;
    private final String tenantId;
    private final String publishedBy;

    public CoursePublishedEvent(CourseId courseId, String tenantId, String publishedBy) {
        super("CoursePublished");
        this.courseId = courseId;
        this.tenantId = tenantId;
        this.publishedBy = publishedBy;
    }

    public CourseId getCourseId() { return courseId; }
    public String getTenantId() { return tenantId; }
    public String getPublishedBy() { return publishedBy; }
}
