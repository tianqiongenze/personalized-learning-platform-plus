package com.platform.teaching.infrastructure.persistence.entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 学习路径步骤持久化实体
 */
@Entity
@Table(name = "learning_path_steps")
@Getter
@Setter
public class LearningPathStepEntity {

    @Id
    @Column(name = "id", columnDefinition = "char(36)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "learning_path_id", nullable = false)
    private LearningPathEntity learningPath;

    @Column(name = "sequence", nullable = false)
    private Integer sequence;

    @Column(name = "step_type", nullable = false, length = 20)
    private String stepType;

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

    @Column(name = "status", nullable = false, length = 20)
    private String status = "NOT_STARTED";

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}