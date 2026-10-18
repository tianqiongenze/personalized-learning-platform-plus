package com.platform.course.domain.model.valueobjects;

import java.util.Objects;
import java.util.UUID;

/**
 * 课程ID值对象
 */
public final class CourseId {
    private final UUID value;

    private CourseId(UUID value) {
        this.value = Objects.requireNonNull(value, "课程ID不能为空");
    }

    public static CourseId of(UUID value) {
        return new CourseId(value);
    }

    public static CourseId of(String value) {
        return new CourseId(UUID.fromString(value));
    }

    public static CourseId generate() {
        return new CourseId(UUID.randomUUID());
    }

    public UUID getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CourseId courseId = (CourseId) o;
        return Objects.equals(value, courseId.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}