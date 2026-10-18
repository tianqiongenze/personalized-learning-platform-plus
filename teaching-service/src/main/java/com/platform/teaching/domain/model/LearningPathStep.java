package com.platform.teaching.domain.model;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 学习路径步骤实体
 */
@Entity
@Table(name = "learning_path_steps")
@Getter
@Setter

/**
 * 步骤类型枚举
 */

public class LearningPathStep {

    @Id
    @Column(name = "id", columnDefinition = "char(36)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "learning_path_id", nullable = false)
    private LearningPath learningPath;

    @Column(name = "sequence", nullable = false)
    private Integer sequence;

    @Enumerated(EnumType.STRING)
    @Column(name = "step_type", nullable = false, length = 20)
    private StepType stepType;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "content_id", columnDefinition = "char(36)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID contentId;

    @Column(name = "content_type", length = 50)
    private String contentType;

    @Column(name = "estimated_minutes")
    private Integer estimatedMinutes;

    @Column(name = "is_required", nullable = false)
    private Boolean isRequired = true;

    @Column(name = "prerequisite_step_id", columnDefinition = "char(36)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID prerequisiteStepId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StepStatus status = StepStatus.NOT_STARTED;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public LearningPathStep() {}

    public LearningPathStep(UUID id, LearningPath learningPath, Integer sequence, StepType stepType,
                           String title, String description, UUID contentId, String contentType,
                           Integer estimatedMinutes, Boolean isRequired) {
        this.id = id;
        this.learningPath = learningPath;
        this.sequence = sequence;
        this.stepType = stepType;
        this.title = title;
        this.description = description;
        this.contentId = contentId;
        this.contentType = contentType;
        this.estimatedMinutes = estimatedMinutes;
        this.isRequired = isRequired;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void start() {
        if (this.status != StepStatus.NOT_STARTED) {
            throw new IllegalStateException("只能开始未开始的步骤");
        }
        this.status = StepStatus.IN_PROGRESS;
        this.startedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void complete() {
        if (this.status != StepStatus.IN_PROGRESS) {
            throw new IllegalStateException("只能完成进行中的步骤");
        }
        this.status = StepStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void skip() {
        if (this.status == StepStatus.COMPLETED) {
            throw new IllegalStateException("已完成的步骤不能跳过");
        }
        this.status = StepStatus.SKIPPED;
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}