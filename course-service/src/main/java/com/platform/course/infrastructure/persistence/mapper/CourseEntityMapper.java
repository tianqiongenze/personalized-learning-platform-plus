package com.platform.course.infrastructure.persistence.mapper;

import com.platform.course.domain.model.Course;
import com.platform.course.domain.model.valueobjects.CourseId;
import com.platform.course.domain.model.valueobjects.CourseStatus;
import com.platform.course.infrastructure.persistence.entity.CourseEntity;
import org.springframework.stereotype.Component;

/**
 * 课程实体映射器(手工实现)
 */
@Component
public class CourseEntityMapper {

    public CourseEntity toEntity(Course c) {
        CourseEntity e = new CourseEntity();
        e.setCourseId(c.getId());
        e.setTenantIdValue(c.getTenantId());
        e.setCode(c.getCode());
        e.setName(c.getName());
        e.setDescription(c.getDescription());
        e.setSubject(c.getSubject());
        e.setGradeLevel(c.getGradeLevel());
        e.setSemester(c.getSemester());
        e.setStatus(c.getStatus());
        e.setCoverUrl(c.getCoverUrl());
        e.setSettings(c.getSettings());
        e.setCreatorId(c.getCreatorId());
        return e;
    }

    public Course toDomain(CourseEntity e) {
        return Course.reconstruct(
            e.getCourseId(), e.getTenantIdValue(), e.getCode(), e.getName(),
            e.getDescription(), e.getSubject(), e.getGradeLevel(), e.getSemester(),
            e.getStatus() == null ? CourseStatus.DRAFT : e.getStatus(), e.getCoverUrl(), e.getSettings(), e.getCreatorId(),
            e.getCreatedAt(), e.getUpdatedAt(), e.getPublishedAt(), e.getArchivedAt(),
            e.getDeletedAt(), e.getDeletedBy(), e.getDeleteReason());
    }

    private static CourseStatus parseStatus(String status) {
        try {
            return status == null ? CourseStatus.DRAFT : CourseStatus.valueOf(status);
        } catch (IllegalArgumentException ex) {
            return CourseStatus.DRAFT;
        }
    }
}
