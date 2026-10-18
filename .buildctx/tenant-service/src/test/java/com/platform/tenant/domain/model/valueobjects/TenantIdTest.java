package com.platform.tenant.domain.model.valueobjects;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

@DisplayName("TenantId 值对象测试")
class TenantIdTest {

    @Test
    @DisplayName("应该从UUID创建")
    void shouldCreateFromUUID() {
        java.util.UUID uuid = java.util.UUID.randomUUID();
        TenantId id = TenantId.of(uuid);
        
        assertThat(id.getValue()).isEqualTo(uuid);
    }

    @Test
    @DisplayName("应该从字符串创建")
    void shouldCreateFromString() {
        String uuidStr = "550e8400-e29b-41d4-a716-446655440000";
        TenantId id = TenantId.of(uuidStr);
        
        assertThat(id.getValue().toString()).isEqualTo(uuidStr);
    }

    @Test
    @DisplayName("应该生成新ID")
    void shouldGenerateNewId() {
        TenantId id1 = TenantId.generate();
        TenantId id2 = TenantId.generate();
        
        assertThat(id1).isNotNull();
        assertThat(id2).isNotNull();
        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    @DisplayName("相同值的ID应相等")
    void shouldBeEqualWhenSameValue() {
        java.util.UUID uuid = java.util.UUID.randomUUID();
        TenantId id1 = TenantId.of(uuid);
        TenantId id2 = TenantId.of(uuid);
        
        assertThat(id1).isEqualTo(id2);
        assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
    }

    @Test
    @DisplayName("toString应返回UUID字符串")
    void shouldReturnUUIDString() {
        String uuidStr = "550e8400-e29b-41d4-a716-446655440000";
        TenantId id = TenantId.of(uuidStr);
        
        assertThat(id.toString()).isEqualTo(uuidStr);
    }

    @Test
    @DisplayName("空值应抛出异常")
    void shouldThrowExceptionWhenNull() {
        assertThatThrownBy(() -> TenantId.of((java.util.UUID) null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("租户ID不能为空");
    }
}