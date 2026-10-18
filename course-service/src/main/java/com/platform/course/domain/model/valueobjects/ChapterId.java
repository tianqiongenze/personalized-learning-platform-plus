package com.platform.course.domain.model.valueobjects;

import java.util.Objects;
import java.util.UUID;

/**
 * 章节ID值对象
 */
public final class ChapterId {
    private final UUID value;

    private ChapterId(UUID value) {
        this.value = Objects.requireNonNull(value, "章节ID不能为空");
    }

    public static ChapterId of(UUID value) {
        return new ChapterId(value);
    }

    public static ChapterId of(String value) {
        return new ChapterId(UUID.fromString(value));
    }

    public static ChapterId generate() {
        return new ChapterId(UUID.randomUUID());
    }

    public UUID getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChapterId chapterId = (ChapterId) o;
        return Objects.equals(value, chapterId.value);
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