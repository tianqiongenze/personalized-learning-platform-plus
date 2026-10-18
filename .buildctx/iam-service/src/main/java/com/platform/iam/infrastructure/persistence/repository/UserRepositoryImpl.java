package com.platform.iam.infrastructure.persistence.repository;

import com.platform.iam.domain.model.User;
import com.platform.iam.domain.model.valueobjects.*;
import com.platform.iam.domain.model.valueobjects.UserStatus;
import com.platform.iam.domain.repository.UserRepository;
import com.platform.iam.infrastructure.persistence.entity.UserEntity;
import com.platform.iam.infrastructure.persistence.mapper.UserEntityMapper;
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
 * 用户仓储实现
 */
@Repository
public class UserRepositoryImpl implements UserRepository {

    private final SpringDataUserRepository jpaRepository;
    private final UserEntityMapper mapper;

    public UserRepositoryImpl(SpringDataUserRepository jpaRepository, UserEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public User save(User user) {
        UserEntity entity = mapper.toEntity(user);
        UserEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<User> findById(UserId id) {
        return jpaRepository.findById(id.getValue())
            .map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByUsername(TenantId tenantId, String username) {
        return jpaRepository.findByTenantIdAndUsername(tenantId.getValue(), username)
            .map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByEmail(TenantId tenantId, String email) {
        return jpaRepository.findByTenantIdAndEmail(tenantId.getValue(), email)
            .map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByPhone(TenantId tenantId, String phone) {
        return jpaRepository.findByTenantIdAndPhone(tenantId.getValue(), phone)
            .map(mapper::toDomain);
    }

    @Override
    public boolean existsByUsername(TenantId tenantId, String username) {
        return jpaRepository.existsByTenantIdAndUsername(tenantId.getValue(), username);
    }

    @Override
    public boolean existsByEmail(TenantId tenantId, String email) {
        return jpaRepository.existsByTenantIdAndEmail(tenantId.getValue(), email);
    }

    @Override
    public boolean existsByPhone(TenantId tenantId, String phone) {
        return jpaRepository.existsByTenantIdAndPhone(tenantId.getValue(), phone);
    }

    @Override
    public List<User> findByTenantId(TenantId tenantId, int page, int size, String status, String keyword) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        
        UserStatus statusEnum = status != null ? UserStatus.valueOf(status.toUpperCase()) : null;
        Page<UserEntity> result = jpaRepository.findWithFilters(tenantId.getValue(), statusEnum, keyword, pageable);
        
        return result.getContent().stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public long countByTenantId(TenantId tenantId, String status, String keyword) {
        UserStatus statusEnum = status != null ? UserStatus.valueOf(status.toUpperCase()) : null;
        return jpaRepository.countWithFilters(tenantId.getValue(), statusEnum, keyword);
    }

    @Override
    public List<User> findByRoleId(RoleId roleId) {
        return jpaRepository.findByRoleIdsContaining(roleId.getValue()).stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<User> findByIds(List<UserId> ids) {
        List<UUID> uuids = ids.stream().map(UserId::getValue).collect(Collectors.toList());
        return jpaRepository.findAllById(uuids).stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public void deleteById(UserId id) {
        jpaRepository.deleteById(id.getValue());
    }
}