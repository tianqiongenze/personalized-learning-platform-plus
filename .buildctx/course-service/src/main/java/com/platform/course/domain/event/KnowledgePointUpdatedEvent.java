package com.platform.course.domain.event;

import com.platform.course.domain.model.valueobjects.*;
import java.time.Instant;
import java.util.UUID;

/**
 * 领域事件基类
 */

/**
 * 资源创建事件
 */

public final class KnowledgePointUpdatedEvent extends DomainEvent {
    private final KnowledgePointId kpId;
    private final CourseId courseId;
    private final String tenantId;
    private final String changedFields;
    private final String updatedBy;

    public KnowledgePointUpdatedEvent(KnowledgePointId kpId, CourseId courseId, String tenantId, String changedFields, String updatedBy) {
        super("KnowledgePointUpdated");
        this.kpId = kpId;
        this.courseId = courseId;
        this.tenantId = tenantId;
        this.changedFields = changedFields;
        this.updatedBy = updatedBy;
    }

    public KnowledgePointId getKpId() { return kpId; }
    public CourseId getCourseId() { return courseId; }
    public String getTenantId() { return tenantId; }
    public String getChangedFields() { return changedFields; }
    public String getUpdatedBy() { return updatedBy; }
}
