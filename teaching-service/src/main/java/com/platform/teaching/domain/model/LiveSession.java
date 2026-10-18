package com.platform.teaching.domain.model;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.platform.teaching.domain.model.valueobjects.LiveSessionId;
import com.platform.teaching.domain.model.valueobjects.LiveSessionStatus;
import com.platform.teaching.domain.model.valueobjects.LiveSessionStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 直播会话聚合根
 */
@Entity
@Table(name = "live_sessions")
@Getter
@Setter
public class LiveSession {

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

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "instructor_id", nullable = false, columnDefinition = "char(36)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID instructorId;

    @Column(name = "instructor_name", nullable = false, length = 100)
    private String instructorName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LiveSessionStatus status = LiveSessionStatus.SCHEDULED;

    @Column(name = "stream_key", length = 100)
    private String streamKey;

    @Column(name = "playback_url", length = 500)
    private String playbackUrl;

    @Column(name = "max_participants", nullable = false)
    private Integer maxParticipants = 100;

    @Column(name = "current_participants", nullable = false)
    private Integer currentParticipants = 0;

    @Column(name = "scheduled_start_at", nullable = false)
    private LocalDateTime scheduledStartAt;

    @Column(name = "scheduled_end_at", nullable = false)
    private LocalDateTime scheduledEndAt;

    @Column(name = "actual_start_at")
    private LocalDateTime actualStartAt;

    @Column(name = "actual_end_at")
    private LocalDateTime actualEndAt;

    @Column(name = "recording_enabled", nullable = false)
    private Boolean recordingEnabled = true;

    @Column(name = "chat_enabled", nullable = false)
    private Boolean chatEnabled = true;

    @Column(name = "created_by", nullable = false, columnDefinition = "char(36)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public LiveSession() {}

    public LiveSession(LiveSessionId id, UUID tenantId, UUID courseId, String title, String description,
                      UUID instructorId, String instructorName, LocalDateTime scheduledStartAt,
                      LocalDateTime scheduledEndAt, UUID createdBy) {
        this.id = id.getValue();
        this.tenantId = tenantId;
        this.courseId = courseId;
        this.title = title;
        this.description = description;
        this.instructorId = instructorId;
        this.instructorName = instructorName;
        this.scheduledStartAt = scheduledStartAt;
        this.scheduledEndAt = scheduledEndAt;
        this.createdBy = createdBy;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.status = LiveSessionStatus.SCHEDULED;
    }

    public static LiveSession create(UUID tenantId, UUID courseId, String title, String description,
                                    UUID instructorId, String instructorName, LocalDateTime scheduledStartAt,
                                    LocalDateTime scheduledEndAt, UUID createdBy) {
        return new LiveSession(LiveSessionId.generate(), tenantId, courseId, title, description,
                              instructorId, instructorName, scheduledStartAt, scheduledEndAt, createdBy);
    }

    public void start() {
        if (this.status != LiveSessionStatus.SCHEDULED) {
            throw new IllegalStateException("只能开始已安排的直播");
        }
        this.status = LiveSessionStatus.LIVE;
        this.actualStartAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void end() {
        if (this.status != LiveSessionStatus.LIVE) {
            throw new IllegalStateException("只能结束进行中的直播");
        }
        this.status = LiveSessionStatus.ENDED;
        this.actualEndAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void cancel() {
        if (this.status == LiveSessionStatus.LIVE || this.status == LiveSessionStatus.ENDED) {
            throw new IllegalStateException("不能取消正在进行或已结束的直播");
        }
        this.status = LiveSessionStatus.CANCELLED;
        this.updatedAt = LocalDateTime.now();
    }

    public void prepare() {
        if (this.status != LiveSessionStatus.SCHEDULED) {
            throw new IllegalStateException("只能准备已安排的直播");
        }
        this.status = LiveSessionStatus.SCHEDULED;
        this.updatedAt = LocalDateTime.now();
    }

    public void join(UUID participantId) {
        if (this.status != LiveSessionStatus.LIVE && this.status != LiveSessionStatus.SCHEDULED) {
            throw new IllegalStateException("只能加入进行中或已安排的直播");
        }
        if (this.currentParticipants >= this.maxParticipants) {
            throw new IllegalStateException("直播参与人数已达上限");
        }
        this.currentParticipants++;
        this.updatedAt = LocalDateTime.now();
    }

    public void leave(UUID participantId) {
        if (this.currentParticipants > 0) {
            this.currentParticipants--;
            this.updatedAt = LocalDateTime.now();
        }
    }

    public void setPlaybackUrl(String playbackUrl) {
        this.playbackUrl = playbackUrl;
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}