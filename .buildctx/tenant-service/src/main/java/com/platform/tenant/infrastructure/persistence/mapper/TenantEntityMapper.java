package com.platform.tenant.infrastructure.persistence.mapper;

import com.platform.tenant.domain.model.Tenant;
import com.platform.tenant.domain.model.valueobjects.TenantId;
import com.platform.tenant.infrastructure.persistence.entity.TenantEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 租户实体与领域模型映射器(手工实现: 值对象 JSON 序列化在实体侧完成)
 */
@Component
public class TenantEntityMapper {

    public TenantEntity toEntity(Tenant tenant) {
        TenantEntity e = new TenantEntity();
        e.setTenantId(tenant.getId());
        e.setCode(tenant.getCode());
        e.setName(tenant.getName());
        e.setDescription(tenant.getDescription());
        e.setStatus(tenant.getStatus());
        e.setQuota(tenant.getQuota());
        e.setBranding(tenant.getBranding());
        e.setConfig(tenant.getConfig());
        return e;
    }

    public Tenant toDomain(TenantEntity e) {
        return Tenant.reconstruct(
            e.getTenantId(), e.getCode(), e.getName(), e.getDescription(),
            e.getStatus(), e.getQuota(), e.getBranding(), e.getConfig(), e.getAdminUserId(),
            e.getCreatedAt(), e.getUpdatedAt(), e.getDeletedAt(), e.getDeletedBy(), e.getDeleteReason());
    }

    public List<Tenant> toDomainList(List<TenantEntity> entities) {
        return entities.stream().map(this::toDomain).collect(Collectors.toList());
    }
}
