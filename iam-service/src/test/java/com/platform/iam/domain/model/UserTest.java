package com.platform.iam.domain.model;

import com.platform.iam.domain.event.AccountLockedEvent;
import com.platform.iam.domain.event.DomainEvent;
import com.platform.iam.domain.event.PasswordChangedEvent;
import com.platform.iam.domain.event.RoleAssignedEvent;
import com.platform.iam.domain.event.RoleRevokedEvent;
import com.platform.iam.domain.event.UserCreatedEvent;
import com.platform.iam.domain.event.UserLoggedInEvent;
import com.platform.iam.domain.event.UserStatusChangedEvent;
import com.platform.iam.domain.model.valueobjects.RoleId;
import com.platform.iam.domain.model.valueobjects.TenantId;
import com.platform.iam.domain.model.valueobjects.UserId;
import com.platform.iam.domain.model.valueobjects.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("User 聚合根测试")
class UserTest {

    private static final TenantId TENANT = TenantId.generate();

    private User pendingUser() {
        return User.create(TENANT, "alice_01", "Alice@Example.com", "13800000000", "hashed-pwd", "system");
    }

    private User activeUser() {
        User user = pendingUser();
        user.activate("admin");
        user.clearDomainEvents();
        return user;
    }

    @Nested
    @DisplayName("创建用户")
    class Create {

        @Test
        @DisplayName("应创建待激活态用户并规范邮箱")
        void shouldCreatePendingUser() {
            User user = User.create(TENANT, "bob-2024", "Bob@Example.com", " 13900000000 ",
                    "hashed", "system");

            assertThat(user.getId()).isNotNull();
            assertThat(user.getTenantId()).isEqualTo(TENANT);
            assertThat(user.getUsername()).isEqualTo("bob-2024");
            assertThat(user.getEmail()).isEqualTo("bob@example.com");
            assertThat(user.getPhone()).isEqualTo("13900000000");
            assertThat(user.getPasswordHash()).isEqualTo("hashed");
            assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING);
            assertThat(user.isActive()).isFalse();
            assertThat(user.getFailedLoginAttempts()).isZero();
            assertThat(user.getCreatedAt()).isNotNull();
            assertThat(user.getUpdatedAt()).isEqualTo(user.getCreatedAt());
        }

        @Test
        @DisplayName("应发布 UserCreatedEvent")
        void shouldPublishCreatedEvent() {
            User user = pendingUser();

            List<DomainEvent> events = user.getDomainEvents();
            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(UserCreatedEvent.class);
            UserCreatedEvent event = (UserCreatedEvent) events.get(0);
            assertThat(event.getUserId()).isEqualTo(user.getId());
            assertThat(event.getUsername()).isEqualTo("alice_01");
        }

        @Test
        @DisplayName("用户名为空应抛出异常")
        void shouldThrowWhenUsernameBlank() {
            assertThatThrownBy(() -> User.create(TENANT, "  ", "a@b.com", null, "h", "system"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("用户名不能为空");
        }

        @Test
        @DisplayName("用户名过短应抛出异常")
        void shouldThrowWhenUsernameTooShort() {
            assertThatThrownBy(() -> User.create(TENANT, "ab", "a@b.com", null, "h", "system"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("用户名长度必须在3-50字符之间");
        }

        @Test
        @DisplayName("用户名含非法字符应抛出异常")
        void shouldThrowWhenUsernameIllegalChar() {
            assertThatThrownBy(() -> User.create(TENANT, "alice 01", "a@b.com", null, "h", "system"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("用户名只能包含字母、数字、下划线和连字符");
        }

        @Test
        @DisplayName("邮箱格式非法应抛出异常")
        void shouldThrowWhenEmailInvalid() {
            assertThatThrownBy(() -> User.create(TENANT, "alice_01", "not-an-email", null, "h", "system"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("邮箱格式无效");
        }

        @Test
        @DisplayName("密码哈希为空应抛出异常")
        void shouldThrowWhenPasswordHashNull() {
            assertThatThrownBy(() -> User.create(TENANT, "alice_01", "a@b.com", null, null, "system"))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("密码哈希不能为空");
        }
    }

    @Nested
    @DisplayName("状态流转")
    class StatusTransition {

        @Test
        @DisplayName("待激活态可激活")
        void shouldActivateFromPending() {
            User user = pendingUser();

            user.activate("admin");

            assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(user.isActive()).isTrue();
            assertThat(user.getDomainEvents()).hasSize(2); // Created + StatusChanged
            assertThat(user.getDomainEvents().get(1)).isInstanceOf(UserStatusChangedEvent.class);
        }

        @Test
        @DisplayName("激活态可暂停")
        void shouldSuspendFromActive() {
            User user = activeUser();

            user.suspend("违规操作", "admin");

            assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
            assertThat(user.isActive()).isFalse();
            assertThat(user.getDomainEvents()).hasSize(1);
        }

        @Test
        @DisplayName("暂停态可恢复并清零失败次数")
        void shouldRestoreFromSuspended() {
            User user = activeUser();
            user.suspend("违规", "admin");
            user.clearDomainEvents();

            user.restore("admin");

            assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(user.getFailedLoginAttempts()).isZero();
            assertThat(user.getDomainEvents()).hasSize(1);
        }

        @Test
        @DisplayName("激活态可过期")
        void shouldExpireFromActive() {
            User user = activeUser();

            user.expire("admin");

            assertThat(user.getStatus()).isEqualTo(UserStatus.EXPIRED);
        }

        @Test
        @DisplayName("激活态可锁定并记录原因")
        void shouldLockFromActive() {
            User user = activeUser();

            user.lock("涉嫌违规", "admin");

            assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
            assertThat(user.getLockedAt()).isNotNull();
            assertThat(user.getLockedReason()).isEqualTo("涉嫌违规");
            assertThat(user.getDomainEvents()).hasSize(1);
            assertThat(user.getDomainEvents().get(0)).isInstanceOf(AccountLockedEvent.class);
        }

        @Test
        @DisplayName("非法流转应抛出异常")
        void shouldThrowOnIllegalTransition() {
            User user = activeUser();
            user.suspend("违规", "admin");

            assertThatThrownBy(() -> user.expire("admin"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("用户状态不允许从 暂停 流转到 过期");
        }
    }

    @Nested
    @DisplayName("删除与恢复")
    class DeleteAndRestore {

        @Test
        @DisplayName("软删除应标记删除状态")
        void shouldSoftDelete() {
            User user = activeUser();

            user.delete("admin", "不再使用");

            assertThat(user.getStatus()).isEqualTo(UserStatus.DELETED);
            assertThat(user.isDeleted()).isTrue();
            assertThat(user.getDeletedAt()).isNotNull();
            assertThat(user.getDeleteReason()).isEqualTo("不再使用");
        }

        @Test
        @DisplayName("重复删除应抛出异常")
        void shouldThrowWhenDeleteTwice() {
            User user = activeUser();
            user.delete("admin", "第一次");

            assertThatThrownBy(() -> user.delete("admin", "第二次"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("用户已被删除");
        }

        @Test
        @DisplayName("已删除态可恢复")
        void shouldRestoreFromDeleted() {
            User user = activeUser();
            user.delete("admin", "误删");
            user.clearDomainEvents();

            user.restore("admin");

            assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(user.isDeleted()).isFalse();
        }
    }

    @Nested
    @DisplayName("信息与密码")
    class InfoAndPassword {

        @Test
        @DisplayName("更新基本信息")
        void shouldUpdateInfo() {
            User user = activeUser();

            user.updateInfo("New@Example.com", "13911112222", "admin");

            assertThat(user.getEmail()).isEqualTo("new@example.com");
            assertThat(user.getPhone()).isEqualTo("13911112222");
            assertThat(user.getDomainEvents()).hasSize(1);
        }

        @Test
        @DisplayName("待激活态允许修改用户名")
        void shouldUpdateUsernameWhenPending() {
            User user = pendingUser();

            user.updateUsername("alice_02", "admin");

            assertThat(user.getUsername()).isEqualTo("alice_02");
        }

        @Test
        @DisplayName("非待激活态禁止修改用户名")
        void shouldThrowWhenUpdateUsernameNotPending() {
            User user = activeUser();

            assertThatThrownBy(() -> user.updateUsername("alice_02", "admin"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("仅待激活态用户允许修改用户名");
        }

        @Test
        @DisplayName("修改密码应更新哈希并发布事件")
        void shouldChangePassword() {
            User user = activeUser();

            user.changePassword("brand-new-hash", "admin", true);

            assertThat(user.getPasswordHash()).isEqualTo("brand-new-hash");
            assertThat(user.getDomainEvents()).hasSize(1);
            assertThat(user.getDomainEvents().get(0)).isInstanceOf(PasswordChangedEvent.class);
        }
    }

    @Nested
    @DisplayName("登录行为")
    class LoginBehaviour {

        @Test
        @DisplayName("登录成功应重置失败次数并记录时间")
        void shouldRecordLoginSuccess() {
            User user = activeUser();
            user.recordLoginFailure("1.1.1.1", "密码错误");
            user.clearDomainEvents();

            user.recordLoginSuccess("1.1.1.1", "UA", "fp");

            assertThat(user.getFailedLoginAttempts()).isZero();
            assertThat(user.getLastLoginAt()).isNotNull();
            assertThat(user.getDomainEvents()).hasSize(1);
            assertThat(user.getDomainEvents().get(0)).isInstanceOf(UserLoggedInEvent.class);
        }

        @Test
        @DisplayName("连续失败5次自动锁定")
        void shouldAutoLockAfterFiveFailures() {
            User user = activeUser();

            for (int i = 0; i < 5; i++) {
                user.recordLoginFailure("1.1.1.1", "密码错误");
            }

            assertThat(user.getFailedLoginAttempts()).isEqualTo(5);
            assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
            assertThat(user.canLogin()).isFalse();
        }

        @Test
        @DisplayName("激活态且失败次数不足可登录")
        void shouldAllowLoginWhenActive() {
            User user = activeUser();

            assertThat(user.canLogin()).isTrue();

            user.recordLoginFailure("1.1.1.1", "密码错误");

            assertThat(user.canLogin()).isTrue();
        }
    }

    @Nested
    @DisplayName("角色分配")
    class RoleAssignment {

        @Test
        @DisplayName("分配与撤销角色")
        void shouldAssignAndRevokeRole() {
            User user = activeUser();
            RoleId roleId = RoleId.generate();

            user.assignRole(roleId, "admin");
            assertThat(user.hasRole(roleId)).isTrue();
            assertThat(user.getDomainEvents().get(0)).isInstanceOf(RoleAssignedEvent.class);

            user.revokeRole(roleId, "admin");
            assertThat(user.hasRole(roleId)).isFalse();
            assertThat(user.getDomainEvents().get(1)).isInstanceOf(RoleRevokedEvent.class);
        }

        @Test
        @DisplayName("重复分配同一角色不产生新事件")
        void shouldNotDuplicateRoleAssignment() {
            User user = activeUser();
            RoleId roleId = RoleId.generate();
            user.assignRole(roleId, "admin");
            user.clearDomainEvents();

            user.assignRole(roleId, "admin");

            assertThat(user.getDomainEvents()).isEmpty();
        }
    }

    @Nested
    @DisplayName("重建与相等性")
    class ReconstructAndEquality {

        @Test
        @DisplayName("从持久化重建应保持全部状态")
        void shouldReconstruct() {
            UserId id = UserId.generate();
            Instant createdAt = Instant.now().minusSeconds(3600);
            Instant updatedAt = Instant.now().minusSeconds(1800);
            RoleId roleId = RoleId.generate();

            User user = User.reconstruct(id, TENANT, "alice_01", "a@b.com", "138", "hash",
                    UserStatus.ACTIVE, Set.of(roleId), createdAt, 2, null, null,
                    createdAt, updatedAt, null, null, null);

            assertThat(user.getId()).isEqualTo(id);
            assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(user.getPasswordHash()).isEqualTo("hash");
            assertThat(user.hasRole(roleId)).isTrue();
            assertThat(user.getFailedLoginAttempts()).isEqualTo(2);
            assertThat(user.getCreatedAt()).isEqualTo(createdAt);
            assertThat(user.getDomainEvents()).isEmpty();
        }

        @Test
        @DisplayName("相同ID用户相等")
        void shouldBeEqualWithSameId() {
            UserId id = UserId.generate();
            User u1 = User.reconstruct(id, TENANT, "alice_01", "a@b.com", null, "h", UserStatus.ACTIVE,
                    Set.of(), null, 0, null, null, Instant.now(), Instant.now(), null, null, null);
            User u2 = User.reconstruct(id, TENANT, "other_99", "c@d.com", null, "h2", UserStatus.PENDING,
                    Set.of(), null, 0, null, null, Instant.now(), Instant.now(), null, null, null);

            assertThat(u1).isEqualTo(u2).hasSameHashCodeAs(u2);
        }

        @Test
        @DisplayName("不同ID用户不相等")
        void shouldNotBeEqualWithDifferentId() {
            assertThat(pendingUser()).isNotEqualTo(pendingUser());
        }
    }
}
