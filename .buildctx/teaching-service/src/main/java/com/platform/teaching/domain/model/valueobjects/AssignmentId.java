package com.platform.teaching.domain.model.valueobjects;

import java.util.Objects;
import java.util.UUID;

/**
 * 作业ID值对象
 */
public final class AssignmentId {
    private final UUID value;

    private AssignmentId(UUID value) {
        this.value = Objects.requireNonNull(value, "作业ID不能为空");
    }

    public static AssignmentId of(UUID value) {
        return new AssignmentId(value);
    }

    public static AssignmentId of(String value) {
        return new AssignmentId(UUID.fromString(value));
    }

    public static AssignmentId generate() {
        return new AssignmentId(UUID.randomUUID());
    }

    public UUID getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AssignmentId that = (AssignmentId) o;
        return Objects.equals(value, that.value);
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