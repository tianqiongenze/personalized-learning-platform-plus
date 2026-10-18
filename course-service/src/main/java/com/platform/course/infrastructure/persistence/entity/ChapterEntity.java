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
 * 章节JPA实体
 */
@Entity
@Table(name = "chapters", indexes = {
    @Index(name = "idx_chapters_course_code", columnList = "course_id, code", unique = true),
    @Index(name = "idx_chapters_parent", columnList = "parent_id"),
    @Index(name = "idx_chapters_status", columnList = "status"),
    @Index(name = "idx_chapters_order", columnList = "course_id, sort_order")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChapterEntity {

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

    @Column(name = "parent_id", columnDefinition = "char(36)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID parentId;

    @Column(name = "sort_order", nullable = false)   // TiDB/MySQL: order 为保留字, 建表 DDL 语法错误, 改用 sort_order
    private int order;

    @Column(name = "difficulty", nullable = false)
    private int difficulty;

    @Column(name = "estimated_hours", nullable = false)
    private int estimatedHours;

    @Column(name = "knowledge_point_ids", columnDefinition = "json")
    @Convert(converter = com.platform.course.infrastructure.persistence.UuidArrayJsonConverter.class)
    private UUID[] knowledgePointIds;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private ChapterStatus status;

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

    public ChapterId getChapterId() {
        return id != null ? ChapterId.of(id) : null;
    }

    public void setChapterId(ChapterId chapterId) {
        this.id = chapterId != null ? chapterId.getValue() : null;
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

    public Set<KnowledgePointId> getKnowledgePointIdSet() {
        if (knowledgePointIds == null) return Set.of();
        return java.util.Arrays.stream(knowledgePointIds)
            .map(KnowledgePointId::of)
            .collect(java.util.stream.Collectors.toSet());
    }

    public void setKnowledgePointIdSet(Set<KnowledgePointId> kpIds) {
        this.knowledgePointIds = kpIds != null ? 
            kpIds.stream().map(KnowledgePointId::getValue).toArray(UUID[]::new) : 
            new UUID[0];
    }
}