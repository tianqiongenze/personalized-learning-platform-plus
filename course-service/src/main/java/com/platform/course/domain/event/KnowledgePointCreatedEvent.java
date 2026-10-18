package com.platform.course.domain.event;

import com.platform.course.domain.model.valueobjects.*;
import java.time.Instant;
import java.util.UUID;

/**
 * 领域事件基类
 */

/**
 * 课程创建事件
 */

public final class KnowledgePointCreatedEvent extends DomainEvent {
    private final KnowledgePointId kpId;
    private final CourseId courseId;
    private final String tenantId;
    private final String code;
    private final String name;
    private final String createdBy;

    public KnowledgePointCreatedEvent(KnowledgePointId kpId, CourseId courseId, String tenantId, String code, String name, String createdBy) {
        super("KnowledgePointCreated");
        this.kpId = kpId;
        this.courseId = courseId;
        this.tenantId = tenantId;
        this.code = code;
        this.name = name;
        this.createdBy = createdBy;
    }

    public KnowledgePointId getKpId() { return kpId; }
    public CourseId getCourseId() { return courseId; }
    public String getTenantId() { return tenantId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getCreatedBy() { return createdBy; }
}
