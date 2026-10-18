package com.platform.teaching.domain.model;

import com.platform.teaching.domain.model.valueobjects.LearningPathId;
import com.platform.teaching.domain.model.valueobjects.StepId;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 学习路径聚合根
 */
@Entity
@Table(name = "learning_paths")
@Getter
@Setter
public class LearningPath {

    @Id
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LearningPathStatus status = LearningPathStatus.DRAFT;

    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @OneToMany(mappedBy = "learningPath", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sequence ASC")
    private List<LearningPathStep> steps = new ArrayList<>();

    public LearningPath() {}

    public LearningPath(LearningPathId id, UUID tenantId, UUID courseId, String title, String description, UUID createdBy) {
        this.id = id.getValue();
        this.tenantId = tenantId;
        this.courseId = courseId;
        this.title = title;
        this.description = description;
        this.createdBy = createdBy;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.status = LearningPathStatus.DRAFT;
        this.version = 0L;
    }

    public static LearningPath create(UUID tenantId, UUID courseId, String title, String description, UUID createdBy) {
        return new LearningPath(LearningPathId.generate(), tenantId, courseId, title, description, createdBy);
    }

    public void addStep(LearningPathStep step) {
        step.setLearningPath(this);
        step.setSequence(this.steps.size() + 1);
        this.steps.add(step);
        this.updatedAt = LocalDateTime.now();
        this.version++;
    }

    public void removeStep(StepId stepId) {
        this.steps.removeIf(step -> step.getId().equals(stepId.getValue()));
        // Re-sequence remaining steps
        for (int i = 0; i < this.steps.size(); i++) {
            this.steps.get(i).setSequence(i + 1);
        }
        this.updatedAt = LocalDateTime.now();
        this.version++;
    }

    public void publish() {
        if (this.status != LearningPathStatus.DRAFT) {
            throw new IllegalStateException("只能发布草稿状态的学习路径");
        }
        if (this.steps.isEmpty()) {
            throw new IllegalStateException("学习路径至少需要一个步骤才能发布");
        }
        this.status = LearningPathStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.version++;
    }

    public void archive() {
        if (this.status != LearningPathStatus.PUBLISHED && this.status != LearningPathStatus.COMPLETED) {
            throw new IllegalStateException("只能归档已发布或已完成的学习路径");
        }
        this.status = LearningPathStatus.ARCHIVED;
        this.updatedAt = LocalDateTime.now();
        this.version++;
    }

    public void complete() {
        if (this.status != LearningPathStatus.PUBLISHED) {
            throw new IllegalStateException("只能完成已发布的学习路径");
        }
        this.status = LearningPathStatus.COMPLETED;
        this.updatedAt = LocalDateTime.now();
        this.version++;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}