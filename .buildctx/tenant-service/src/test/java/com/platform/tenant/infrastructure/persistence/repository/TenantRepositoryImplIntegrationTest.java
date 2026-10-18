package com.platform.tenant.infrastructure.persistence.repository;

import com.platform.tenant.domain.model.Tenant;
import com.platform.tenant.domain.model.valueobjects.*;
import com.platform.tenant.infrastructure.persistence.entity.TenantEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@Import({TenantRepositoryImpl.class, TenantEntityMapper.class})
@DisplayName("TenantRepositoryImpl 集成测试")
class TenantRepositoryImplIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TenantRepositoryImpl tenantRepository;

    @Test
    @DisplayName("应该保存并查找租户")
    void shouldSaveAndFindTenant() {
        // Given
        Tenant tenant = Tenant.create("test-tenant", "测试租户", "描述", 
            Quota.starter(), Branding.defaultBranding(), TenantConfig.defaultConfig(), "admin-001");
        tenant.activate("admin");

        // When
        Tenant saved = tenantRepository.save(tenant);
        Optional<Tenant> found = tenantRepository.findById(saved.getId());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getCode()).isEqualTo("test-tenant");
        assertThat(found.get().getName()).isEqualTo("测试租户");
        assertThat(found.get().getStatus()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(found.get().getQuota()).isEqualTo(Quota.starter());
    }

    @Test
    @DisplayName("应该根据编码查找租户")
    void shouldFindByCode() {
        // Given
        Tenant tenant = Tenant.create("find-by-code", "按编码查找", "描述", 
            Quota.starter(), Branding.defaultBranding(), TenantConfig.defaultConfig(), "admin");
        tenant.activate("admin");
        tenantRepository.save(tenant);

        // When
        Optional<Tenant> found = tenantRepository.findByCode("find-by-code");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getCode()).isEqualTo("find-by-code");
    }

    @Test
    @DisplayName("应该检查编码是否存在")
    void shouldCheckCodeExists() {
        // Given
        Tenant tenant = Tenant.create("exists-check", "存在性检查", "描述", 
            Quota.starter(), Branding.defaultBranding(), TenantConfig.defaultConfig(), "admin");
        tenantRepository.save(tenant);

        // When & Then
        assertThat(tenantRepository.existsByCode("exists-check")).isTrue();
        assertThat(tenantRepository.existsByCode("not-exists")).isFalse();
    }

    @Test
    @DisplayName("应该分页查询租户列表")
    void shouldFindAllWithPagination() {
        // Given
        for (int i = 0; i < 5; i++) {
            Tenant tenant = Tenant.create("tenant-" + i, "租户" + i, "描述", 
                Quota.starter(), Branding.defaultBranding(), TenantConfig.defaultConfig(), "admin");
            if (i % 2 == 0) tenant.activate("admin");
            tenantRepository.save(tenant);
        }

        // When
        List<Tenant> activeTenants = tenantRepository.findAll(0, 10, "ACTIVE", null);
        List<Tenant> allTenants = tenantRepository.findAll(0, 10, null, null);

        // Then
        assertThat(activeTenants).hasSize(3); // 0, 2, 4
        assertThat(allTenants).hasSize(5);
    }

    @Test
    @DisplayName("应该统计租户数量")
    void shouldCountTenants() {
        // Given
        for (int i = 0; i < 3; i++) {
            Tenant tenant = Tenant.create("count-" + i, "统计" + i, "描述", 
                Quota.starter(), Branding.defaultBranding(), TenantConfig.defaultConfig(), "admin");
            if (i < 2) tenant.activate("admin");
            tenantRepository.save(tenant);
        }

        // When
        long total = tenantRepository.count(null, null);
        long active = tenantRepository.countByStatus("ACTIVE");
        long draft = tenantRepository.countByStatus("DRAFT");

        // Then
        assertThat(total).isEqualTo(3);
        assertThat(active).isEqualTo(2);
        assertThat(draft).isEqualTo(1);
    }

    @Test
    @DisplayName("应该根据ID批量查找")
    void shouldFindByIds() {
        // Given
        Tenant t1 = Tenant.create("batch-1", "批量1", "描述", Quota.starter(), 
            Branding.defaultBranding(), TenantConfig.defaultConfig(), "admin");
        Tenant t2 = Tenant.create("batch-2", "批量2", "描述", Quota.starter(), 
            Branding.defaultBranding(), TenantConfig.defaultConfig(), "admin");
        t1.activate("admin");
        t2.activate("admin");
        tenantRepository.save(t1);
        tenantRepository.save(t2);

        // When
        List<Tenant> found = tenantRepository.findByIds(List.of(t1.getId(), t2.getId()));

        // Then
        assertThat(found).hasSize(2);
        assertThat(found).extracting(Tenant::getCode).containsExactlyInAnyOrder("batch-1", "batch-2");
    }
}