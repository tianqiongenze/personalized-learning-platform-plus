package com.platform.teaching.domain.model;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.platform.teaching.domain.model.valueobjects.DiscussionId;
import com.platform.teaching.domain.model.valueobjects.DiscussionStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 讨论聚合根
 */
@Entity
@Table(name = "discussions")
@Getter
@Setter
public class Discussion {

    @Id
    @Column(name = "id", columnDefinition = "char(36)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, columnDefinition = "char(36)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID tenantId;

    @Column(name = "course_id", nullable = false, columnDefinition = "char(36)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID courseId;

    @Column(name = "learning_path_id", columnDefinition = "char(36)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID learningPathId;

    @Column(name = "step_id", columnDefinition = "char(36)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID stepId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "author_id", nullable = false, columnDefinition = "char(36)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID authorId;

    @Column(name = "author_name", nullable = false, length = 100)
    private String authorName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DiscussionStatus status = DiscussionStatus.OPEN;

    @Column(name = "is_pinned", nullable = false)
    private Boolean isPinned = false;

    @Column(name = "is_locked", nullable = false)
    private Boolean isLocked = false;

    @Column(name = "view_count", nullable = false)
    private Long viewCount = 0L;

    @Column(name = "reply_count", nullable = false)
    private Long replyCount = 0L;

    @Column(name = "last_reply_at")
    private LocalDateTime lastReplyAt;

    @Column(name = "last_reply_by", columnDefinition = "char(36)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID lastReplyBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Discussion() {}

    public Discussion(DiscussionId id, UUID tenantId, UUID courseId, String title, String content,
                     UUID authorId, String authorName) {
        this.id = id.getValue();
        this.tenantId = tenantId;
        this.courseId = courseId;
        this.title = title;
        this.content = content;
        this.authorId = authorId;
        this.authorName = authorName;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.status = DiscussionStatus.OPEN;
    }

    public static Discussion create(UUID tenantId, UUID courseId, String title, String content,
                                   UUID authorId, String authorName) {
        return new Discussion(DiscussionId.generate(), tenantId, courseId, title, content, authorId, authorName);
    }

    public void close() {
        if (this.status == DiscussionStatus.CLOSED) {
            throw new IllegalStateException("讨论已经关闭");
        }
        this.status = DiscussionStatus.CLOSED;
        this.updatedAt = LocalDateTime.now();
    }

    public void reopen() {
        if (this.status != DiscussionStatus.CLOSED) {
            throw new IllegalStateException("只能重新打开已关闭的讨论");
        }
        this.status = DiscussionStatus.OPEN;
        this.updatedAt = LocalDateTime.now();
    }

    public void lock() {
        this.isLocked = true;
        this.updatedAt = LocalDateTime.now();
    }

    public void unlock() {
        this.isLocked = false;
        this.updatedAt = LocalDateTime.now();
    }

    public void pin() {
        this.isPinned = true;
        this.updatedAt = LocalDateTime.now();
    }

    public void unpin() {
        this.isPinned = false;
        this.updatedAt = LocalDateTime.now();
    }

    public void incrementViewCount() {
        this.viewCount++;
    }

    public void incrementReplyCount(UUID replyAuthorId) {
        this.replyCount++;
        this.lastReplyAt = LocalDateTime.now();
        this.lastReplyBy = replyAuthorId;
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}