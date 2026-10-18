package com.platform.course.domain.event;

import com.platform.course.domain.model.valueobjects.*;
import java.time.Instant;
import java.util.UUID;

/**
 * 领域事件基类
 */

public final class ResourceUpdatedEvent extends DomainEvent {
    private final ResourceId resourceId;
    private final CourseId courseId;
    private final String tenantId;
    private final String changedFields;
    private final String updatedBy;

    public ResourceUpdatedEvent(ResourceId resourceId, CourseId courseId, String tenantId, String changedFields, String updatedBy) {
        super("ResourceUpdated");
        this.resourceId = resourceId;
        this.courseId = courseId;
        this.tenantId = tenantId;
        this.changedFields = changedFields;
        this.updatedBy = updatedBy;
    }

    public ResourceId getResourceId() { return resourceId; }
    public CourseId getCourseId() { return courseId; }
    public String getTenantId() { return tenantId; }
    public String getChangedFields() { return changedFields; }
    public String getUpdatedBy() { return updatedBy; }
}
