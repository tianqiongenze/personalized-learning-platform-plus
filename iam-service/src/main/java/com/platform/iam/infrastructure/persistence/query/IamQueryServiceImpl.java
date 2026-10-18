package com.platform.iam.infrastructure.persistence.query;

import com.platform.iam.application.query.*;
import com.platform.iam.domain.model.valueobjects.*;
import com.platform.iam.infrastructure.persistence.entity.RoleEntity;
import com.platform.iam.infrastructure.persistence.entity.UserEntity;
import com.platform.iam.infrastructure.persistence.repository.SpringDataRoleRepository;
import com.platform.iam.infrastructure.persistence.repository.SpringDataUserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * IAM 查询服务实现(CQRS 读侧) — 基于 Spring Data 分页/投影, 不加载聚合根。
 * 同时实现 UserQueryService 与 RoleQueryService(同一数据面, 一个 Bean)。
 */
@Service
@Transactional(readOnly = true)
public class IamQueryServiceImpl implements UserQueryService, RoleQueryService {

    private final SpringDataUserRepository userRepository;
    private final SpringDataRoleRepository roleRepository;

    public IamQueryServiceImpl(SpringDataUserRepository userRepository,
                               SpringDataRoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    // ================= UserQueryService =================

    @Override
    public UserDetailDto findById(UserId id) {
        UserEntity u = userRepository.findById(id.getValue())
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + id));
        return toDetail(u);
    }

    @Override
    public UserDetailDto findByUsername(TenantId tenantId, String username) {
        UserEntity u = userRepository.findByTenantIdAndUsername(tenantId.getValue(), username)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + username));
        return toDetail(u);
    }

    @Override
    public UserDetailDto findByEmail(TenantId tenantId, String email) {
        UserEntity u = userRepository.findByTenantIdAndEmail(tenantId.getValue(), email)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + email));
        return toDetail(u);
    }

    @Override
    public PageResult<UserListDto> findPage(UserPageQuery query) {
        Page<UserEntity> page = userRepository.findWithFilters(query.tenantId().getValue(),
                null, query.keyword(),
                PageRequest.of(Math.max(query.page(), 0), Math.max(query.size(), 1)));
        List<UserListDto> items = page.getContent().stream()
                .map(IamQueryServiceImpl::toList).collect(Collectors.toList());
        return new PageResult<>(items, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
    }

    @Override
    public List<UserSimpleDto> findByRoleId(RoleId roleId) {
        return userRepository.findByRoleIdsContaining(roleId.getValue()).stream()
                .map(u -> new UserSimpleDto(u.getId().toString(), u.getUsername(), u.getEmail()))
                .collect(Collectors.toList());
    }

    @Override
    public UserStatisticsDto userStatistics(TenantId tenantId) {
        long total = userRepository.countByTenantId(tenantId.getValue());
        long active = userRepository.countByTenantIdAndStatus(tenantId.getValue(), UserStatus.ACTIVE);
        long suspended = userRepository.countByTenantIdAndStatus(tenantId.getValue(), UserStatus.SUSPENDED);
        long locked = userRepository.countByTenantIdAndStatus(tenantId.getValue(), UserStatus.LOCKED);
        long expired = userRepository.countByTenantIdAndStatus(tenantId.getValue(), UserStatus.EXPIRED);
        long deleted = userRepository.countByTenantIdAndStatus(tenantId.getValue(), UserStatus.DELETED);
        return new UserStatisticsDto(total, active, 0, suspended, locked, expired, deleted);
    }
    // ================= RoleQueryService =================

    @Override
    public RoleDetailDto findById(RoleId id) {
        RoleEntity r = roleRepository.findById(id.getValue())
                .orElseThrow(() -> new IllegalArgumentException("角色不存在: " + id));
        return toRoleDetail(r);
    }

    @Override
    public RoleDetailDto findByCode(TenantId tenantId, String code) {
        RoleEntity r = roleRepository.findByTenantIdAndCode(tenantId.getValue(), code)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在: " + code));
        return toRoleDetail(r);
    }

    @Override
    public PageResult<RoleListDto> findPage(RolePageQuery query) {
        Page<RoleEntity> page = roleRepository.findWithFilters(query.tenantId().getValue(),
                null, query.keyword(), query.systemOnly(),
                PageRequest.of(Math.max(query.page(), 0), Math.max(query.size(), 1)));
        List<RoleListDto> items = page.getContent().stream()
                .map(IamQueryServiceImpl::toRoleList).collect(Collectors.toList());
        return new PageResult<>(items, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
    }

    @Override
    public List<RoleSimpleDto> findByUserId(UserId userId) {
        return roleRepository.findByUserId(userId.getValue()).stream()
                .map(r -> new RoleSimpleDto(r.getId().toString(), r.getCode(), r.getName()))
                .collect(Collectors.toList());
    }

    @Override
    public List<RoleSimpleDto> findSystemRoles() {
        return roleRepository.findByIsSystemTrue().stream()
                .map(r -> new RoleSimpleDto(r.getId().toString(), r.getCode(), r.getName()))
                .collect(Collectors.toList());
    }

    @Override
    public RoleStatisticsDto statistics(TenantId tenantId) {
        long total = roleRepository.countByTenantId(tenantId.getValue());
        long active = roleRepository.countByTenantIdAndStatus(tenantId.getValue(), RoleStatus.ACTIVE);
        long inactive = total - active;
        return new RoleStatisticsDto(total, active, inactive, 0, roleRepository.countByTenantIdAndIsSystem(tenantId.getValue(), true));
    }

    // ================= 映射 =================

    private static UserDetailDto toDetail(UserEntity u) {
        List<String> roleCodes = u.getRoleIds() == null ? List.of()
                : java.util.Arrays.stream(u.getRoleIds()).map(UUID::toString).collect(Collectors.toList());
        return new UserDetailDto(u.getId().toString(), u.getTenantId().toString(), u.getUsername(),
                u.getEmail(), u.getPhone(), u.getStatus().name(),
                new java.util.HashSet<>(roleCodes), u.getLastLoginAt(),
                u.getFailedLoginAttempts(), u.getLockedAt(), u.getLockedReason(),
                u.getCreatedAt(), u.getUpdatedAt(), u.getDeletedAt(), u.getDeletedBy(), u.getDeleteReason());
    }

    private static UserListDto toList(UserEntity u) {
        return new UserListDto(u.getId().toString(), u.getTenantId().toString(), u.getUsername(),
                u.getEmail(), u.getPhone(), u.getStatus().name(), u.getLastLoginAt(),
                u.getFailedLoginAttempts(), u.getCreatedAt());
    }

    private static RoleDetailDto toRoleDetail(RoleEntity r) {
        List<PermissionDto> perms = new ArrayList<>();
        try {
            for (Permission p : r.getPermissionSet()) {
                perms.add(new PermissionDto(
                        p.getResourceType() + ":" + p.getAction(),
                        p.getResourceType() == null ? null : p.getResourceType().name(),
                        p.getAction() == null ? null : p.getAction().name(),
                        p.getResourceId()));
            }
        } catch (Exception ignored) { }
        return new RoleDetailDto(r.getId().toString(), r.getTenantId().toString(), r.getCode(),
                r.getName(), r.getDescription(), r.getStatus().name(), r.isSystem(),
                new java.util.HashSet<>(perms), r.getCreatedAt(), r.getUpdatedAt(),
                r.getDeletedAt(), r.getDeletedBy(), r.getDeleteReason());
    }

    private static RoleListDto toRoleList(RoleEntity r) {
        int permCount = 0;
        try {
            permCount = r.getPermissionSet().size();
        } catch (Exception ignored) { }
        return new RoleListDto(r.getId().toString(), r.getTenantId().toString(), r.getCode(),
                r.getName(), r.getDescription(), r.getStatus().name(), r.isSystem(),
                permCount, r.getCreatedAt());
    }
}
