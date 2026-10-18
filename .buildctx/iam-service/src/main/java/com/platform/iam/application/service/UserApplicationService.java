package com.platform.iam.application.service;

import com.platform.iam.application.command.*;
import com.platform.iam.application.query.*;
import com.platform.iam.domain.event.DomainEvent;
import com.platform.iam.domain.model.User;
import com.platform.iam.domain.model.Role;
import com.platform.iam.domain.model.valueobjects.*;
import com.platform.iam.domain.repository.UserRepository;
import com.platform.iam.domain.repository.RoleRepository;
import com.platform.iam.application.service.exceptions.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import com.platform.iam.infrastructure.event.DomainEventPublisher;

/**
 * 用户应用服务
 */
@Service
@Transactional

public class UserApplicationService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserQueryService userQueryService;
    private final DomainEventPublisher domainEventPublisher;
    private final PasswordEncoder passwordEncoder;

    public UserApplicationService(UserRepository userRepository,
                                  RoleRepository roleRepository,
                                  UserQueryService userQueryService,
                                  DomainEventPublisher domainEventPublisher,
                                  PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userQueryService = userQueryService;
        this.domainEventPublisher = domainEventPublisher;
        this.passwordEncoder = passwordEncoder;
    }

    // ========== 命令处理 ==========

    public UserId createUser(CreateUserCommand cmd) {
        // 检查用户名唯一性
        if (userRepository.existsByUsername(cmd.tenantId(), cmd.username())) {
            throw new UsernameAlreadyExistsException(cmd.username());
        }
        // 检查邮箱唯一性
        if (userRepository.existsByEmail(cmd.tenantId(), cmd.email())) {
            throw new EmailAlreadyExistsException(cmd.email());
        }
        // 检查手机号唯一性
        if (cmd.phone() != null && userRepository.existsByPhone(cmd.tenantId(), cmd.phone())) {
            throw new PhoneAlreadyExistsException(cmd.phone());
        }

        // 验证角色存在
        if (cmd.roleIds() != null && !cmd.roleIds().isEmpty()) {
            for (RoleId roleId : cmd.roleIds()) {
                if (roleRepository.findById(roleId).isEmpty()) {
                    throw new RoleNotFoundException(roleId);
                }
            }
        }

        String passwordHash = passwordEncoder.encode(cmd.password());
        User user = User.create(cmd.tenantId(), cmd.username(), cmd.email(), cmd.phone(),
                                passwordHash, "system");
        if (cmd.roleIds() != null) {
            for (RoleId roleId : cmd.roleIds()) {
                user.assignRole(roleId, "system");
            }
        }

        User saved = userRepository.save(user);
        publishDomainEvents(saved);
        return saved.getId();
    }

    public void updateUserInfo(UpdateUserInfoCommand cmd) {
        User user = getUserOrThrow(cmd.userId());
        
        // 检查邮箱唯一性
        if (!user.getEmail().equals(cmd.email()) && 
            userRepository.existsByEmail(user.getTenantId(), cmd.email())) {
            throw new EmailAlreadyExistsException(cmd.email());
        }
        // 检查手机号唯一性
        if (cmd.phone() != null && !cmd.phone().equals(user.getPhone()) &&
            userRepository.existsByPhone(user.getTenantId(), cmd.phone())) {
            throw new PhoneAlreadyExistsException(cmd.phone());
        }

        user.updateInfo(cmd.email(), cmd.phone(), cmd.updatedBy());
        User saved = userRepository.save(user);
        publishDomainEvents(saved);
    }

    public void updateUsername(UpdateUsernameCommand cmd) {
        User user = getUserOrThrow(cmd.userId());
        
        if (userRepository.existsByUsername(user.getTenantId(), cmd.username())) {
            throw new UsernameAlreadyExistsException(cmd.username());
        }

        user.updateUsername(cmd.username(), cmd.updatedBy());
        User saved = userRepository.save(user);
        publishDomainEvents(saved);
    }

    public void changePassword(ChangePasswordCommand cmd) {
        User user = getUserOrThrow(cmd.userId());
        
        if (!passwordEncoder.matches(cmd.oldPassword(), user.getPasswordHash())) {
            throw new InvalidPasswordException("原密码错误");
        }

        String newPasswordHash = passwordEncoder.encode(cmd.newPassword());
        user.changePassword(newPasswordHash, cmd.changedBy(), true);
        User saved = userRepository.save(user);
        publishDomainEvents(saved);
    }

    public void resetPassword(ResetPasswordCommand cmd) {
        User user = getUserOrThrow(cmd.userId());
        
        String newPasswordHash = passwordEncoder.encode(cmd.newPassword());
        user.changePassword(newPasswordHash, cmd.resetBy(), false);
        User saved = userRepository.save(user);
        publishDomainEvents(saved);
    }

    public void activateUser(ActivateUserCommand cmd) {
        User user = getUserOrThrow(cmd.userId());
        user.activate(cmd.activatedBy());
        User saved = userRepository.save(user);
        publishDomainEvents(saved);
    }

    public void suspendUser(SuspendUserCommand cmd) {
        User user = getUserOrThrow(cmd.userId());
        user.suspend(cmd.reason(), cmd.suspendedBy());
        User saved = userRepository.save(user);
        publishDomainEvents(saved);
    }

    public void restoreUser(RestoreUserCommand cmd) {
        User user = getUserOrThrow(cmd.userId());
        user.restore(cmd.restoredBy());
        User saved = userRepository.save(user);
        publishDomainEvents(saved);
    }

    public void expireUser(ExpireUserCommand cmd) {
        User user = getUserOrThrow(cmd.userId());
        user.expire(cmd.expiredBy());
        User saved = userRepository.save(user);
        publishDomainEvents(saved);
    }

    public void lockUser(LockUserCommand cmd) {
        User user = getUserOrThrow(cmd.userId());
        user.lock(cmd.reason(), cmd.lockedBy());
        User saved = userRepository.save(user);
        publishDomainEvents(saved);
    }

    public void deleteUser(DeleteUserCommand cmd) {
        User user = getUserOrThrow(cmd.userId());
        user.delete(cmd.deletedBy(), cmd.reason());
        User saved = userRepository.save(user);
        publishDomainEvents(saved);
    }

    public void assignRole(AssignRoleCommand cmd) {
        User user = getUserOrThrow(cmd.userId());
        Role role = getRoleOrThrow(cmd.roleId());
        
        // 验证租户一致性
        if (!user.getTenantId().equals(role.getTenantId())) {
            throw new IllegalArgumentException("用户和角色不属于同一租户");
        }

        user.assignRole(cmd.roleId(), cmd.assignedBy());
        User saved = userRepository.save(user);
        publishDomainEvents(saved);
    }

    public void revokeRole(RevokeRoleCommand cmd) {
        User user = getUserOrThrow(cmd.userId());
        user.revokeRole(cmd.roleId(), cmd.revokedBy());
        User saved = userRepository.save(user);
        publishDomainEvents(saved);
    }

    public void assignRoles(AssignRolesCommand cmd) {
        User user = getUserOrThrow(cmd.userId());
        
        for (RoleId roleId : cmd.roleIds()) {
            Role role = getRoleOrThrow(roleId);
            if (!user.getTenantId().equals(role.getTenantId())) {
                throw new IllegalArgumentException("用户和角色不属于同一租户: " + roleId);
            }
            user.assignRole(roleId, cmd.assignedBy());
        }
        
        User saved = userRepository.save(user);
        publishDomainEvents(saved);
    }

    // ========== 查询处理 ==========

    @Transactional(readOnly = true)
    public UserDetailDto findById(UserId id) {
        return userQueryService.findById(id);
    }

    @Transactional(readOnly = true)
    public UserDetailDto findByUsername(TenantId tenantId, String username) {
        return userQueryService.findByUsername(tenantId, username);
    }

    @Transactional(readOnly = true)
    public PageResult<UserListDto> findPage(UserPageQuery query) {
        return userQueryService.findPage(query);
    }

    @Transactional(readOnly = true)
    public List<UserSimpleDto> findByRoleId(RoleId roleId) {
        return userQueryService.findByRoleId(roleId);
    }

    @Transactional(readOnly = true)
    public UserStatisticsDto statistics(TenantId tenantId) {
        return userQueryService.statistics(tenantId);
    }

    // ========== 私有方法 ==========

    private User getUserOrThrow(UserId id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new UserNotFoundException(id));
    }

    private Role getRoleOrThrow(RoleId id) {
        return roleRepository.findById(id)
            .orElseThrow(() -> new RoleNotFoundException(id));
    }

    private void publishDomainEvents(User user) {
        List<DomainEvent> events = user.getDomainEvents();
        if (!events.isEmpty()) {
            domainEventPublisher.publishAll(events);
            user.clearDomainEvents();
        }
    }
}
