package com.platform.teaching.domain.model.valueobjects;

import java.util.Objects;
import java.util.UUID;

/**
 * 直播会话ID值对象
 */
public final class LiveSessionId {
    private final UUID value;

    private LiveSessionId(UUID value) {
        this.value = Objects.requireNonNull(value, "直播会话ID不能为空");
    }

    public static LiveSessionId of(UUID value) {
        return new LiveSessionId(value);
    }

    public static LiveSessionId of(String value) {
        return new LiveSessionId(UUID.fromString(value));
    }

    public static LiveSessionId generate() {
        return new LiveSessionId(UUID.randomUUID());
    }

    public UUID getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LiveSessionId that = (LiveSessionId) o;
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