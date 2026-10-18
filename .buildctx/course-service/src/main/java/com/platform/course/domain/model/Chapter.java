package com.platform.course.domain.model;

import com.platform.course.domain.event.*;
import com.platform.course.domain.model.valueobjects.*;
import java.time.Instant;
import java.util.*;

/**
 * 章节聚合根
 * 封装章节生命周期管理的业务规则
 */
public class Chapter {
    private final ChapterId id;
    private final CourseId courseId;
    private final String tenantId;
    private String code;
    private String name;
    private String description;
    private final ChapterId parentId;
    private int order;
    private int difficulty;
    private int estimatedHours;
    private final Set<KnowledgePointId> knowledgePointIds = new HashSet<>();
    private ChapterStatus status;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;
    private String deletedBy;
    private String deleteReason;
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    // 构造函数 - 仅工厂方法使用
    private Chapter(ChapterId id, CourseId courseId, String tenantId, String code, String name,
                    String description, ChapterId parentId, int order, int difficulty,
                    int estimatedHours, ChapterStatus status, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "章节ID不能为空");
        this.courseId = Objects.requireNonNull(courseId, "课程ID不能为空");
        this.tenantId = Objects.requireNonNull(tenantId, "租户ID不能为空");
        this.code = validateCode(code);
        this.name = validateName(name);
        this.description = description != null ? description.trim() : null;
        this.parentId = parentId;
        this.order = order;
        this.difficulty = difficulty;
        this.estimatedHours = estimatedHours;
        this.status = status != null ? status : ChapterStatus.DRAFT;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = this.createdAt;
    }

    private String validateCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("章节编码不能为空");
        }
        String trimmed = code.trim().toUpperCase();
        if (!trimmed.matches("^[A-Z0-9_]{2,50}$")) {
            throw new IllegalArgumentException("章节编码必须为2-50位大写字母、数字或下划线");
        }
        return trimmed;
    }

    private String validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("章节名称不能为空");
        }
        String trimmed = name.trim();
        if (trimmed.length() > 200) {
            throw new IllegalArgumentException("章节名称不能超过200字符");
        }
        return trimmed;
    }

    /**
     * 创建新章节（工厂方法）
     */
    public static Chapter create(CourseId courseId, String tenantId, String code, String name,
                                 String description, ChapterId parentId, int order,
                                 int difficulty, int estimatedHours, String createdBy) {
        Chapter chapter = new Chapter(
            ChapterId.generate(), courseId, tenantId, code, name, description,
            parentId, order, difficulty, estimatedHours, ChapterStatus.DRAFT, Instant.now()
        );
        chapter.addDomainEvent(new ChapterCreatedEvent(chapter.id, courseId, tenantId, chapter.code, chapter.name, createdBy));
        return chapter;
    }

    /**
     * 从持久化重建章节
     */
    public static Chapter reconstruct(ChapterId id, CourseId courseId, String tenantId, String code, String name,
                                      String description, ChapterId parentId, int order, int difficulty,
                                      int estimatedHours, Set<KnowledgePointId> knowledgePointIds,
                                      ChapterStatus status, Instant createdAt, Instant updatedAt,
                                      Instant deletedAt, String deletedBy, String deleteReason) {
        Chapter chapter = new Chapter(id, courseId, tenantId, code, name, description,
            parentId, order, difficulty, estimatedHours, status, createdAt);
        chapter.knowledgePointIds.addAll(knowledgePointIds);
        chapter.updatedAt = updatedAt;
        chapter.deletedAt = deletedAt;
        chapter.deletedBy = deletedBy;
        chapter.deleteReason = deleteReason;
        return chapter;
    }

    // ========== 业务方法 ==========

    /**
     * 发布章节
     */
    public void publish(String publishedBy) {
        status = ChapterStatus.PUBLISHED;
        updatedAt = Instant.now();
        addDomainEvent(new ChapterUpdatedEvent(id, courseId, tenantId, "status:PUBLISHED", publishedBy));
    }

    /**
     * 删除章节
     */
    public void delete(String deletedBy, String reason) {
        if (status == ChapterStatus.DELETED) {
            throw new IllegalStateException("章节已被删除");
        }
        status = ChapterStatus.DELETED;
        deletedAt = Instant.now();
        deletedBy = deletedBy;
        deleteReason = reason;
        updatedAt = Instant.now();
        addDomainEvent(new ChapterDeletedEvent(id, courseId, tenantId, deletedBy, reason));
    }

    /**
     * 更新基本信息
     */
    public void updateInfo(String name, String description, int difficulty, int estimatedHours, String updatedBy) {
        this.name = validateName(name);
        this.description = description != null ? description.trim() : null;
        this.difficulty = difficulty;
        this.estimatedHours = estimatedHours;
        updatedAt = Instant.now();
        addDomainEvent(new ChapterUpdatedEvent(id, courseId, tenantId, "name,description,difficulty,estimatedHours", updatedBy));
    }

    /**
     * 更新编码（仅草稿态允许）
     */
    public void updateCode(String code, String updatedBy) {
        if (status != ChapterStatus.DRAFT) {
            throw new IllegalStateException("仅草稿态章节允许修改编码");
        }
        this.code = validateCode(code);
        updatedAt = Instant.now();
        addDomainEvent(new ChapterUpdatedEvent(id, courseId, tenantId, "code", updatedBy));
    }

    /**
     * 更新排序
     */
    public void updateOrder(int order, String updatedBy) {
        this.order = order;
        updatedAt = Instant.now();
        addDomainEvent(new ChapterUpdatedEvent(id, courseId, tenantId, "order", updatedBy));
    }

    /**
     * 添加知识点
     */
    public void addKnowledgePoint(KnowledgePointId kpId, String updatedBy) {
        if (knowledgePointIds.add(kpId)) {
            updatedAt = Instant.now();
            addDomainEvent(new ChapterUpdatedEvent(id, courseId, tenantId, "knowledgePointIds:add", updatedBy));
        }
    }

    /**
     * 移除知识点
     */
    public void removeKnowledgePoint(KnowledgePointId kpId, String updatedBy) {
        if (knowledgePointIds.remove(kpId)) {
            updatedAt = Instant.now();
            addDomainEvent(new ChapterUpdatedEvent(id, courseId, tenantId, "knowledgePointIds:remove", updatedBy));
        }
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

    public ChapterId getId() { return id; }
    public CourseId getCourseId() { return courseId; }
    public String getTenantId() { return tenantId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public ChapterId getParentId() { return parentId; }
    public int getOrder() { return order; }
    public int getDifficulty() { return difficulty; }
    public int getEstimatedHours() { return estimatedHours; }
    public Set<KnowledgePointId> getKnowledgePointIds() { return Collections.unmodifiableSet(knowledgePointIds); }
    public ChapterStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public String getDeletedBy() { return deletedBy; }
    public String getDeleteReason() { return deleteReason; }
    public boolean isDeleted() { return status == ChapterStatus.DELETED; }
    public boolean isPublished() { return status.isPublished(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Chapter chapter = (Chapter) o;
        return Objects.equals(id, chapter.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Chapter{" +
               "id=" + id +
               ", courseId=" + courseId +
               ", code='" + code + '\'' +
               ", name='" + name + '\'' +
               ", status=" + status +
               '}';
    }
}