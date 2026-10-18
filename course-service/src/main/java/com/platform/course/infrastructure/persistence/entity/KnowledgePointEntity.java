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
 * 知识点JPA实体
 */
@Entity
@Table(name = "knowledge_points", indexes = {
    @Index(name = "idx_kp_course_code", columnList = "course_id, code", unique = true),
    @Index(name = "idx_kp_chapter", columnList = "chapter_id"),
    @Index(name = "idx_kp_status", columnList = "status"),
    @Index(name = "idx_kp_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KnowledgePointEntity {

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

    @Column(name = "code", length = 50, nullable = false)
    private String code;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "chapter_id", columnDefinition = "char(36)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID chapterId;

    @Column(name = "difficulty", nullable = false)
    private int difficulty;

    @Column(name = "prerequisites", columnDefinition = "json")
    @Convert(converter = com.platform.course.infrastructure.persistence.UuidArrayJsonConverter.class)
    private UUID[] prerequisites;

    @Column(name = "related_points", columnDefinition = "json")
    @Convert(converter = com.platform.course.infrastructure.persistence.UuidArrayJsonConverter.class)
    private UUID[] relatedPoints;

    @Column(name = "metadata", columnDefinition = "json")
    private String metadataJson;

    @Column(name = "status", length = 20, nullable = false)
    private String status;

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
    @Column(name = "version", nullable = false)
    private Long version;

    // ========== 转换方法 ==========

    public KnowledgePointId getKpId() {
        return id != null ? KnowledgePointId.of(id) : null;
    }

    public void setKpId(KnowledgePointId kpId) {
        this.id = kpId != null ? kpId.getValue() : null;
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

    public Set<KnowledgePointId> getPrerequisiteIdSet() {
        if (prerequisites == null) return Set.of();
        return java.util.Arrays.stream(prerequisites)
            .map(KnowledgePointId::of)
            .collect(java.util.stream.Collectors.toSet());
    }

    public void setPrerequisiteIdSet(Set<KnowledgePointId> kpIds) {
        this.prerequisites = kpIds != null ? 
            kpIds.stream().map(KnowledgePointId::getValue).toArray(UUID[]::new) : 
            new UUID[0];
    }

    public Set<KnowledgePointId> getRelatedPointIdSet() {
        if (relatedPoints == null) return Set.of();
        return java.util.Arrays.stream(relatedPoints)
            .map(KnowledgePointId::of)
            .collect(java.util.stream.Collectors.toSet());
    }

    public void setRelatedPointIdSet(Set<KnowledgePointId> kpIds) {
        this.relatedPoints = kpIds != null ? 
            kpIds.stream().map(KnowledgePointId::getValue).toArray(UUID[]::new) : 
            new UUID[0];
    }
}