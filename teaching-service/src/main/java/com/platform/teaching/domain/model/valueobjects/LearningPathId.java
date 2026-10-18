package com.platform.teaching.domain.model.valueobjects;

import java.util.Objects;
import java.util.UUID;

/**
 * 学习路径ID值对象
 */
public class LearningPathId {
    private final UUID value;

    private LearningPathId(UUID value) {
        this.value = Objects.requireNonNull(value, "LearningPathId cannot be null");
    }

    public static LearningPathId of(UUID value) {
        return new LearningPathId(value);
    }

    public static LearningPathId generate() {
        return new LearningPathId(UUID.randomUUID());
    }

    public static LearningPathId fromString(String value) {
        return new LearningPathId(UUID.fromString(value));
    }

    public UUID getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LearningPathId that = (LearningPathId) o;
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