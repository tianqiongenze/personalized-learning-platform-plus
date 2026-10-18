package com.platform.course.domain.model.valueobjects;

import java.util.Objects;
import java.util.UUID;

/**
 * 知识点ID值对象
 */
public final class KnowledgePointId {
    private final UUID value;

    private KnowledgePointId(UUID value) {
        this.value = Objects.requireNonNull(value, "知识点ID不能为空");
    }

    public static KnowledgePointId of(UUID value) {
        return new KnowledgePointId(value);
    }

    public static KnowledgePointId of(String value) {
        return new KnowledgePointId(UUID.fromString(value));
    }

    public static KnowledgePointId generate() {
        return new KnowledgePointId(UUID.randomUUID());
    }

    public UUID getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KnowledgePointId kpId = (KnowledgePointId) o;
        return Objects.equals(value, kpId.value);
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