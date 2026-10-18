package com.platform.course.domain.event;

import com.platform.course.domain.model.valueobjects.*;
import java.time.Instant;
import java.util.UUID;

/**
 * 领域事件基类
 */

/**
 * 课程更新事件
 */

public final class CourseDeletedEvent extends DomainEvent {
    private final CourseId courseId;
    private final String tenantId;
    private final String deletedBy;
    private final String reason;

    public CourseDeletedEvent(CourseId courseId, String tenantId, String deletedBy, String reason) {
        super("CourseDeleted");
        this.courseId = courseId;
        this.tenantId = tenantId;
        this.deletedBy = deletedBy;
        this.reason = reason;
    }

    public CourseId getCourseId() { return courseId; }
    public String getTenantId() { return tenantId; }
    public String getDeletedBy() { return deletedBy; }
    public String getReason() { return reason; }
}
