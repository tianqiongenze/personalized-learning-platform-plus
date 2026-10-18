package com.platform.course.domain.event;

import com.platform.course.domain.model.valueobjects.*;
import java.time.Instant;
import java.util.UUID;

/**
 * 领域事件基类
 */

/**
 * 章节更新事件
 */

public final class ChapterDeletedEvent extends DomainEvent {
    private final ChapterId chapterId;
    private final CourseId courseId;
    private final String tenantId;
    private final String deletedBy;
    private final String reason;

    public ChapterDeletedEvent(ChapterId chapterId, CourseId courseId, String tenantId, String deletedBy, String reason) {
        super("ChapterDeleted");
        this.chapterId = chapterId;
        this.courseId = courseId;
        this.tenantId = tenantId;
        this.deletedBy = deletedBy;
        this.reason = reason;
    }

    public ChapterId getChapterId() { return chapterId; }
    public CourseId getCourseId() { return courseId; }
    public String getTenantId() { return tenantId; }
    public String getDeletedBy() { return deletedBy; }
    public String getReason() { return reason; }
}
