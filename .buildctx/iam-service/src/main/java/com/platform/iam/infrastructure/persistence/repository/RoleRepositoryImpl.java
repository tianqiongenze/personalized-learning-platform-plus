package com.platform.iam.infrastructure.persistence.repository;

import com.platform.iam.domain.model.Role;
import com.platform.iam.domain.model.valueobjects.*;
import com.platform.iam.domain.model.valueobjects.RoleStatus;
import com.platform.iam.domain.repository.RoleRepository;
import com.platform.iam.infrastructure.persistence.entity.RoleEntity;
import com.platform.iam.infrastructure.persistence.mapper.RoleEntityMapper;
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
 * 角色仓储实现
 */
@Repository
public class RoleRepositoryImpl implements RoleRepository {

    private final SpringDataRoleRepository jpaRepository;
    private final RoleEntityMapper mapper;

    public RoleRepositoryImpl(SpringDataRoleRepository jpaRepository, RoleEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Role save(Role role) {
        RoleEntity entity = mapper.toEntity(role);
        RoleEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Role> findById(RoleId id) {
        return jpaRepository.findById(id.getValue())
            .map(mapper::toDomain);
    }

    @Override
    public Optional<Role> findByCode(TenantId tenantId, String code) {
        return jpaRepository.findByTenantIdAndCode(tenantId.getValue(), code)
            .map(mapper::toDomain);
    }

    @Override
    public boolean existsByCode(TenantId tenantId, String code) {
        return jpaRepository.existsByTenantIdAndCode(tenantId.getValue(), code);
    }

    @Override
    public List<Role> findByTenantId(TenantId tenantId, int page, int size, String status, String keyword) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        
        RoleStatus statusEnum = status != null ? RoleStatus.valueOf(status.toUpperCase()) : null;
        Page<RoleEntity> result = jpaRepository.findWithFilters(tenantId.getValue(), statusEnum, keyword, false, pageable);
        
        return result.getContent().stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public long countByTenantId(TenantId tenantId, String status, String keyword) {
        RoleStatus statusEnum = status != null ? RoleStatus.valueOf(status.toUpperCase()) : null;
        return jpaRepository.countWithFilters(tenantId.getValue(), statusEnum, keyword, false);
    }

    @Override
    public List<Role> findByUserId(UserId userId) {
        return jpaRepository.findByUserId(userId.getValue()).stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<Role> findSystemRoles() {
        return jpaRepository.findByIsSystemTrue().stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<Role> findByIds(List<RoleId> ids) {
        List<UUID> uuids = ids.stream().map(RoleId::getValue).collect(Collectors.toList());
        return jpaRepository.findAllById(uuids).stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public void deleteById(RoleId id) {
        jpaRepository.deleteById(id.getValue());
    }
}