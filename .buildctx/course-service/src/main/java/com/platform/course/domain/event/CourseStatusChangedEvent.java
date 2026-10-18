package com.platform.course.domain.event;

import com.platform.course.domain.model.valueobjects.*;
import java.time.Instant;
import java.util.UUID;

/**
 * 领域事件基类
 */

/**
 * 课程归档事件
 */

public final class CourseStatusChangedEvent extends DomainEvent {
    private final CourseId courseId;
    private final String tenantId;
    private final String oldStatus;
    private final String newStatus;
    private final String changedBy;

    public CourseStatusChangedEvent(CourseId courseId, String tenantId, String oldStatus, String newStatus, String changedBy) {
        super("CourseStatusChanged");
        this.courseId = courseId;
        this.tenantId = tenantId;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.changedBy = changedBy;
    }

    public CourseId getCourseId() { return courseId; }
    public String getTenantId() { return tenantId; }
    public String getOldStatus() { return oldStatus; }
    public String getNewStatus() { return newStatus; }
    public String getChangedBy() { return changedBy; }
}
