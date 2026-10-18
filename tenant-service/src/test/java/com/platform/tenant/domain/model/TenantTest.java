package com.platform.tenant.domain.model;

import com.platform.tenant.domain.model.valueobjects.*;
import com.platform.tenant.domain.event.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Tenant 聚合根测试")
class TenantTest {

    @Nested
    @DisplayName("创建租户")
    class CreateTenant {

        @Test
        @DisplayName("应该成功创建有效租户")
        void shouldCreateValidTenant() {
            // Given
            String code = "test-tenant";
            String name = "测试租户";
            String description = "测试描述";
            Quota quota = Quota.defaultQuota();
            Branding branding = Branding.defaultBranding();
            TenantConfig config = TenantConfig.defaultConfig();
            String adminUserId = "admin-001";

            // When
            Tenant tenant = Tenant.create(code, name, description, quota, branding, config, adminUserId);

            // Then
            assertThat(tenant.getId()).isNotNull();
            assertThat(tenant.getCode()).isEqualTo(code);
            assertThat(tenant.getName()).isEqualTo(name);
            assertThat(tenant.getDescription()).isEqualTo(description);
            assertThat(tenant.getStatus()).isEqualTo(TenantStatus.DRAFT);
            assertThat(tenant.getQuota()).isEqualTo(quota);
            assertThat(tenant.getBranding()).isEqualTo(branding);
            assertThat(tenant.getConfig()).isEqualTo(config);
            assertThat(tenant.getAdminUserId()).isEqualTo(adminUserId);
            assertThat(tenant.getCreatedAt()).isNotNull();
            assertThat(tenant.getUpdatedAt()).isEqualTo(tenant.getCreatedAt());
            assertThat(tenant.isDeleted()).isFalse();
            assertThat(tenant.isActive()).isFalse(); // DRAFT 不是激活态
        }

        @Test
        @DisplayName("应该发布 TenantCreatedEvent 领域事件")
        void shouldPublishTenantCreatedEvent() {
            // When
            Tenant tenant = Tenant.create("test", "测试", "描述", Quota.defaultQuota(), 
                Branding.defaultBranding(), TenantConfig.defaultConfig(), "admin");

            // Then
            List<DomainEvent> events = tenant.getDomainEvents();
            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(TenantCreatedEvent.class);
            
            TenantCreatedEvent event = (TenantCreatedEvent) events.get(0);
            assertThat(event.getTenantId()).isEqualTo(tenant.getId());
            assertThat(event.getTenantCode()).isEqualTo("test");
            assertThat(event.getTenantName()).isEqualTo("测试");
            assertThat(event.getAdminEmail()).contains("admin");
        }

        @Test
        @DisplayName("租户编码为空时应抛出异常")
        void shouldThrowExceptionWhenCodeIsEmpty() {
            assertThatThrownBy(() -> Tenant.create("", "测试", "描述", Quota.defaultQuota(), 
                Branding.defaultBranding(), TenantConfig.defaultConfig(), "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("租户编码不能为空");
        }

        @Test
        @DisplayName("租户编码格式无效时应抛出异常")
        void shouldThrowExceptionWhenCodeFormatInvalid() {
            assertThatThrownBy(() -> Tenant.create("Test_Tenant", "测试", "描述", Quota.defaultQuota(), 
                Branding.defaultBranding(), TenantConfig.defaultConfig(), "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("租户编码必须为3-50位小写字母数字或连字符");
        }

        @Test
        @DisplayName("租户名称为空时应抛出异常")
        void shouldThrowExceptionWhenNameIsEmpty() {
            assertThatThrownBy(() -> Tenant.create("test", "", "描述", Quota.defaultQuota(), 
                Branding.defaultBranding(), TenantConfig.defaultConfig(), "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("租户名称不能为空");
        }
    }

    @Nested
    @DisplayName("状态流转")
    class StatusTransition {

        private Tenant createActiveTenant() {
            Tenant tenant = Tenant.create("test", "测试", "描述", Quota.defaultQuota(), 
                Branding.defaultBranding(), TenantConfig.defaultConfig(), "admin");
            tenant.activate("admin");
            tenant.clearDomainEvents();
            return tenant;
        }

        private Tenant createActiveTenantWithQuota(Quota quota) {
            Tenant tenant = Tenant.create("test", "测试", "描述", quota,
                Branding.defaultBranding(), TenantConfig.defaultConfig(), "admin");
            tenant.activate("admin");
            tenant.clearDomainEvents();
            return tenant;
        }

        @Test
        @DisplayName("草稿态可激活")
        void shouldActivateFromDraft() {
            Tenant tenant = Tenant.create("test", "测试", "描述", Quota.defaultQuota(), 
                Branding.defaultBranding(), TenantConfig.defaultConfig(), "admin");
            
            tenant.activate("admin");

            assertThat(tenant.getStatus()).isEqualTo(TenantStatus.ACTIVE);
            assertThat(tenant.isActive()).isTrue();
            // 聚合累积领域事件: 创建(TenantCreatedEvent) + 激活(TenantStatusChangedEvent)
            List<DomainEvent> events = tenant.getDomainEvents();
            assertThat(events).hasSize(2);
            assertThat(events.get(events.size() - 1)).isInstanceOf(TenantStatusChangedEvent.class);
        }

        @Test
        @DisplayName("激活态可暂停")
        void shouldSuspendFromActive() {
            Tenant tenant = createActiveTenant();
            
            tenant.suspend("违规操作", "admin");
            
            assertThat(tenant.getStatus()).isEqualTo(TenantStatus.SUSPENDED);
            assertThat(tenant.isActive()).isFalse();
        }

        @Test
        @DisplayName("暂停态可恢复")
        void shouldRestoreFromSuspended() {
            Tenant tenant = createActiveTenant();
            tenant.suspend("违规", "admin");
            tenant.clearDomainEvents();
            
            tenant.restore("admin");
            
            assertThat(tenant.getStatus()).isEqualTo(TenantStatus.ACTIVE);
            assertThat(tenant.isActive()).isTrue();
            assertThat(tenant.getDomainEvents()).hasSize(2); // StatusChanged + Restored
        }

        @Test
        @DisplayName("激活态可过期")
        void shouldExpireFromActive() {
            Tenant tenant = createActiveTenant();
            
            tenant.expire("admin");
            
            assertThat(tenant.getStatus()).isEqualTo(TenantStatus.EXPIRED);
        }

        @Test
        @DisplayName("激活态可锁定")
        void shouldLockFromActive() {
            Tenant tenant = createActiveTenant();
            
            tenant.lock("涉嫌违规", "admin");
            
            assertThat(tenant.getStatus()).isEqualTo(TenantStatus.LOCKED);
        }

        @Test
        @DisplayName("激活态可删除")
        void shouldDeleteFromActive() {
            Tenant tenant = createActiveTenant();
            
            tenant.delete("admin", "不再使用");
            
            assertThat(tenant.getStatus()).isEqualTo(TenantStatus.DELETED);
            assertThat(tenant.isDeleted()).isTrue();
            assertThat(tenant.getDeletedAt()).isNotNull();
            assertThat(tenant.getDeletedBy()).isEqualTo("admin");
            assertThat(tenant.getDeleteReason()).isEqualTo("不再使用");
        }

        @Test
        @DisplayName("已删除态可恢复")
        void shouldRestoreFromDeleted() {
            Tenant tenant = createActiveTenant();
            tenant.delete("admin", "测试删除");
            tenant.clearDomainEvents();
            
            tenant.restore("admin");
            
            assertThat(tenant.getStatus()).isEqualTo(TenantStatus.ACTIVE);
            assertThat(tenant.isDeleted()).isFalse();
            assertThat(tenant.getDeletedAt()).isNull();
            assertThat(tenant.getDeletedBy()).isNull();
            assertThat(tenant.getDeleteReason()).isNull();
        }

        @Test
        @DisplayName("非法状态流转应抛出异常")
        void shouldThrowExceptionOnInvalidTransition() {
            Tenant tenant = createActiveTenant();
            tenant.suspend("违规", "admin");
            
            // 暂停态不能直接过期
            assertThatThrownBy(() -> tenant.expire("admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不允许从 暂停 流转到 过期");
        }
    }

    @Nested
    @DisplayName("业务操作")
    class BusinessOperations {

        private Tenant createActiveTenantWithQuota(Quota quota) {
            Tenant tenant = Tenant.create("test", "测试", "描述", quota,
                Branding.defaultBranding(), TenantConfig.defaultConfig(), "admin");
            tenant.activate("admin");
            tenant.clearDomainEvents();
            return tenant;
        }

        private Tenant createActiveTenant() {
            Tenant tenant = Tenant.create("test", "测试", "描述", Quota.defaultQuota(), 
                Branding.defaultBranding(), TenantConfig.defaultConfig(), "admin");
            tenant.activate("admin");
            tenant.clearDomainEvents();
            return tenant;
        }

        @Test
        @DisplayName("更新基本信息")
        void shouldUpdateInfo() {
            Tenant tenant = createActiveTenant();
            
            tenant.updateInfo("新名称", "新描述", "admin");
            
            assertThat(tenant.getName()).isEqualTo("新名称");
            assertThat(tenant.getDescription()).isEqualTo("新描述");
            assertThat(tenant.getDomainEvents()).hasSize(1);
        }

        @Test
        @DisplayName("更新配额")
        void shouldUpdateQuota() {
            Tenant tenant = createActiveTenant();
            Quota newQuota = Quota.defaultQuota();
            
            tenant.updateQuota(newQuota, "admin");
            
            assertThat(tenant.getQuota()).isEqualTo(newQuota);
            assertThat(tenant.getDomainEvents()).hasSize(1);
            assertThat(tenant.getDomainEvents().get(0)).isInstanceOf(TenantQuotaChangedEvent.class);
        }

        @Test
        @DisplayName("更新品牌定制")
        void shouldUpdateBranding() {
            Tenant tenant = createActiveTenant();
            Branding newBranding = Branding.of("https://example.com/logo.png", "#ff0000", "#00ff00", 
                null, "custom.example.com", null, null, null);
            
            tenant.updateBranding(newBranding, "admin");
            
            assertThat(tenant.getBranding()).isEqualTo(newBranding);
            assertThat(tenant.getDomainEvents()).hasSize(1);
            assertThat(tenant.getDomainEvents().get(0)).isInstanceOf(TenantBrandingChangedEvent.class);
        }

        @Test
        @DisplayName("更新配置")
        void shouldUpdateConfig() {
            Tenant tenant = createActiveTenant();
            TenantConfig newConfig = TenantConfig.defaultConfig().withFeature("llm_assistant", true);
            
            tenant.updateConfig(newConfig, "admin");
            
            assertThat(tenant.getConfig()).isEqualTo(newConfig);
            assertThat(tenant.getDomainEvents()).hasSize(1);
        }

        @Test
        @DisplayName("检查用户配额")
        void shouldCheckUserQuota() {
            // 显式 starter 档配额(maxUsers=100), 避免依赖 defaultQuota 的具体数值
            Tenant tenant = createActiveTenantWithQuota(Quota.of(100, 50, 10, 1000, 50));

            assertThat(tenant.canAllocateUser(50)).isTrue();
            assertThat(tenant.canAllocateUser(100)).isFalse();
            assertThat(tenant.canAllocateUser(101)).isFalse();
        }

        @Test
        @DisplayName("检查课程配额")
        void shouldCheckCourseQuota() {
            Tenant tenant = createActiveTenantWithQuota(Quota.of(100, 50, 10, 1000, 50));

            assertThat(tenant.canAllocateCourse(25)).isTrue();
            assertThat(tenant.canAllocateCourse(50)).isFalse();
        }

        @Test
        @DisplayName("检查存储配额")
        void shouldCheckStorageQuota() {
            Tenant tenant = createActiveTenantWithQuota(Quota.of(100, 50, 10, 1000, 50));
            long tenGB = 10L * 1024 * 1024 * 1024;

            assertThat(tenant.isStorageExceeded(5L * 1024 * 1024 * 1024)).isFalse();
            assertThat(tenant.isStorageExceeded(tenGB)).isFalse();      // 恰好用满不算超限
            assertThat(tenant.isStorageExceeded(tenGB + 1)).isTrue();   // 超过才判超限
            assertThat(tenant.isStorageExceeded(11L * 1024 * 1024 * 1024)).isTrue();
        }
    }

    @Nested
    @DisplayName("重建租户")
    class ReconstructTenant {

        @Test
        @DisplayName("从持久化重建租户应保持所有状态")
        void shouldReconstructTenantWithAllState() {
            // Given
            TenantId id = TenantId.generate();
            Instant createdAt = Instant.now().minusSeconds(3600);
            Instant updatedAt = Instant.now().minusSeconds(1800);
            Instant deletedAt = Instant.now().minusSeconds(600);
            
            // When
            Tenant tenant = Tenant.reconstruct(
                id, "test", "测试", "描述",
                TenantStatus.SUSPENDED, Quota.defaultQuota(), Branding.defaultBranding(),
                TenantConfig.defaultConfig(), "admin-001",
                createdAt, updatedAt, deletedAt, "admin", "违规"
            );

            // Then
            assertThat(tenant.getId()).isEqualTo(id);
            assertThat(tenant.getCode()).isEqualTo("test");
            assertThat(tenant.getName()).isEqualTo("测试");
            assertThat(tenant.getStatus()).isEqualTo(TenantStatus.SUSPENDED);
            assertThat(tenant.getQuota()).isEqualTo(Quota.defaultQuota());
            assertThat(tenant.getAdminUserId()).isEqualTo("admin-001");
            assertThat(tenant.getCreatedAt()).isEqualTo(createdAt);
            assertThat(tenant.getUpdatedAt()).isEqualTo(updatedAt);
            assertThat(tenant.getDeletedAt()).isEqualTo(deletedAt);
            assertThat(tenant.getDeletedBy()).isEqualTo("admin");
            assertThat(tenant.getDeleteReason()).isEqualTo("违规");
            assertThat(tenant.getDomainEvents()).isEmpty(); // 重建不产生事件
        }
    }

    @Nested
    @DisplayName("相等性与哈希")
    class EqualityAndHash {

        @Test
        @DisplayName("相同ID的租户应相等")
        void shouldBeEqualWhenSameId() {
            TenantId id = TenantId.generate();
            Tenant tenant1 = Tenant.reconstruct(id, "test", "测试", "描述", TenantStatus.ACTIVE, 
                Quota.defaultQuota(), Branding.defaultBranding(), TenantConfig.defaultConfig(), 
                "admin", Instant.now(), Instant.now(), null, null, null);
            Tenant tenant2 = Tenant.reconstruct(id, "other", "其他", "描述", TenantStatus.SUSPENDED, 
                Quota.defaultQuota(), Branding.defaultBranding(), TenantConfig.defaultConfig(), 
                "admin", Instant.now(), Instant.now(), null, null, null);

            assertThat(tenant1).isEqualTo(tenant2);
            assertThat(tenant1.hashCode()).isEqualTo(tenant2.hashCode());
        }

        @Test
        @DisplayName("不同ID的租户不相等")
        void shouldNotBeEqualWhenDifferentId() {
            Tenant tenant1 = Tenant.create("test1", "测试1", "描述", Quota.defaultQuota(), 
                Branding.defaultBranding(), TenantConfig.defaultConfig(), "admin");
            Tenant tenant2 = Tenant.create("test2", "测试2", "描述", Quota.defaultQuota(), 
                Branding.defaultBranding(), TenantConfig.defaultConfig(), "admin");

            assertThat(tenant1).isNotEqualTo(tenant2);
        }
    }
}