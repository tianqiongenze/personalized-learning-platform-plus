package com.platform.teaching.domain.model;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.platform.teaching.domain.model.valueobjects.ExamId;
import com.platform.teaching.domain.model.valueobjects.ExamStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 考试聚合根
 */
@Entity
@Table(name = "exams")
@Getter
@Setter
public class Exam {

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

    @Column(name = "instructions", columnDefinition = "TEXT")
    private String instructions;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ExamStatus status = ExamStatus.DRAFT;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes = 60;

    @Column(name = "max_score", nullable = false)
    private Integer maxScore = 100;

    @Column(name = "passing_score", nullable = false)
    private Integer passingScore = 60;

    @Column(name = "max_attempts", nullable = false)
    private Integer maxAttempts = 1;

    @Column(name = "shuffle_questions", nullable = false)
    private Boolean shuffleQuestions = true;

    @Column(name = "shuffle_options", nullable = false)
    private Boolean shuffleOptions = true;

    @Column(name = "show_results_immediately", nullable = false)
    private Boolean showResultsImmediately = true;

    @Column(name = "start_at")
    private LocalDateTime startAt;

    @Column(name = "end_at")
    private LocalDateTime endAt;

    @Column(name = "created_by", nullable = false, columnDefinition = "char(36)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    public Exam() {}

    public Exam(ExamId id, UUID tenantId, UUID courseId, String title, String description,
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
        this.status = ExamStatus.DRAFT;
    }

    public static Exam create(UUID tenantId, UUID courseId, String title, String description,
                             String instructions, UUID createdBy) {
        return new Exam(ExamId.generate(), tenantId, courseId, title, description, instructions, createdBy);
    }

    public void publish() {
        if (this.status != ExamStatus.DRAFT) {
            throw new IllegalStateException("只能发布草稿状态的考试");
        }
        this.status = ExamStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void start() {
        if (this.status != ExamStatus.PUBLISHED && this.status != ExamStatus.ONGOING) {
            throw new IllegalStateException("只能开始已发布或进行中的考试");
        }
        this.status = ExamStatus.ONGOING;
        this.updatedAt = LocalDateTime.now();
    }

    public void end() {
        if (this.status != ExamStatus.ONGOING && this.status != ExamStatus.PUBLISHED) {
            throw new IllegalStateException("只能结束进行中或已发布的考试");
        }
        this.status = ExamStatus.ENDED;
        this.updatedAt = LocalDateTime.now();
    }

    public void archive() {
        if (this.status != ExamStatus.ENDED && this.status != ExamStatus.PUBLISHED) {
            throw new IllegalStateException("只能归档已结束或已发布的考试");
        }
        this.status = ExamStatus.ARCHIVED;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isOpenForAttempt(LocalDateTime now) {
        if (this.status != ExamStatus.ONGOING && this.status != ExamStatus.PUBLISHED && this.status != ExamStatus.ONGOING) {
            return false;
        }
        if (this.startAt != null && now.isBefore(this.startAt)) {
            return false;
        }
        if (this.endAt != null && now.isAfter(this.endAt)) {
            return false;
        }
        return true;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}