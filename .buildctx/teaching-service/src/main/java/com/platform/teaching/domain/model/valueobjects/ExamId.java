package com.platform.teaching.domain.model.valueobjects;

import java.util.Objects;
import java.util.UUID;

/**
 * 考试ID值对象
 */
public final class ExamId {
    private final UUID value;

    private ExamId(UUID value) {
        this.value = Objects.requireNonNull(value, "考试ID不能为空");
    }

    public static ExamId of(UUID value) {
        return new ExamId(value);
    }

    public static ExamId of(String value) {
        return new ExamId(UUID.fromString(value));
    }

    public static ExamId generate() {
        return new ExamId(UUID.randomUUID());
    }

    public UUID getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExamId that = (ExamId) o;
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