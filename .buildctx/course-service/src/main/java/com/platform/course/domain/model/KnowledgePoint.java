package com.platform.course.domain.model;

import com.platform.course.domain.event.*;
import com.platform.course.domain.model.valueobjects.*;
import java.time.Instant;
import java.util.*;

/**
 * 知识点聚合根
 * 封装知识点生命周期管理的业务规则
 */
public class KnowledgePoint {
    private final KnowledgePointId id;
    private final CourseId courseId;
    private final String tenantId;
    private String code;
    private String name;
    private String description;
    private final ChapterId chapterId;
    private int difficulty;
    private final Set<KnowledgePointId> prerequisites = new HashSet<>();
    private final Set<KnowledgePointId> relatedPoints = new HashSet<>();
    private final Map<String, Object> metadata = new HashMap<>();
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;
    private String deletedBy;
    private String deleteReason;
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    // 构造函数 - 仅工厂方法使用
    private KnowledgePoint(KnowledgePointId id, CourseId courseId, String tenantId, String code, String name,
                           String description, ChapterId chapterId, int difficulty, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "知识点ID不能为空");
        this.courseId = Objects.requireNonNull(courseId, "课程ID不能为空");
        this.tenantId = Objects.requireNonNull(tenantId, "租户ID不能为空");
        this.code = validateCode(code);
        this.name = validateName(name);
        this.description = description != null ? description.trim() : null;
        this.chapterId = chapterId;
        this.difficulty = difficulty;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = this.createdAt;
    }

    private String validateCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("知识点编码不能为空");
        }
        String trimmed = code.trim().toUpperCase();
        if (!trimmed.matches("^[A-Z0-9_]{2,50}$")) {
            throw new IllegalArgumentException("知识点编码必须为2-50位大写字母、数字或下划线");
        }
        return trimmed;
    }

    private String validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("知识点名称不能为空");
        }
        String trimmed = name.trim();
        if (trimmed.length() > 200) {
            throw new IllegalArgumentException("知识点名称不能超过200字符");
        }
        return trimmed;
    }

    /**
     * 创建新知识点（工厂方法）
     */
    public static KnowledgePoint create(CourseId courseId, String tenantId, String code, String name,
                                        String description, ChapterId chapterId, int difficulty, String createdBy) {
        KnowledgePoint kp = new KnowledgePoint(
            KnowledgePointId.generate(), courseId, tenantId, code, name, description,
            chapterId, difficulty, Instant.now()
        );
        kp.addDomainEvent(new KnowledgePointCreatedEvent(kp.id, courseId, tenantId, kp.code, kp.name, createdBy));
        return kp;
    }

    /**
     * 从持久化重建知识点
     */
    public static KnowledgePoint reconstruct(KnowledgePointId id, CourseId courseId, String tenantId, String code, String name,
                                             String description, ChapterId chapterId, int difficulty,
                                             Set<KnowledgePointId> prerequisites, Set<KnowledgePointId> relatedPoints,
                                             Map<String, Object> metadata, Instant createdAt, Instant updatedAt,
                                             Instant deletedAt, String deletedBy, String deleteReason) {
        KnowledgePoint kp = new KnowledgePoint(id, courseId, tenantId, code, name, description, chapterId, difficulty, createdAt);
        kp.prerequisites.addAll(prerequisites);
        kp.relatedPoints.addAll(relatedPoints);
        kp.metadata.putAll(metadata);
        kp.updatedAt = updatedAt;
        kp.deletedAt = deletedAt;
        kp.deletedBy = deletedBy;
        kp.deleteReason = deleteReason;
        return kp;
    }

    // ========== 业务方法 ==========

    /**
     * 删除知识点
     */
    public void delete(String deletedBy, String reason) {
        deletedAt = Instant.now();
        deletedBy = deletedBy;
        deleteReason = reason;
        updatedAt = Instant.now();
        addDomainEvent(new KnowledgePointDeletedEvent(id, courseId, tenantId, deletedBy, reason));
    }

    /**
     * 更新基本信息
     */
    public void updateInfo(String name, String description, int difficulty, String updatedBy) {
        this.name = validateName(name);
        this.description = description != null ? description.trim() : null;
        this.difficulty = difficulty;
        updatedAt = Instant.now();
        addDomainEvent(new KnowledgePointUpdatedEvent(id, courseId, tenantId, "name,description,difficulty", updatedBy));
    }

    /**
     * 更新编码
     */
    public void updateCode(String code, String updatedBy) {
        this.code = validateCode(code);
        updatedAt = Instant.now();
        addDomainEvent(new KnowledgePointUpdatedEvent(id, courseId, tenantId, "code", updatedBy));
    }

    /**
     * 添加前置知识点
     */
    public void addPrerequisite(KnowledgePointId kpId, String updatedBy) {
        if (kpId.equals(this.id)) {
            throw new IllegalArgumentException("知识点不能依赖自己");
        }
        if (prerequisites.add(kpId)) {
            updatedAt = Instant.now();
            addDomainEvent(new KnowledgePointUpdatedEvent(id, courseId, tenantId, "prerequisites:add", updatedBy));
        }
    }

    /**
     * 移除前置知识点
     */
    public void removePrerequisite(KnowledgePointId kpId, String updatedBy) {
        if (prerequisites.remove(kpId)) {
            updatedAt = Instant.now();
            addDomainEvent(new KnowledgePointUpdatedEvent(id, courseId, tenantId, "prerequisites:remove", updatedBy));
        }
    }

    /**
     * 添加关联知识点
     */
    public void addRelatedPoint(KnowledgePointId kpId, String updatedBy) {
        if (kpId.equals(this.id)) {
            throw new IllegalArgumentException("知识点不能关联自己");
        }
        if (relatedPoints.add(kpId)) {
            updatedAt = Instant.now();
            addDomainEvent(new KnowledgePointUpdatedEvent(id, courseId, tenantId, "relatedPoints:add", updatedBy));
        }
    }

    /**
     * 移除关联知识点
     */
    public void removeRelatedPoint(KnowledgePointId kpId, String updatedBy) {
        if (relatedPoints.remove(kpId)) {
            updatedAt = Instant.now();
            addDomainEvent(new KnowledgePointUpdatedEvent(id, courseId, tenantId, "relatedPoints:remove", updatedBy));
        }
    }

    /**
     * 更新元数据
     */
    public void updateMetadata(Map<String, Object> metadata, String updatedBy) {
        this.metadata.clear();
        if (metadata != null) {
            this.metadata.putAll(metadata);
        }
        updatedAt = Instant.now();
        addDomainEvent(new KnowledgePointUpdatedEvent(id, courseId, tenantId, "metadata", updatedBy));
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

    public KnowledgePointId getId() { return id; }
    public CourseId getCourseId() { return courseId; }
    public String getTenantId() { return tenantId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public ChapterId getChapterId() { return chapterId; }
    public int getDifficulty() { return difficulty; }
    public Set<KnowledgePointId> getPrerequisites() { return Collections.unmodifiableSet(prerequisites); }
    public Set<KnowledgePointId> getRelatedPoints() { return Collections.unmodifiableSet(relatedPoints); }
    public Map<String, Object> getMetadata() { return Collections.unmodifiableMap(metadata); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public String getDeletedBy() { return deletedBy; }
    public String getDeleteReason() { return deleteReason; }
    public boolean isDeleted() { return deletedAt != null; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KnowledgePoint that = (KnowledgePoint) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "KnowledgePoint{" +
               "id=" + id +
               ", courseId=" + courseId +
               ", code='" + code + '\'' +
               ", name='" + name + '\'' +
               '}';
    }
}