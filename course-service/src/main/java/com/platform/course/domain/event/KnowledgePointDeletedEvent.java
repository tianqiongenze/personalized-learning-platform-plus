package com.platform.course.domain.event;

import com.platform.course.domain.model.valueobjects.*;
import java.time.Instant;
import java.util.UUID;

/**
 * 领域事件基类
 */

/**
 * 知识点更新事件
 */

public final class KnowledgePointDeletedEvent extends DomainEvent {
    private final KnowledgePointId kpId;
    private final CourseId courseId;
    private final String tenantId;
    private final String deletedBy;
    private final String reason;

    public KnowledgePointDeletedEvent(KnowledgePointId kpId, CourseId courseId, String tenantId, String deletedBy, String reason) {
        super("KnowledgePointDeleted");
        this.kpId = kpId;
        this.courseId = courseId;
        this.tenantId = tenantId;
        this.deletedBy = deletedBy;
        this.reason = reason;
    }

    public KnowledgePointId getKpId() { return kpId; }
    public CourseId getCourseId() { return courseId; }
    public String getTenantId() { return tenantId; }
    public String getDeletedBy() { return deletedBy; }
    public String getReason() { return reason; }
}
