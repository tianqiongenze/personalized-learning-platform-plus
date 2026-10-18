package com.platform.course.domain.model;

import com.platform.course.domain.event.*;
import com.platform.course.domain.model.valueobjects.*;
import java.time.Instant;
import java.util.*;

/**
 * 课程聚合根
 * 核心业务实体，封装课程生命周期管理的所有业务规则
 */
public class Course {
    private final CourseId id;
    private final String tenantId;
    private String code;
    private String name;
    private String description;
    private String subject;
    private String gradeLevel;
    private String semester;
    private CourseStatus status;
    private String coverUrl;
    private Map<String, Object> settings = new HashMap<>();
    private final String creatorId;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant publishedAt;
    private Instant archivedAt;
    private Instant deletedAt;
    private String deletedBy;
    private String deleteReason;
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    // 构造函数 - 仅工厂方法使用
    private Course(CourseId id, String tenantId, String code, String name, String description,
                   String subject, String gradeLevel, String semester, CourseStatus status,
                   String coverUrl, Map<String, Object> settings, String creatorId, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "课程ID不能为空");
        this.tenantId = Objects.requireNonNull(tenantId, "租户ID不能为空");
        this.code = validateCode(code);
        this.name = validateName(name);
        this.description = description != null ? description.trim() : null;
        this.subject = subject;
        this.gradeLevel = gradeLevel;
        this.semester = semester;
        this.status = status != null ? status : CourseStatus.DRAFT;
        this.coverUrl = coverUrl;
        this.settings = settings != null ? new HashMap<>(settings) : new HashMap<>();
        this.creatorId = creatorId;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = this.createdAt;
    }

    private String validateCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("课程编码不能为空");
        }
        String trimmed = code.trim().toUpperCase();
        if (!trimmed.matches("^[A-Z0-9_]{2,50}$")) {
            throw new IllegalArgumentException("课程编码必须为2-50位大写字母、数字或下划线");
        }
        return trimmed;
    }

    private String validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("课程名称不能为空");
        }
        String trimmed = name.trim();
        if (trimmed.length() > 200) {
            throw new IllegalArgumentException("课程名称不能超过200字符");
        }
        return trimmed;
    }

    /**
     * 创建新课程（工厂方法）
     */
    public static Course create(String tenantId, String code, String name, String description,
                                String subject, String gradeLevel, String semester,
                                String coverUrl, Map<String, Object> settings, String creatorId) {
        Course course = new Course(
            CourseId.generate(), tenantId, code, name, description,
            subject, gradeLevel, semester, CourseStatus.DRAFT,
            coverUrl, settings, creatorId, Instant.now()
        );
        course.addDomainEvent(new CourseCreatedEvent(course.id, tenantId, course.code, course.name, creatorId));
        return course;
    }

    /**
     * 从持久化重建课程
     */
    public static Course reconstruct(CourseId id, String tenantId, String code, String name, String description,
                                     String subject, String gradeLevel, String semester, CourseStatus status,
                                     String coverUrl, Map<String, Object> settings, String creatorId,
                                     Instant createdAt, Instant updatedAt, Instant publishedAt,
                                     Instant archivedAt, Instant deletedAt, String deletedBy, String deleteReason) {
        Course course = new Course(id, tenantId, code, name, description,
            subject, gradeLevel, semester, status, coverUrl, settings, creatorId, createdAt);
        course.updatedAt = updatedAt;
        course.publishedAt = publishedAt;
        course.archivedAt = archivedAt;
        course.deletedAt = deletedAt;
        course.deletedBy = deletedBy;
        course.deleteReason = deleteReason;
        return course;
    }

    // ========== 业务方法 ==========

    /**
     * 发布课程（草稿 -> 发布）
     */
    public void publish(String publishedBy) {
        status = status.transitionTo(CourseStatus.PUBLISHED);
        publishedAt = Instant.now();
        updatedAt = Instant.now();
        addDomainEvent(new CoursePublishedEvent(id, tenantId, publishedBy));
        addDomainEvent(new CourseStatusChangedEvent(id, tenantId, CourseStatus.DRAFT.name(), CourseStatus.PUBLISHED.name(), publishedBy));
    }

    /**
     * 归档课程（发布 -> 归档）
     */
    public void archive(String archivedBy) {
        status = status.transitionTo(CourseStatus.ARCHIVED);
        archivedAt = Instant.now();
        updatedAt = Instant.now();
        addDomainEvent(new CourseArchivedEvent(id, tenantId, archivedBy));
        addDomainEvent(new CourseStatusChangedEvent(id, tenantId, status.name(), CourseStatus.ARCHIVED.name(), archivedBy));
    }

    /**
     * 恢复发布（归档 -> 发布）
     */
    public void restorePublish(String restoredBy) {
        status = status.transitionTo(CourseStatus.PUBLISHED);
        archivedAt = null;
        updatedAt = Instant.now();
        addDomainEvent(new CourseStatusChangedEvent(id, tenantId, CourseStatus.ARCHIVED.name(), CourseStatus.PUBLISHED.name(), restoredBy));
    }

    /**
     * 删除课程
     */
    public void delete(String deletedBy, String reason) {
        if (status == CourseStatus.DELETED) {
            throw new IllegalStateException("课程已被删除");
        }
        status = status.transitionTo(CourseStatus.DELETED);
        deletedAt = Instant.now();
        deletedBy = deletedBy;
        deleteReason = reason;
        updatedAt = Instant.now();
        addDomainEvent(new CourseDeletedEvent(id, tenantId, deletedBy, reason));
    }

    /**
     * 更新基本信息
     */
    public void updateInfo(String name, String description, String subject, String gradeLevel, String semester,
                           String coverUrl, String updatedBy) {
        this.name = validateName(name);
        this.description = description != null ? description.trim() : null;
        this.subject = subject;
        this.gradeLevel = gradeLevel;
        this.semester = semester;
        this.coverUrl = coverUrl;
        updatedAt = Instant.now();
        addDomainEvent(new CourseUpdatedEvent(id, tenantId, "name,description,subject,gradeLevel,semester,coverUrl", updatedBy));
    }

    /**
     * 更新编码（仅草稿态允许）
     */
    public void updateCode(String code, String updatedBy) {
        if (status != CourseStatus.DRAFT) {
            throw new IllegalStateException("仅草稿态课程允许修改编码");
        }
        this.code = validateCode(code);
        updatedAt = Instant.now();
        addDomainEvent(new CourseUpdatedEvent(id, tenantId, "code", updatedBy));
    }

    /**
     * 更新设置
     */
    public void updateSettings(Map<String, Object> settings, String updatedBy) {
        this.settings.clear();
        if (settings != null) {
            this.settings.putAll(settings);
        }
        updatedAt = Instant.now();
        addDomainEvent(new CourseUpdatedEvent(id, tenantId, "settings", updatedBy));
    }

    // ========== 领域事件管理 ==========

    private void addDomainEvent(DomainEvent event) {
        domainEvents.add(event);
    }

    public List<DomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    public void clearDomainEvents() {
        domainEvents.clear();
    }

    // ========== Getters ==========

    public CourseId getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getSubject() { return subject; }
    public String getGradeLevel() { return gradeLevel; }
    public String getSemester() { return semester; }
    public CourseStatus getStatus() { return status; }
    public String getCoverUrl() { return coverUrl; }
    public Map<String, Object> getSettings() { return Collections.unmodifiableMap(settings); }
    public String getCreatorId() { return creatorId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getArchivedAt() { return archivedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public String getDeletedBy() { return deletedBy; }
    public String getDeleteReason() { return deleteReason; }
    public boolean isDeleted() { return status == CourseStatus.DELETED; }
    public boolean isPublished() { return status.isPublished(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Course course = (Course) o;
        return Objects.equals(id, course.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Course{" +
               "id=" + id +
               ", tenantId='" + tenantId + '\'' +
               ", code='" + code + '\'' +
               ", name='" + name + '\'' +
               ", status=" + status +
               '}';
    }
}