package com.platform.course.domain.event;

import com.platform.course.domain.model.valueobjects.*;
import java.time.Instant;
import java.util.UUID;

/**
 * 领域事件基类
 */

/**
 * 知识点创建事件
 */

public final class ChapterUpdatedEvent extends DomainEvent {
    private final ChapterId chapterId;
    private final CourseId courseId;
    private final String tenantId;
    private final String changedFields;
    private final String updatedBy;

    public ChapterUpdatedEvent(ChapterId chapterId, CourseId courseId, String tenantId, String changedFields, String updatedBy) {
        super("ChapterUpdated");
        this.chapterId = chapterId;
        this.courseId = courseId;
        this.tenantId = tenantId;
        this.changedFields = changedFields;
        this.updatedBy = updatedBy;
    }

    public ChapterId getChapterId() { return chapterId; }
    public CourseId getCourseId() { return courseId; }
    public String getTenantId() { return tenantId; }
    public String getChangedFields() { return changedFields; }
    public String getUpdatedBy() { return updatedBy; }
}
