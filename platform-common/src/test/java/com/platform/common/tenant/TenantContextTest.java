package com.platform.common.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * TenantContext 单测: 缺省租户/空值防御/清理语义。
 */
class TenantContextTest {

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void unsetReturnsDefaultTenant() {
        assertEquals(TenantContext.DEFAULT_TENANT, TenantContext.get());
    }

    @Test
    void setThenGetReturnsValue() {
        TenantContext.set("tenant-a");
        assertEquals("tenant-a", TenantContext.get());
    }

    @Test
    void setNullFallsBackToDefault() {
        TenantContext.set(null);
        assertEquals(TenantContext.DEFAULT_TENANT, TenantContext.get());
    }

    @Test
    void setBlankFallsBackToDefault() {
        TenantContext.set("   ");
        assertEquals(TenantContext.DEFAULT_TENANT, TenantContext.get());
    }

    @Test
    void clearRemovesValue() {
        TenantContext.set("tenant-b");
        TenantContext.clear();
        assertEquals(TenantContext.DEFAULT_TENANT, TenantContext.get());
    }

    @Test
    void clearIsIdempotent() {
        TenantContext.clear();
        TenantContext.clear();
        assertNull(null);
        assertEquals(TenantContext.DEFAULT_TENANT, TenantContext.get());
    }
}
