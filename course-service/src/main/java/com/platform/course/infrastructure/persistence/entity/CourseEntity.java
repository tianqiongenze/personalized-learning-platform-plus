package com.platform.course.infrastructure.persistence.entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.platform.course.domain.model.valueobjects.*;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 课程JPA实体
 */
@Entity
@Table(name = "courses", indexes = {
    @Index(name = "idx_courses_tenant_code", columnList = "tenant_id, code", unique = true),
    @Index(name = "idx_courses_status", columnList = "status"),
    @Index(name = "idx_courses_creator", columnList = "creator_id"),
    @Index(name = "idx_courses_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourseEntity {

    @Id
    @Column(name = "id", columnDefinition = "char(36)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID id;

    @Column(name = "tenant_id", columnDefinition = "char(36)", nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID tenantId;

    @Column(name = "code", length = 50, nullable = false)
    private String code;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "subject", length = 50)
    private String subject;

    @Column(name = "grade_level", length = 20)
    private String gradeLevel;

    @Column(name = "semester", length = 20)
    private String semester;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private CourseStatus status;

    @Column(name = "cover_url", length = 500)
    private String coverUrl;

    @Column(name = "settings", columnDefinition = "json")
    private String settingsJson;

    @Column(name = "creator_id", length = 100, nullable = false)
    private String creatorId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

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

    public CourseId getCourseId() {
        return id != null ? CourseId.of(id) : null;
    }

    public void setCourseId(CourseId courseId) {
        this.id = courseId != null ? courseId.getValue() : null;
    }

    public String getTenantIdValue() {
        return tenantId != null ? tenantId.toString() : null;
    }

    public void setTenantIdValue(String tenantId) {
        this.tenantId = tenantId != null ? UUID.fromString(tenantId) : null;
    }

    public Map<String, Object> getSettings() {
        if (settingsJson == null || settingsJson.isEmpty()) return Map.of();
        // 实际应使用 Jackson 反序列化
        return Map.of();
    }

    public void setSettings(Map<String, Object> settings) {
        // 实际应使用 Jackson 序列化
        this.settingsJson = settings != null ? settings.toString() : "{}";
    }
}