package com.platform.course.domain.event;

import com.platform.course.domain.model.valueobjects.*;
import java.time.Instant;
import java.util.UUID;

/**
 * 领域事件基类
 */

/**
 * 课程删除事件
 */

public final class CourseCreatedEvent extends DomainEvent {
    private final CourseId courseId;
    private final String tenantId;
    private final String code;
    private final String name;
    private final String createdBy;

    public CourseCreatedEvent(CourseId courseId, String tenantId, String code, String name, String createdBy) {
        super("CourseCreated");
        this.courseId = courseId;
        this.tenantId = tenantId;
        this.code = code;
        this.name = name;
        this.createdBy = createdBy;
    }

    public CourseId getCourseId() { return courseId; }
    public String getTenantId() { return tenantId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getCreatedBy() { return createdBy; }
}
