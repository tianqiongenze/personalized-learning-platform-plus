package com.platform.course.domain.event;

import com.platform.course.domain.model.valueobjects.*;
import java.time.Instant;
import java.util.UUID;

/**
 * 领域事件基类
 */

/**
 * 资源更新事件
 */

public final class ResourceDeletedEvent extends DomainEvent {
    private final ResourceId resourceId;
    private final CourseId courseId;
    private final String tenantId;
    private final String deletedBy;
    private final String reason;

    public ResourceDeletedEvent(ResourceId resourceId, CourseId courseId, String tenantId, String deletedBy, String reason) {
        super("ResourceDeleted");
        this.resourceId = resourceId;
        this.courseId = courseId;
        this.tenantId = tenantId;
        this.deletedBy = deletedBy;
        this.reason = reason;
    }

    public ResourceId getResourceId() { return resourceId; }
    public CourseId getCourseId() { return courseId; }
    public String getTenantId() { return tenantId; }
    public String getDeletedBy() { return deletedBy; }
    public String getReason() { return reason; }
}
