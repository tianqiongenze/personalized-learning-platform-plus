package com.platform.teaching.domain.model.valueobjects;

import java.util.Objects;
import java.util.UUID;

/**
 * 讨论ID值对象
 */
public final class DiscussionId {
    private final UUID value;

    private DiscussionId(UUID value) {
        this.value = Objects.requireNonNull(value, "讨论ID不能为空");
    }

    public static DiscussionId of(UUID value) {
        return new DiscussionId(value);
    }

    public static DiscussionId of(String value) {
        return new DiscussionId(UUID.fromString(value));
    }

    public static DiscussionId generate() {
        return new DiscussionId(UUID.randomUUID());
    }

    public UUID getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DiscussionId that = (DiscussionId) o;
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