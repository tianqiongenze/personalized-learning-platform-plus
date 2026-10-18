package com.platform.course.domain.model.valueobjects;

import java.util.Objects;
import java.util.UUID;

/**
 * 资源ID值对象
 */
public final class ResourceId {
    private final UUID value;

    private ResourceId(UUID value) {
        this.value = Objects.requireNonNull(value, "资源ID不能为空");
    }

    public static ResourceId of(UUID value) {
        return new ResourceId(value);
    }

    public static ResourceId of(String value) {
        return new ResourceId(UUID.fromString(value));
    }

    public static ResourceId generate() {
        return new ResourceId(UUID.randomUUID());
    }

    public UUID getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ResourceId resourceId = (ResourceId) o;
        return Objects.equals(value, resourceId.value);
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