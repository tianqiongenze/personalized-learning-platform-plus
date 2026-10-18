package com.platform.course.infrastructure.persistence.entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.platform.course.domain.model.valueobjects.*;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * 资源JPA实体
 */
@Entity
@Table(name = "resources", indexes = {
    @Index(name = "idx_resources_course_code", columnList = "course_id, code", unique = true),
    @Index(name = "idx_resources_chapter", columnList = "chapter_id"),
    @Index(name = "idx_resources_type", columnList = "type"),
    @Index(name = "idx_resources_status", columnList = "status"),
    @Index(name = "idx_resources_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResourceEntity {

    @Id
    @Column(name = "id", columnDefinition = "char(36)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID id;

    @Column(name = "course_id", columnDefinition = "char(36)", nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID courseId;

    @Column(name = "tenant_id", columnDefinition = "char(36)", nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID tenantId;

    @Column(name = "chapter_id", columnDefinition = "char(36)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID chapterId;

    @Column(name = "code", length = 50, nullable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 20, nullable = false)
    private ResourceType type;

    @Column(name = "title", length = 300, nullable = false)
    private String title;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "content_url", length = 500)
    private String contentUrl;

    @Column(name = "cover_url", length = 500)
    private String coverUrl;

    @Column(name = "metadata", columnDefinition = "json")
    private String metadataJson;

    @Column(name = "difficulty", nullable = false)
    private int difficulty;

    // TiDB 切换: text[] 数组类型改为 json 存储(转换器序列化/反序列化)
    @Convert(converter = com.platform.course.infrastructure.persistence.StringArrayJsonConverter.class)
    @Column(name = "tags", columnDefinition = "json")
    private String[] tags;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private ResourceStatus status;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "creator_id", length = 100, nullable = false)
    private String creatorId;

    @Column(name = "kp_ids", columnDefinition = "json")
    @Convert(converter = com.platform.course.infrastructure.persistence.UuidArrayJsonConverter.class)
    private UUID[] kpIds;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by", length = 100)
    private String deletedBy;

    @Column(name = "delete_reason", length = 500)
    private String deleteReason;

    @Version
    @Column(name = "jpa_version", nullable = false)
    private Long jpaVersion;

    // ========== 转换方法 ==========

    public ResourceId getResourceId() {
        return id != null ? ResourceId.of(id) : null;
    }

    public void setResourceId(ResourceId resourceId) {
        this.id = resourceId != null ? resourceId.getValue() : null;
    }

    public CourseId getCourseIdValue() {
        return courseId != null ? CourseId.of(courseId) : null;
    }

    public void setCourseIdValue(CourseId courseId) {
        this.courseId = courseId != null ? courseId.getValue() : null;
    }

    public String getTenantIdValue() {
        return tenantId != null ? tenantId.toString() : null;
    }

    public void setTenantIdValue(String tenantId) {
        this.tenantId = tenantId != null ? UUID.fromString(tenantId) : null;
    }

    public ChapterId getChapterIdValue() {
        return chapterId != null ? ChapterId.of(chapterId) : null;
    }

    public void setChapterIdValue(ChapterId chapterId) {
        this.chapterId = chapterId != null ? chapterId.getValue() : null;
    }

    public Set<KnowledgePointId> getKpIdSet() {
        if (kpIds == null) return Set.of();
        return java.util.Arrays.stream(kpIds)
            .map(KnowledgePointId::of)
            .collect(java.util.stream.Collectors.toSet());
    }

    public void setKpIdSet(Set<KnowledgePointId> kpIds) {
        this.kpIds = kpIds != null ? 
            kpIds.stream().map(KnowledgePointId::getValue).toArray(UUID[]::new) : 
            new UUID[0];
    }

    public Set<String> getTagSet() {
        if (tags == null) return Set.of();
        return java.util.Arrays.stream(tags).collect(java.util.stream.Collectors.toSet());
    }

    public void setTagSet(Set<String> tags) {
        this.tags = tags != null ? tags.toArray(new String[0]) : new String[0];
    }
}