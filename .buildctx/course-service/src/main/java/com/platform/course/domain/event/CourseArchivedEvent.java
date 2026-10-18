package com.platform.course.domain.event;

import com.platform.course.domain.model.valueobjects.*;
import java.time.Instant;
import java.util.UUID;

/**
 * 领域事件基类
 */

/**
 * 章节删除事件
 */

public final class CourseArchivedEvent extends DomainEvent {
    private final CourseId courseId;
    private final String tenantId;
    private final String archivedBy;

    public CourseArchivedEvent(CourseId courseId, String tenantId, String archivedBy) {
        super("CourseArchived");
        this.courseId = courseId;
        this.tenantId = tenantId;
        this.archivedBy = archivedBy;
    }

    public CourseId getCourseId() { return courseId; }
    public String getTenantId() { return tenantId; }
    public String getArchivedBy() { return archivedBy; }
}
