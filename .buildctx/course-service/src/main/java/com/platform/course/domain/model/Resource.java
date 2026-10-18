package com.platform.course.domain.model;

import com.platform.course.domain.event.*;
import com.platform.course.domain.model.valueobjects.*;
import java.time.Instant;
import java.util.*;

/**
 * 资源聚合根
 * 封装资源生命周期管理的业务规则
 */
public class Resource {
    private final ResourceId id;
    private final CourseId courseId;
    private final String tenantId;
    private final ChapterId chapterId;
    private final Set<KnowledgePointId> kpIds = new HashSet<>();
    private ResourceType type;
    private String title;
    private String description;
    private String contentUrl;
    private String coverUrl;
    private Map<String, Object> metadata = new HashMap<>();
    private int difficulty;
    private Set<String> tags = new HashSet<>();
    private ResourceStatus status;
    private int version;
    private final String creatorId;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;
    private String deletedBy;
    private String deleteReason;
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    // 构造函数 - 仅工厂方法使用
    private Resource(ResourceId id, CourseId courseId, String tenantId, ChapterId chapterId,
                     ResourceType type, String title, String description, String contentUrl,
                     String coverUrl, Map<String, Object> metadata, int difficulty,
                     Set<String> tags, String creatorId, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "资源ID不能为空");
        this.courseId = Objects.requireNonNull(courseId, "课程ID不能为空");
        this.tenantId = Objects.requireNonNull(tenantId, "租户ID不能为空");
        this.chapterId = chapterId;
        this.type = Objects.requireNonNull(type, "资源类型不能为空");
        this.title = validateTitle(title);
        this.description = description != null ? description.trim() : null;
        this.contentUrl = contentUrl;
        this.coverUrl = coverUrl;
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
        this.difficulty = difficulty;
        this.tags = tags != null ? new HashSet<>(tags) : new HashSet<>();
        this.status = ResourceStatus.DRAFT;
        this.version = 1;
        this.creatorId = creatorId;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = this.createdAt;
    }

    private String validateTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("资源标题不能为空");
        }
        String trimmed = title.trim();
        if (trimmed.length() > 300) {
            throw new IllegalArgumentException("资源标题不能超过300字符");
        }
        return trimmed;
    }

    /**
     * 创建新资源（工厂方法）
     */
    public static Resource create(CourseId courseId, String tenantId, ChapterId chapterId,
                                  ResourceType type, String title, String description, String contentUrl,
                                  String coverUrl, Map<String, Object> metadata, int difficulty,
                                  Set<String> tags, String creatorId) {
        Resource resource = new Resource(
            ResourceId.generate(), courseId, tenantId, chapterId, type, title, description,
            contentUrl, coverUrl, metadata, difficulty, tags, creatorId, Instant.now()
        );
        resource.addDomainEvent(new ResourceCreatedEvent(resource.id, courseId, tenantId, resource.title, creatorId));
        return resource;
    }

    /**
     * 从持久化重建资源
     */
    public static Resource reconstruct(ResourceId id, CourseId courseId, String tenantId, ChapterId chapterId,
                                       ResourceType type, String title, String description, String contentUrl,
                                       String coverUrl, Map<String, Object> metadata, int difficulty,
                                       Set<String> tags, ResourceStatus status, int version,
                                       String creatorId, Instant createdAt, Instant updatedAt,
                                       Instant deletedAt, String deletedBy, String deleteReason) {
        Resource resource = new Resource(id, courseId, tenantId, chapterId, type, title, description,
            contentUrl, coverUrl, metadata, difficulty, tags, creatorId, createdAt);
        resource.status = status;
        resource.version = version;
        resource.updatedAt = updatedAt;
        resource.deletedAt = deletedAt;
        resource.deletedBy = deletedBy;
        resource.deleteReason = deleteReason;
        return resource;
    }

    // ========== 业务方法 ==========

    /**
     * 发布资源
     */
    public void publish(String publishedBy) {
        status = ResourceStatus.PUBLISHED;
        updatedAt = Instant.now();
        addDomainEvent(new ResourceUpdatedEvent(id, courseId, tenantId, "status:PUBLISHED", publishedBy));
    }

    /**
     * 删除资源
     */
    public void delete(String deletedBy, String reason) {
        if (status == ResourceStatus.DELETED) {
            throw new IllegalStateException("资源已被删除");
        }
        status = ResourceStatus.DELETED;
        deletedAt = Instant.now();
        deletedBy = deletedBy;
        deleteReason = reason;
        updatedAt = Instant.now();
        addDomainEvent(new ResourceDeletedEvent(id, courseId, tenantId, deletedBy, reason));
    }

    /**
     * 更新基本信息
     */
    public void updateInfo(String title, String description, String contentUrl, String coverUrl,
                           int difficulty, Set<String> tags, String updatedBy) {
        this.title = validateTitle(title);
        this.description = description != null ? description.trim() : null;
        this.contentUrl = contentUrl;
        this.coverUrl = coverUrl;
        this.difficulty = difficulty;
        this.tags.clear();
        if (tags != null) {
            this.tags.addAll(tags);
        }
        updatedAt = Instant.now();
        version++;
        addDomainEvent(new ResourceUpdatedEvent(id, courseId, tenantId, "title,description,contentUrl,coverUrl,difficulty,tags", updatedBy));
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
        version++;
        addDomainEvent(new ResourceUpdatedEvent(id, courseId, tenantId, "metadata", updatedBy));
    }

    /**
     * 添 添加关联知识点
     */
    public void addKnowledgePoint(KnowledgePointId kpId, String updatedBy) {
        if (kpIds.add(kpId)) {
            updatedAt = Instant.now();
            version++;
            addDomainEvent(new ResourceUpdatedEvent(id, courseId, tenantId, "kpIds:add", updatedBy));
        }
    }

    /**
     * 移除关联知识点
     */
    public void removeKnowledgePoint(KnowledgePointId kpId, String updatedBy) {
        if (kpIds.remove(kpId)) {
            updatedAt = Instant.now();
            version++;
            addDomainEvent(new ResourceUpdatedEvent(id, courseId, tenantId, "kpIds:remove", updatedBy));
        }
    }

    /**
     * 创建新版本
     */
    public void createNewVersion(String title, String description, String contentUrl, String coverUrl,
                                 Map<String, Object> metadata, int difficulty, Set<String> tags, String updatedBy) {
        // 归档当前版本
        version++;
        this.title = validateTitle(title);
        this.description = description != null ? description.trim() : null;
        this.contentUrl = contentUrl;
        this.coverUrl = coverUrl;
        this.metadata.clear();
        if (metadata != null) {
            this.metadata.putAll(metadata);
        }
        this.difficulty = difficulty;
        this.tags.clear();
        if (tags != null) {
            this.tags.addAll(tags);
        }
        updatedAt = Instant.now();
        addDomainEvent(new ResourceUpdatedEvent(id, courseId, tenantId, "new_version:" + version, updatedBy));
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

    public ResourceId getId() { return id; }
    public CourseId getCourseId() { return courseId; }
    public String getTenantId() { return tenantId; }
    public ChapterId getChapterId() { return chapterId; }
    public Set<KnowledgePointId> getKpIds() { return Collections.unmodifiableSet(kpIds); }
    public ResourceType getType() { return type; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getContentUrl() { return contentUrl; }
    public String getCoverUrl() { return coverUrl; }
    public Map<String, Object> getMetadata() { return Collections.unmodifiableMap(metadata); }
    public int getDifficulty() { return difficulty; }
    public Set<String> getTags() { return Collections.unmodifiableSet(tags); }
    public ResourceStatus getStatus() { return status; }
    public int getVersion() { return version; }
    public String getCreatorId() { return creatorId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public String getDeletedBy() { return deletedBy; }
    public String getDeleteReason() { return deleteReason; }
    public boolean isDeleted() { return status == ResourceStatus.DELETED; }
    public boolean isPublished() { return status.isPublished(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Resource resource = (Resource) o;
        return Objects.equals(id, resource.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Resource{" +
               "id=" + id +
               ", courseId=" + courseId +
               ", title='" + title + '\'' +
               ", type=" + type +
               ", status=" + status +
               ", version=" + version +
               '}';
    }
}