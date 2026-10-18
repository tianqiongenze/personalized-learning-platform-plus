package com.platform.tenant.application.service;

import com.platform.tenant.application.command.*;
import com.platform.tenant.application.query.*;
import com.platform.tenant.domain.event.DomainEvent;
import com.platform.tenant.domain.model.Tenant;
import com.platform.tenant.domain.model.valueobjects.*;
import com.platform.tenant.domain.repository.TenantRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenantApplicationService 测试")
class TenantApplicationServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private TenantQueryService tenantQueryService;

    @Mock
    private DomainEventPublisher domainEventPublisher;

    @InjectMocks
    private TenantApplicationService tenantService;

    @Nested
    @DisplayName("创建租户")
    class CreateTenant {

        @Test
        @DisplayName("应该成功创建租户")
        void shouldCreateTenant() {
            // Given
            CreateTenantCommand cmd = new CreateTenantCommand(
                "test-tenant", "测试租户", "描述",
                Quota.starter(), Branding(), Branding.defaultBranding(), TenantConfig.defaultConfig(), "admin-001"
            );
            
            when(tenantRepository.existsByCode("test-tenant")).thenReturn(false);
            when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            TenantId tenantId = tenantService.createTenant(cmd);

            // Then
            assertThat(tenantId).isNotNull();
            verify(tenantRepository).save(any(Tenant.class));
            verify(domainEventPublisher).publishAll(anyList());
        }

        @Test
        @DisplayName("编码已存在时应抛出异常")
        void shouldThrowWhenCodeExists() {
            // Given
            CreateTenantCommand cmd = new CreateTenantCommand(
                "existing", "测试", "描述",
                Quota.starter(), Branding.defaultBranding(), TenantConfig.defaultConfig(), "admin"
            );
            
            when(tenantRepository.existsByCode("existing")).thenReturn(true);

            // When & Then
            assertThatThrownBy(() -> tenantService.createTenant(cmd))
                .isInstanceOf(TenantCodeAlreadyExistsException.class)
                .hasMessageContaining("existing");
            
            verify(tenantRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("更新租户信息")
    class UpdateTenantInfo {

        @Test
        @DisplayName("应该成功更新租户信息")
        void shouldUpdateTenantInfo() {
            // Given
            TenantId id = TenantId.generate();
            Tenant tenant = Tenant.create("test", "原名称", "描述", Quota.starter(), 
                Branding.defaultBranding(), TenantConfig.defaultConfig(), "admin");
            tenant.activate("admin");
            tenant.clearDomainEvents();
            
            when(tenantRepository.findById(id)).thenReturn(Optional.of(tenant));
            when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            tenantService.updateTenantInfo(new UpdateTenantInfoCommand(id, "新名称", "新描述", "admin"));

            // Then
            ArgumentCaptor<Tenant> captor = ArgumentCaptor.forClass(Tenant.class);
            verify(tenantRepository).save(captor.capture());
            
            Tenant saved = captor.getValue();
            assertThat(saved.getName()).isEqualTo("新名称");
            assertThat(saved.getDescription()).isEqualTo("新描述");
            verify(domainEventPublisher).publishAll(anyList());
        }

        @Test
        @DisplayName("租户不存在时应抛出异常")
        void shouldThrowWhenTenantNotFound() {
            // Given
            TenantId id = TenantId.generate();
            when(tenantRepository.findById(id)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> tenantService.updateTenantInfo(
                new UpdateTenantInfoCommand(id, "新名称", "描述", "admin")))
                .isInstanceOf(TenantNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("更新租户编码")
    class UpdateTenantCode {

        @Test
        @DisplayName("草稿态租户应可更新编码")
        void shouldUpdateCodeForDraftTenant() {
            // Given
            TenantId id = TenantId.generate();
            Tenant tenant = Tenant.create("old-code", "测试", "描述", Quota.starter(), 
                Branding.defaultBranding(), TenantConfig.defaultConfig(), "admin");
            // 保持草稿态
            tenant.clearDomainEvents();
            
            when(tenantRepository.findById(id)).thenReturn(Optional.of(tenant));
            when(tenantRepository.existsByCodeAndIdNot("new-code", id)).thenReturn(false);
            when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            tenantService.updateTenantCode(new UpdateTenantCodeCommand(id, "new-code", "admin"));

            // Then
            ArgumentCaptor<Tenant> captor = ArgumentCaptor.forClass(Tenant.class);
            verify(tenantRepository).save(captor.capture());
            assertThat(captor.getValue().getCode()).isEqualTo("new-code");
        }

        @Test
        @DisplayName("非草稿态租户更新编码应抛出异常")
        void shouldThrowWhenNotDraft() {
            // Given
            TenantId id = TenantId.generate();
            Tenant tenant = Tenant.create("test", "测试", "描述", Quota.starter(), 
                Branding.defaultBranding(), TenantConfig.defaultConfig(), "admin");
            tenant.activate("admin");
            tenant.clearDomainEvents();
            
            when(tenantRepository.findById(id)).thenReturn(Optional.of(tenant));

            // When & Then
            assertThatThrownBy(() -> tenantService.updateTenantCode(
                new UpdateTenantCodeCommand(id, "new-code", "admin")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("仅草稿态租户允许修改编码");
        }

        @Test
        @DisplayName("编码已存在时应抛出异常")
        void shouldThrowWhenCodeExists() {
            // Given
            TenantId id = TenantId.generate();
            Tenant tenant = Tenant.create("test", "测试", "描述", Quota.starter(), 
                Branding.defaultBranding(), TenantConfig.defaultConfig(), "admin");
            tenant.clearDomainEvents();
            
            when(tenantRepository.findById(id)).thenReturn(Optional.of(tenant));
            when(tenantRepository.existsByCodeAndIdNot("existing", id)).thenReturn(true);

            // When & Then
            assertThatThrownBy(() -> tenantService.updateTenantCode(
                new UpdateTenantCodeCommand(id, "existing", "admin")))
                .isInstanceOf(TenantCodeAlreadyExistsException.class);
        }
    }

    @Nested
    @DisplayName("状态流转")
    class StatusTransition {

        private Tenant createActiveTenant(TenantId id) {
            Tenant tenant = Tenant.create("test", "测试", "描述", Quota.starter(), 
                Branding.defaultBranding(), TenantConfig.defaultConfig(), "admin");
            tenant.activate("admin");
            tenant.clearDomainEvents();
            return tenant;
        }

        @Test
        @DisplayName("应该成功激活草稿态租户")
        void shouldActivateDraftTenant() {
            // Given
            TenantId id = TenantId.generate();
            Tenant tenant = Tenant.create("test", "测试", "描述", Quota.starter(), 
                Branding.defaultBranding(), TenantConfig.defaultConfig(), "admin");
            tenant.clearDomainEvents();
            
            when(tenantRepository.findById(id)).thenReturn(Optional.of(tenant));
            when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            tenantService.activateTenant(new ActivateTenantCommand(id, "admin"));

            // Then
            ArgumentCaptor<Tenant> captor = ArgumentCaptor.forClass(Tenant.class);
            verify(tenantRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(TenantStatus.ACTIVE);
            verify(domainEventPublisher).publishAll(anyList());
        }

        @Test
        @DisplayName("应该成功暂停激活态租户")
        void shouldSuspendActiveTenant() {
            // Given
            TenantId id = TenantId.generate();
            Tenant tenant = createActiveTenant(id);
            
            when(tenantRepository.findById(id)).thenReturn(Optional.of(tenant));
            when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            tenantService.suspendTenant(new SuspendTenantCommand(id, "违规", "admin"));

            // Then
            ArgumentCaptor<Tenant> captor = ArgumentCaptor.forClass(Tenant.class);
            verify(tenantRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(TenantStatus.SUSPENDED);
        }

        @Test
        @DisplayName("应该成功恢复暂停态租户")
        void shouldRestoreSuspendedTenant() {
            // Given
            TenantId id = TenantId.generate();
            Tenant tenant = createActiveTenant(id);
            tenant.suspend("违规", "admin");
            tenant.clearDomainEvents();
            
            when(tenantRepository.findById(id)).thenReturn(Optional.of(tenant));
            when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            tenantService.restoreTenant(new RestoreTenantCommand(id, "admin"));

            // Then
            ArgumentCaptor<Tenant> captor = ArgumentCaptor.forClass(Tenant.class);
            verify(tenantRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(TenantStatus.ACTIVE);
        }

        @Test
        @DisplayName("应该成功删除激活态租户")
        void shouldDeleteActiveTenant() {
            // Given
            TenantId id = TenantId.generate();
            Tenant tenant = createActiveTenant(id);
            
            when(tenantRepository.findById(id)).thenReturn(Optional.of(tenant));
            when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            tenantService.deleteTenant(new DeleteTenantCommand(id, "admin", "不再使用"));

            // Then
            ArgumentCaptor<Tenant> captor = ArgumentCaptor.forClass(Tenant.class);
            verify(tenantRepository).save(captor.capture());
            Tenant saved = captor.getValue();
            assertThat(saved.getStatus()).isEqualTo(TenantStatus.DELETED);
            assertThat(saved.isDeleted()).isTrue();
            assertThat(saved.getDeletedBy()).isEqualTo("admin");
            assertThat(saved.getDeleteReason()).isEqualTo("不再使用");
        }

        @Test
        @DisplayName("非法状态流转应抛出异常")
        void shouldThrowOnInvalidTransition() {
            // Given
            TenantId id = TenantId.generate();
            Tenant tenant = createActiveTenant(id);
            tenant.suspend("违规", "admin");
            tenant.clearDomainEvents();
            
            when(tenantRepository.findById(id)).thenReturn(Optional.of(tenant));

            // When & Then - 暂停态不能直接过期
            assertThatThrownBy(() -> tenantService.expireTenant(new ExpireTenantCommand(id, "admin")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不允许从 暂停 流转到 过期");
        }
    }

    @Nested
    @DisplayName("查询操作")
    class QueryOperations {

        @Test
        @DisplayName("应该委托查询服务")
        void shouldDelegateToQueryService() {
            // Given
            TenantId id = TenantId.generate();
            TenantDetailDto expected = mock(TenantDetailDto.class);
            when(tenantQueryService.findById(id)).thenReturn(expected);

            // When
            TenantDetailDto result = tenantService.findById(id);

            // Then
            assertThat(result).isSameAs(expected);
            verify(tenantQueryService).findById(id);
        }

        @Test
        @DisplayName("分页查询应委托查询服务")
        void shouldDelegatePageQuery() {
            // Given
            TenantPageQuery query = new TenantPageQuery(0, 20, "ACTIVE", null, "createdAt", "desc");
            PageResult<TenantListDto> expected = mock(PageResult.class);
            when(tenantQueryService.findPage(query)).thenReturn(expected);

            // When
            PageResult<TenantListDto> result = tenantService.findPage(query);

            // Then
            assertThat(result).isSameAs(expected);
            verify(tenantQueryService).findPage(query);
        }
    }
}