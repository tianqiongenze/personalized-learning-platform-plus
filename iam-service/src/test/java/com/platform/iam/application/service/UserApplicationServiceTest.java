package com.platform.iam.application.service;

import com.platform.iam.application.command.ActivateUserCommand;
import com.platform.iam.application.command.AssignRoleCommand;
import com.platform.iam.application.command.AssignRolesCommand;
import com.platform.iam.application.command.ChangePasswordCommand;
import com.platform.iam.application.command.CreateUserCommand;
import com.platform.iam.application.command.DeleteUserCommand;
import com.platform.iam.application.command.ExpireUserCommand;
import com.platform.iam.application.command.LockUserCommand;
import com.platform.iam.application.command.ResetPasswordCommand;
import com.platform.iam.application.command.RestoreUserCommand;
import com.platform.iam.application.command.RevokeRoleCommand;
import com.platform.iam.application.command.SuspendUserCommand;
import com.platform.iam.application.command.UpdateUserInfoCommand;
import com.platform.iam.application.command.UpdateUsernameCommand;
import com.platform.iam.application.query.PageResult;
import com.platform.iam.application.query.UserDetailDto;
import com.platform.iam.application.query.UserListDto;
import com.platform.iam.application.query.UserPageQuery;
import com.platform.iam.application.query.UserQueryService;
import com.platform.iam.application.query.UserStatisticsDto;
import com.platform.iam.application.service.exceptions.EmailAlreadyExistsException;
import com.platform.iam.application.service.exceptions.InvalidPasswordException;
import com.platform.iam.application.service.exceptions.PhoneAlreadyExistsException;
import com.platform.iam.application.service.exceptions.RoleNotFoundException;
import com.platform.iam.application.service.exceptions.UserNotFoundException;
import com.platform.iam.application.service.exceptions.UsernameAlreadyExistsException;
import com.platform.iam.domain.model.Role;
import com.platform.iam.domain.model.User;
import com.platform.iam.domain.model.valueobjects.RoleId;
import com.platform.iam.domain.model.valueobjects.TenantId;
import com.platform.iam.domain.model.valueobjects.UserId;
import com.platform.iam.domain.model.valueobjects.UserStatus;
import com.platform.iam.domain.repository.RoleRepository;
import com.platform.iam.domain.repository.UserRepository;
import com.platform.iam.infrastructure.event.DomainEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserApplicationService 测试")
class UserApplicationServiceTest {

    private static final TenantId TENANT = TenantId.generate();

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserQueryService userQueryService;

    @Mock
    private DomainEventPublisher domainEventPublisher;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserApplicationService service;

    private User userWithStatus(UserStatus status) {
        User user = User.create(TENANT, "alice_01", "alice@example.com", null, "hashed", "system");
        user.clearDomainEvents();
        switch (status) {
            case ACTIVE -> user.activate("admin");
            case SUSPENDED -> {
                user.activate("admin");
                user.suspend("违规", "admin");
            }
            case EXPIRED -> {
                user.activate("admin");
                user.expire("admin");
            }
            case LOCKED -> {
                user.activate("admin");
                user.lock("违规", "admin");
            }
            case DELETED -> {
                user.activate("admin");
                user.delete("admin", "原因");
            }
            default -> {
                // PENDING 保持原状
            }
        }
        user.clearDomainEvents();
        return user;
    }

    @Nested
    @DisplayName("注册用户")
    class CreateUser {

        @Test
        @DisplayName("应成功注册用户")
        void shouldCreateUser() {
            CreateUserCommand cmd = new CreateUserCommand(TENANT, "alice_01", "alice@example.com",
                    null, "password123", null);
            when(userRepository.existsByUsername(TENANT, "alice_01")).thenReturn(false);
            when(userRepository.existsByEmail(TENANT, "alice@example.com")).thenReturn(false);
            when(passwordEncoder.encode("password123")).thenReturn("hashed");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            UserId id = service.createUser(cmd);

            assertThat(id).isNotNull();
            verify(userRepository).save(any(User.class));
            verify(domainEventPublisher).publishAll(anyList());
        }

        @Test
        @DisplayName("用户名已存在应抛出异常")
        void shouldThrowWhenUsernameExists() {
            CreateUserCommand cmd = new CreateUserCommand(TENANT, "alice_01", "alice@example.com",
                    null, "password123", null);
            when(userRepository.existsByUsername(TENANT, "alice_01")).thenReturn(true);

            assertThatThrownBy(() -> service.createUser(cmd))
                    .isInstanceOf(UsernameAlreadyExistsException.class)
                    .hasMessageContaining("alice_01");
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("邮箱已存在应抛出异常")
        void shouldThrowWhenEmailExists() {
            CreateUserCommand cmd = new CreateUserCommand(TENANT, "alice_01", "alice@example.com",
                    null, "password123", null);
            when(userRepository.existsByUsername(TENANT, "alice_01")).thenReturn(false);
            when(userRepository.existsByEmail(TENANT, "alice@example.com")).thenReturn(true);

            assertThatThrownBy(() -> service.createUser(cmd))
                    .isInstanceOf(EmailAlreadyExistsException.class);
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("手机号已存在应抛出异常")
        void shouldThrowWhenPhoneExists() {
            CreateUserCommand cmd = new CreateUserCommand(TENANT, "alice_01", "alice@example.com",
                    "13800000000", "password123", null);
            when(userRepository.existsByUsername(TENANT, "alice_01")).thenReturn(false);
            when(userRepository.existsByEmail(TENANT, "alice@example.com")).thenReturn(false);
            when(userRepository.existsByPhone(TENANT, "13800000000")).thenReturn(true);

            assertThatThrownBy(() -> service.createUser(cmd))
                    .isInstanceOf(PhoneAlreadyExistsException.class);
        }

        @Test
        @DisplayName("指定角色不存在应抛出异常")
        void shouldThrowWhenRoleNotFound() {
            RoleId roleId = RoleId.generate();
            CreateUserCommand cmd = new CreateUserCommand(TENANT, "alice_01", "alice@example.com",
                    null, "password123", Set.of(roleId));
            when(userRepository.existsByUsername(TENANT, "alice_01")).thenReturn(false);
            when(userRepository.existsByEmail(TENANT, "alice@example.com")).thenReturn(false);
            when(roleRepository.findById(roleId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createUser(cmd))
                    .isInstanceOf(RoleNotFoundException.class);
        }

        @Test
        @DisplayName("注册时分配角色")
        void shouldAssignRolesOnCreate() {
            RoleId roleId = RoleId.generate();
            Role role = Role.create(TENANT, "teacher", "教师", null, false, "admin");
            CreateUserCommand cmd = new CreateUserCommand(TENANT, "alice_01", "alice@example.com",
                    null, "password123", Set.of(roleId));
            when(userRepository.existsByUsername(TENANT, "alice_01")).thenReturn(false);
            when(userRepository.existsByEmail(TENANT, "alice@example.com")).thenReturn(false);
            when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
            when(passwordEncoder.encode("password123")).thenReturn("hashed");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            UserId id = service.createUser(cmd);

            assertThat(id).isNotNull();
            verify(userRepository).save(any(User.class));
        }
    }

    @Nested
    @DisplayName("信息维护")
    class InfoMaintenance {

        @Test
        @DisplayName("应更新用户基本信息")
        void shouldUpdateUserInfo() {
            User user = userWithStatus(UserStatus.ACTIVE);
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
            when(userRepository.existsByEmail(user.getTenantId(), "new@example.com")).thenReturn(false);
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            service.updateUserInfo(new UpdateUserInfoCommand(user.getId(), "new@example.com", null, "admin"));

            assertThat(user.getEmail()).isEqualTo("new@example.com");
            verify(domainEventPublisher).publishAll(anyList());
        }

        @Test
        @DisplayName("邮箱冲突应抛出异常")
        void shouldThrowWhenUpdateEmailConflict() {
            User user = userWithStatus(UserStatus.ACTIVE);
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
            when(userRepository.existsByEmail(user.getTenantId(), "dup@example.com")).thenReturn(true);

            assertThatThrownBy(() -> service.updateUserInfo(
                    new UpdateUserInfoCommand(user.getId(), "dup@example.com", null, "admin")))
                    .isInstanceOf(EmailAlreadyExistsException.class);
        }

        @Test
        @DisplayName("用户不存在应抛出异常")
        void shouldThrowWhenUserNotFound() {
            UserId id = UserId.generate();
            when(userRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.activateUser(new ActivateUserCommand(id, "admin")))
                    .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        @DisplayName("应更新待激活用户用户名")
        void shouldUpdateUsername() {
            User user = userWithStatus(UserStatus.PENDING);
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
            when(userRepository.existsByUsername(user.getTenantId(), "alice_02")).thenReturn(false);
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            service.updateUsername(new UpdateUsernameCommand(user.getId(), "alice_02", "admin"));

            assertThat(user.getUsername()).isEqualTo("alice_02");
        }

        @Test
        @DisplayName("用户名冲突应抛出异常")
        void shouldThrowWhenUsernameConflict() {
            User user = userWithStatus(UserStatus.PENDING);
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
            when(userRepository.existsByUsername(user.getTenantId(), "taken")).thenReturn(true);

            assertThatThrownBy(() -> service.updateUsername(
                    new UpdateUsernameCommand(user.getId(), "taken", "admin")))
                    .isInstanceOf(UsernameAlreadyExistsException.class);
        }
    }

    @Nested
    @DisplayName("密码操作")
    class PasswordOperations {

        @Test
        @DisplayName("原密码正确应修改密码")
        void shouldChangePassword() {
            User user = userWithStatus(UserStatus.ACTIVE);
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("oldPass123", "hashed")).thenReturn(true);
            when(passwordEncoder.encode("newPass123")).thenReturn("new-hash");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            service.changePassword(new ChangePasswordCommand(user.getId(), "oldPass123", "newPass123", "self"));

            assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        }

        @Test
        @DisplayName("原密码错误应抛出异常")
        void shouldThrowWhenOldPasswordWrong() {
            User user = userWithStatus(UserStatus.ACTIVE);
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrongPass", "hashed")).thenReturn(false);

            assertThatThrownBy(() -> service.changePassword(
                    new ChangePasswordCommand(user.getId(), "wrongPass", "newPass123", "self")))
                    .isInstanceOf(InvalidPasswordException.class)
                    .hasMessageContaining("原密码错误");
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("管理员重置密码")
        void shouldResetPassword() {
            User user = userWithStatus(UserStatus.ACTIVE);
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
            when(passwordEncoder.encode("resetPass123")).thenReturn("reset-hash");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            service.resetPassword(new ResetPasswordCommand(user.getId(), "resetPass123", "admin"));

            assertThat(user.getPasswordHash()).isEqualTo("reset-hash");
        }
    }

    @Nested
    @DisplayName("状态流转命令")
    class StatusCommands {

        private void stubSave() {
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        }

        @Test
        @DisplayName("激活用户")
        void shouldActivate() {
            User user = userWithStatus(UserStatus.PENDING);
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
            stubSave();

            service.activateUser(new ActivateUserCommand(user.getId(), "admin"));

            assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        @DisplayName("暂停用户")
        void shouldSuspend() {
            User user = userWithStatus(UserStatus.ACTIVE);
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
            stubSave();

            service.suspendUser(new SuspendUserCommand(user.getId(), "违规", "admin"));

            assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
        }

        @Test
        @DisplayName("恢复用户")
        void shouldRestore() {
            User user = userWithStatus(UserStatus.SUSPENDED);
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
            stubSave();

            service.restoreUser(new RestoreUserCommand(user.getId(), "admin"));

            assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        @DisplayName("过期用户")
        void shouldExpire() {
            User user = userWithStatus(UserStatus.ACTIVE);
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
            stubSave();

            service.expireUser(new ExpireUserCommand(user.getId(), "admin"));

            assertThat(user.getStatus()).isEqualTo(UserStatus.EXPIRED);
        }

        @Test
        @DisplayName("锁定用户")
        void shouldLock() {
            User user = userWithStatus(UserStatus.ACTIVE);
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
            stubSave();

            service.lockUser(new LockUserCommand(user.getId(), "违规", "admin"));

            assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
        }

        @Test
        @DisplayName("删除用户")
        void shouldDelete() {
            User user = userWithStatus(UserStatus.ACTIVE);
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
            stubSave();

            service.deleteUser(new DeleteUserCommand(user.getId(), "admin", "不再使用"));

            assertThat(user.getStatus()).isEqualTo(UserStatus.DELETED);
        }
    }

    @Nested
    @DisplayName("角色分配命令")
    class RoleCommands {

        @Test
        @DisplayName("应给用户分配角色")
        void shouldAssignRole() {
            User user = userWithStatus(UserStatus.ACTIVE);
            RoleId roleId = RoleId.generate();
            Role role = Role.create(TENANT, "teacher", "教师", null, false, "admin");
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
            when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            service.assignRole(new AssignRoleCommand(user.getId(), roleId, "admin"));

            assertThat(user.hasRole(roleId)).isTrue();
        }

        @Test
        @DisplayName("跨租户分配角色应抛出异常")
        void shouldThrowWhenTenantMismatch() {
            User user = userWithStatus(UserStatus.ACTIVE);
            RoleId roleId = RoleId.generate();
            Role role = Role.create(TenantId.generate(), "teacher", "教师", null, false, "admin");
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
            when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));

            assertThatThrownBy(() -> service.assignRole(new AssignRoleCommand(user.getId(), roleId, "admin")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("用户和角色不属于同一租户");
        }

        @Test
        @DisplayName("应撤销用户角色")
        void shouldRevokeRole() {
            User user = userWithStatus(UserStatus.ACTIVE);
            RoleId roleId = RoleId.generate();
            user.assignRole(roleId, "admin");
            user.clearDomainEvents();
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            service.revokeRole(new RevokeRoleCommand(user.getId(), roleId, "admin"));

            assertThat(user.hasRole(roleId)).isFalse();
        }

        @Test
        @DisplayName("应批量分配角色")
        void shouldAssignRoles() {
            User user = userWithStatus(UserStatus.ACTIVE);
            RoleId roleId1 = RoleId.generate();
            RoleId roleId2 = RoleId.generate();
            Role role1 = Role.create(TENANT, "teacher", "教师", null, false, "admin");
            Role role2 = Role.create(TENANT, "student", "学生", null, false, "admin");
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
            when(roleRepository.findById(roleId1)).thenReturn(Optional.of(role1));
            when(roleRepository.findById(roleId2)).thenReturn(Optional.of(role2));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            service.assignRoles(new AssignRolesCommand(user.getId(), Set.of(roleId1, roleId2), "admin"));

            assertThat(user.hasRole(roleId1)).isTrue();
            assertThat(user.hasRole(roleId2)).isTrue();
        }
    }

    @Nested
    @DisplayName("查询委托")
    class Queries {

        @Test
        @DisplayName("findById 应委托查询服务")
        void shouldDelegateFindById() {
            UserId id = UserId.generate();
            java.time.Instant now = java.time.Instant.now();
            UserDetailDto expected = new UserDetailDto(id.toString(), TENANT.toString(), "alice_01",
                    "alice@example.com", null, "ACTIVE", Set.of(), now, 0, null, null, now, now, null, null, null);
            when(userQueryService.findById(id)).thenReturn(expected);

            assertThat(service.findById(id)).isSameAs(expected);
        }

        @Test
        @DisplayName("findPage 应委托查询服务")
        void shouldDelegateFindPage() {
            UserPageQuery query = new UserPageQuery(TENANT, 0, 20, "ACTIVE", null, "createdAt", "desc");
            PageResult<UserListDto> expected = new PageResult<>(List.of(), 0, 20, 0, 0, true, true);
            when(userQueryService.findPage(query)).thenReturn(expected);

            assertThat(service.findPage(query)).isSameAs(expected);
        }

        @Test
        @DisplayName("statistics 应委托查询服务")
        void shouldDelegateStatistics() {
            UserStatisticsDto expected = new UserStatisticsDto(10, 8, 1, 1, 0, 0, 0);
            when(userQueryService.userStatistics(TENANT)).thenReturn(expected);

            assertThat(service.statistics(TENANT)).isSameAs(expected);
        }

        @Test
        @DisplayName("findByRoleId 应委托查询服务")
        void shouldDelegateFindByRoleId() {
            RoleId roleId = RoleId.generate();
            when(userQueryService.findByRoleId(roleId)).thenReturn(List.of());

            assertThat(service.findByRoleId(roleId)).isEmpty();
        }
    }
}
