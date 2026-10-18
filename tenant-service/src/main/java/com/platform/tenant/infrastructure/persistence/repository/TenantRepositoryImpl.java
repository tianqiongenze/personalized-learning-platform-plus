package com.platform.tenant.infrastructure.persistence.repository;

import com.platform.tenant.domain.model.Tenant;
import com.platform.tenant.domain.model.valueobjects.TenantId;
import com.platform.tenant.domain.model.valueobjects.TenantStatus;
import com.platform.tenant.domain.repository.TenantRepository;
import com.platform.tenant.infrastructure.persistence.entity.TenantEntity;
import com.platform.tenant.infrastructure.persistence.mapper.TenantEntityMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 租户仓储实现
 * 适配 Spring Data JPA 到领域仓储接口
 */
@Repository
public class TenantRepositoryImpl implements TenantRepository {

    private final SpringDataTenantRepository jpaRepository;
    private final TenantEntityMapper mapper;

    public TenantRepositoryImpl(SpringDataTenantRepository jpaRepository, TenantEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Tenant save(Tenant tenant) {
        TenantEntity entity = mapper.toEntity(tenant);
        TenantEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Tenant> findById(TenantId id) {
        return jpaRepository.findById(id.getValue())
            .map(mapper::toDomain);
    }

    @Override
    public Optional<Tenant> findByCode(String code) {
        return jpaRepository.findByCode(code)
            .map(mapper::toDomain);
    }

    @Override
    public Optional<Tenant> findByAdminUserId(String adminUserId) {
        return jpaRepository.findByAdminUserId(adminUserId)
            .map(mapper::toDomain);
    }

    @Override
    public boolean existsByCode(String code) {
        return jpaRepository.existsByCode(code);
    }

    @Override
    public boolean existsByCodeAndIdNot(String code, TenantId excludeId) {
        return jpaRepository.existsByCodeAndIdNot(code, excludeId.getValue());
    }

    @Override
    public List<Tenant> findAll(int page, int size, String status, String keyword) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        
        TenantStatus statusEnum = status != null ? TenantStatus.valueOf(status.toUpperCase()) : null;
        Page<TenantEntity> result = jpaRepository.findWithFilters(statusEnum, keyword, pageable);
        
        return result.getContent().stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public long count(String status, String keyword) {
        TenantStatus statusEnum = status != null ? TenantStatus.valueOf(status.toUpperCase()) : null;
        return jpaRepository.countWithFilters(statusEnum, keyword);
    }

    @Override
    public long countByStatus(String status) {
        TenantStatus statusEnum = TenantStatus.valueOf(status.toUpperCase());
        return jpaRepository.countByStatus(statusEnum);
    }

    @Override
    public void deleteById(TenantId id) {
        jpaRepository.deleteById(id.getValue());
    }

    @Override
    public List<Tenant> findByIds(List<TenantId> ids) {
        List<UUID> uuids = ids.stream().map(TenantId::getValue).collect(Collectors.toList());
        return jpaRepository.findAllById(uuids).stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }
}