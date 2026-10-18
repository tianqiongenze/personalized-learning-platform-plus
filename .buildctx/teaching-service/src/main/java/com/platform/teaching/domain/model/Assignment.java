package com.platform.teaching.domain.model;

import com.platform.teaching.domain.model.valueobjects.AssignmentId;
import com.platform.teaching.domain.model.valueobjects.AssignmentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 作业聚合根
 */
@Entity
@Table(name = "assignments")
@Getter
@Setter
public class Assignment {

    @Id
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "learning_path_id", columnDefinition = "uuid")
    private UUID learningPathId;

    @Column(name = "step_id", columnDefinition = "uuid")
    private UUID stepId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "instructions", columnDefinition = "TEXT")
    private String instructions;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AssignmentStatus status = AssignmentStatus.DRAFT;

    @Column(name = "max_score", nullable = false)
    private Integer maxScore = 100;

    @Column(name = "passing_score", nullable = false)
    private Integer passingScore = 60;

    @Column(name = "allow_late_submission", nullable = false)
    private Boolean allowLateSubmission = false;

    @Column(name = "late_penalty_percent")
    private Integer latePenaltyPercent = 10;

    @Column(name = "max_attempts", nullable = false)
    private Integer maxAttempts = 3;

    @Column(name = "start_at")
    private LocalDateTime startAt;

    @Column(name = "due_at")
    private LocalDateTime dueAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    public Assignment() {}

    public Assignment(AssignmentId id, UUID tenantId, UUID courseId, String title, String description,
                     String instructions, UUID createdBy) {
        this.id = id.getValue();
        this.tenantId = tenantId;
        this.courseId = courseId;
        this.title = title;
        this.description = description;
        this.instructions = instructions;
        this.createdBy = createdBy;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.status = AssignmentStatus.DRAFT;
    }

    public static Assignment create(UUID tenantId, UUID courseId, String title, String description,
                                   String instructions, UUID createdBy) {
        return new Assignment(AssignmentId.generate(), tenantId, courseId, title, description, instructions, createdBy);
    }

    public void publish() {
        if (this.status != AssignmentStatus.DRAFT) {
            throw new IllegalStateException("只能发布草稿状态的作业");
        }
        this.status = AssignmentStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void start() {
        if (this.status != AssignmentStatus.PUBLISHED) {
            throw new IllegalStateException("只能开始已发布的作业");
        }
        this.status = AssignmentStatus.ONGOING;
        this.updatedAt = LocalDateTime.now();
    }

    public void end() {
        if (this.status != AssignmentStatus.ONGOING && this.status != AssignmentStatus.PUBLISHED) {
            throw new IllegalStateException("只能结束进行中或已发布的作业");
        }
        this.status = AssignmentStatus.ENDED;
        this.updatedAt = LocalDateTime.now();
    }

    public void archive() {
        if (this.status != AssignmentStatus.ENDED && this.status != AssignmentStatus.PUBLISHED) {
            throw new IllegalStateException("只能归档已结束或已发布的作业");
        }
        this.status = AssignmentStatus.ARCHIVED;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isOpenForSubmission(LocalDateTime now) {
        if (this.status != AssignmentStatus.ONGOING && this.status != AssignmentStatus.PUBLISHED) {
            return false;
        }
        if (this.startAt != null && now.isBefore(this.startAt)) {
            return false;
        }
        if (this.dueAt != null && now.isAfter(this.dueAt) && !this.allowLateSubmission) {
            return false;
        }
        return true;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}