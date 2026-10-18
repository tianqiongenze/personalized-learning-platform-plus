package com.platform.course.domain.event;

import com.platform.course.domain.model.valueobjects.*;
import java.time.Instant;
import java.util.UUID;

/**
 * 领域事件基类
 */

/**
 * 知识点删除事件
 */

public final class ResourceCreatedEvent extends DomainEvent {
    private final ResourceId resourceId;
    private final CourseId courseId;
    private final String tenantId;
    private final String title;
    private final String createdBy;

    public ResourceCreatedEvent(ResourceId resourceId, CourseId courseId, String tenantId, String title, String createdBy) {
        super("ResourceCreated");
        this.resourceId = resourceId;
        this.courseId = courseId;
        this.tenantId = tenantId;
        this.title = title;
        this.createdBy = createdBy;
    }

    public ResourceId getResourceId() { return resourceId; }
    public CourseId getCourseId() { return courseId; }
    public String getTenantId() { return tenantId; }
    public String getTitle() { return title; }
    public String getCreatedBy() { return createdBy; }
}
