package com.platform.tenant.application.service;

import com.platform.tenant.application.command.*;
import com.platform.tenant.application.query.*;
import com.platform.tenant.domain.event.DomainEvent;
import com.platform.tenant.domain.model.Tenant;
import com.platform.tenant.domain.model.valueobjects.TenantId;
import com.platform.tenant.domain.repository.TenantRepository;
import com.platform.tenant.infrastructure.event.DomainEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 租户应用服务
 * 编排领域对象完成用例，处理事务边界
 */
@Service
@Transactional
public class TenantApplicationService {

    private final TenantRepository tenantRepository;
    private final TenantQueryService tenantQueryService;
    private final DomainEventPublisher domainEventPublisher;

    public TenantApplicationService(TenantRepository tenantRepository,
                                    TenantQueryService tenantQueryService,
                                    DomainEventPublisher domainEventPublisher) {
        this.tenantRepository = tenantRepository;
        this.tenantQueryService = tenantQueryService;
        this.domainEventPublisher = domainEventPublisher;
    }

    // ========== 命令处理 ==========

    public TenantId createTenant(CreateTenantCommand cmd) {
        // 检查编码唯一性
        if (tenantRepository.existsByCode(cmd.code())) {
            throw new TenantCodeAlreadyExistsException(cmd.code());
        }

        Tenant tenant = Tenant.create(
            cmd.code(), cmd.name(), cmd.description(),
            cmd.quota(), cmd.branding(), cmd.config(), cmd.adminUserId()
        );

        Tenant saved = tenantRepository.save(tenant);
        publishDomainEvents(saved);
        return saved.getId();
    }

    public void updateTenantInfo(UpdateTenantInfoCommand cmd) {
        Tenant tenant = getTenantOrThrow(cmd.tenantId());
        tenant.updateInfo(cmd.name(), cmd.description(), cmd.updatedBy());
        Tenant saved = tenantRepository.save(tenant);
        publishDomainEvents(saved);
    }

    public void updateTenantCode(UpdateTenantCodeCommand cmd) {
        Tenant tenant = getTenantOrThrow(cmd.tenantId());
        
        if (tenantRepository.existsByCodeAndIdNot(cmd.code(), cmd.tenantId())) {
            throw new TenantCodeAlreadyExistsException(cmd.code());
        }
        
        tenant.updateCode(cmd.code(), cmd.updatedBy());
        Tenant saved = tenantRepository.save(tenant);
        publishDomainEvents(saved);
    }

    public void updateTenantQuota(UpdateTenantQuotaCommand cmd) {
        Tenant tenant = getTenantOrThrow(cmd.tenantId());
        tenant.updateQuota(cmd.quota(), cmd.updatedBy());
        Tenant saved = tenantRepository.save(tenant);
        publishDomainEvents(saved);
    }

    public void updateTenantBranding(UpdateTenantBrandingCommand cmd) {
        Tenant tenant = getTenantOrThrow(cmd.tenantId());
        tenant.updateBranding(cmd.branding(), cmd.updatedBy());
        Tenant saved = tenantRepository.save(tenant);
        publishDomainEvents(saved);
    }

    public void updateTenantConfig(UpdateTenantConfigCommand cmd) {
        Tenant tenant = getTenantOrThrow(cmd.tenantId());
        tenant.updateConfig(cmd.config(), cmd.updatedBy());
        Tenant saved = tenantRepository.save(tenant);
        publishDomainEvents(saved);
    }

    public void updateTenantAdmin(UpdateTenantAdminCommand cmd) {
        Tenant tenant = getTenantOrThrow(cmd.tenantId());
        tenant.updateAdminUser(cmd.adminUserId(), cmd.updatedBy());
        Tenant saved = tenantRepository.save(tenant);
        publishDomainEvents(saved);
    }

    public void activateTenant(ActivateTenantCommand cmd) {
        Tenant tenant = getTenantOrThrow(cmd.tenantId());
        tenant.activate(cmd.activatedBy());
        Tenant saved = tenantRepository.save(tenant);
        publishDomainEvents(saved);
    }

    public void suspendTenant(SuspendTenantCommand cmd) {
        Tenant tenant = getTenantOrThrow(cmd.tenantId());
        tenant.suspend(cmd.reason(), cmd.suspendedBy());
        Tenant saved = tenantRepository.save(tenant);
        publishDomainEvents(saved);
    }

    public void restoreTenant(RestoreTenantCommand cmd) {
        Tenant tenant = getTenantOrThrow(cmd.tenantId());
        tenant.restore(cmd.restoredBy());
        Tenant saved = tenantRepository.save(tenant);
        publishDomainEvents(saved);
    }

    public void expireTenant(ExpireTenantCommand cmd) {
        Tenant tenant = getTenantOrThrow(cmd.tenantId());
        tenant.expire(cmd.expiredBy());
        Tenant saved = tenantRepository.save(tenant);
        publishDomainEvents(saved);
    }

    public void lockTenant(LockTenantCommand cmd) {
        Tenant tenant = getTenantOrThrow(cmd.tenantId());
        tenant.lock(cmd.reason(), cmd.lockedBy());
        Tenant saved = tenantRepository.save(tenant);
        publishDomainEvents(saved);
    }

    public void deleteTenant(DeleteTenantCommand cmd) {
        Tenant tenant = getTenantOrThrow(cmd.tenantId());
        tenant.delete(cmd.deletedBy(), cmd.reason());
        Tenant saved = tenantRepository.save(tenant);
        publishDomainEvents(saved);
    }

    // ========== 查询处理 ==========

    @Transactional(readOnly = true)
    public TenantDetailDto findById(TenantId id) {
        return tenantQueryService.findById(id);
    }

    @Transactional(readOnly = true)
    public TenantDetailDto findByCode(String code) {
        return tenantQueryService.findByCode(code);
    }

    @Transactional(readOnly = true)
    public PageResult<TenantListDto> findPage(TenantPageQuery query) {
        return tenantQueryService.findPage(query);
    }

    @Transactional(readOnly = true)
    public List<TenantSimpleDto> findAllActive() {
        return tenantQueryService.findAllActive();
    }

    @Transactional(readOnly = true)
    public TenantStatisticsDto statistics() {
        return tenantQueryService.statistics();
    }

    // ========== 私有方法 ==========

    private Tenant getTenantOrThrow(TenantId id) {
        return tenantRepository.findById(id)
            .orElseThrow(() -> new TenantNotFoundException(id));
    }

    private void publishDomainEvents(Tenant tenant) {
        List<DomainEvent> events = tenant.getDomainEvents();
        if (!events.isEmpty()) {
            domainEventPublisher.publishAll(events);
            tenant.clearDomainEvents();
        }
    }
}
