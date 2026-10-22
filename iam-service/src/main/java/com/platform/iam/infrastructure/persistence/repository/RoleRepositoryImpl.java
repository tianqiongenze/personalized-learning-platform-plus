package com.platform.iam.infrastructure.persistence.repository;

import com.platform.iam.domain.model.Role;
import com.platform.iam.domain.model.valueobjects.*;
import com.platform.iam.domain.model.valueobjects.RoleStatus;
import com.platform.iam.domain.repository.RoleRepository;
import com.platform.iam.infrastructure.persistence.entity.RoleEntity;
import com.platform.iam.infrastructure.persistence.entity.UserEntity;
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
    private final com.platform.iam.infrastructure.persistence.repository.SpringDataUserRepository jpaUserRepository;
    private final RoleEntityMapper mapper;

    public RoleRepositoryImpl(SpringDataRoleRepository jpaRepository,
                              com.platform.iam.infrastructure.persistence.repository.SpringDataUserRepository jpaUserRepository,
                              RoleEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.jpaUserRepository = jpaUserRepository;
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
        
        // status 必填语义: 控制器未传时默认 ACTIVE(原 SQL "status IS NULL" 分支已删, 见 D-04 v2)
        RoleStatus statusEnum = status != null ? RoleStatus.valueOf(status.toUpperCase()) : RoleStatus.ACTIVE;
        Page<RoleEntity> result;
        if (keyword == null || keyword.isBlank()) {
            result = jpaRepository.findWithFiltersNoKeyword(tenantId.getValue().toString(), statusEnum.name(), pageable);
        } else {
            result = jpaRepository.findWithFiltersKeyword(tenantId.getValue().toString(), statusEnum.name(), keyword, pageable);
        }
        return result.getContent().stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public long countByTenantId(TenantId tenantId, String status, String keyword) {
        RoleStatus statusEnum = status != null ? RoleStatus.valueOf(status.toUpperCase()) : RoleStatus.ACTIVE;
        if (keyword == null || keyword.isBlank()) {
            return jpaRepository.countWithFiltersNoKeyword(tenantId.getValue().toString(), statusEnum.name());
        }
        return jpaRepository.countWithFiltersKeyword(tenantId.getValue().toString(), statusEnum.name(), keyword);
    }

    @Override
    public List<Role> findByUserId(UserId userId) {
        // D-03 修复: 原 native SQL unnest(u.role_ids) 为 PG 专有, TiDB 不支持致登录 403。
        // 改为 ANSI 两步: 先读用户的 role_ids(JSON 列, 实体转换器已解析为 UUID[]), 再按 id 查角色。
        UserEntity user = jpaUserRepository.findById(userId.getValue()).orElse(null);
        if (user == null || user.getRoleIds() == null || user.getRoleIds().length == 0) {
            return List.of();
        }
        List<String> roleIds = java.util.Arrays.stream(user.getRoleIds())
            .map(UUID::toString).collect(java.util.stream.Collectors.toList());
        return jpaRepository.findByIds(roleIds).stream()
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