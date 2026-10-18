package com.platform.teaching.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 作业持久化实体
 */
@Entity
@Table(name = "assignments")
@Getter
@Setter
public class AssignmentEntity {

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
    private String status = "DRAFT";

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

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}