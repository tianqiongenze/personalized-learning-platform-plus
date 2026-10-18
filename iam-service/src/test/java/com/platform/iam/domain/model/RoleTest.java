package com.platform.iam.domain.model;

import com.platform.iam.domain.event.DomainEvent;
import com.platform.iam.domain.event.PermissionAddedEvent;
import com.platform.iam.domain.event.PermissionRemovedEvent;
import com.platform.iam.domain.event.RoleCreatedEvent;
import com.platform.iam.domain.event.RoleDeletedEvent;
import com.platform.iam.domain.event.RoleStatusChangedEvent;
import com.platform.iam.domain.model.valueobjects.Permission;
import com.platform.iam.domain.model.valueobjects.RoleId;
import com.platform.iam.domain.model.valueobjects.RoleStatus;
import com.platform.iam.domain.model.valueobjects.TenantId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Role 聚合根测试")
class RoleTest {

    private static final TenantId TENANT = TenantId.generate();

    private static final Permission USER_READ =
            Permission.of(Permission.ResourceType.USER, Permission.Action.READ, null);

    private Role plainRole() {
        return Role.create(TENANT, "teacher", "教师", "教学角色", false, "admin");
    }

    @Nested
    @DisplayName("创建角色")
    class Create {

        @Test
        @DisplayName("应创建激活态普通角色")
        void shouldCreateRole() {
            Role role = Role.create(TENANT, "teacher", "教师", "教学角色", false, "admin");

            assertThat(role.getId()).isNotNull();
            assertThat(role.getTenantId()).isEqualTo(TENANT);
            assertThat(role.getCode()).isEqualTo("TEACHER");
            assertThat(role.getName()).isEqualTo("教师");
            assertThat(role.getDescription()).isEqualTo("教学角色");
            assertThat(role.getStatus()).isEqualTo(RoleStatus.ACTIVE);
            assertThat(role.isSystem()).isFalse();
            assertThat(role.isActive()).isTrue();
            assertThat(role.getDomainEvents()).hasSize(1);
            assertThat(role.getDomainEvents().get(0)).isInstanceOf(RoleCreatedEvent.class);
        }

        @Test
        @DisplayName("编码应被规范化并大写")
        void shouldNormalizeCode() {
            Role role = Role.create(TENANT, "  student_role  ", "学生", null, false, "admin");

            assertThat(role.getCode()).isEqualTo("STUDENT_ROLE");
        }

        @Test
        @DisplayName("编码为空应抛出异常")
        void shouldThrowWhenCodeBlank() {
            assertThatThrownBy(() -> Role.create(TENANT, " ", "教师", null, false, "admin"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("角色编码不能为空");
        }

        @Test
        @DisplayName("编码含非法字符应抛出异常")
        void shouldThrowWhenCodeIllegal() {
            assertThatThrownBy(() -> Role.create(TENANT, "te-acher", "教师", null, false, "admin"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("角色编码必须为2-50位大写字母、数字或下划线");
        }

        @Test
        @DisplayName("名称为空应抛出异常")
        void shouldThrowWhenNameBlank() {
            assertThatThrownBy(() -> Role.create(TENANT, "teacher", "  ", null, false, "admin"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("角色名称不能为空");
        }

        @Test
        @DisplayName("名称超长应抛出异常")
        void shouldThrowWhenNameTooLong() {
            String tooLong = "角".repeat(101);
            assertThatThrownBy(() -> Role.create(TENANT, "teacher", tooLong, null, false, "admin"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("角色名称不能超过100字符");
        }

        @Test
        @DisplayName("系统内置角色应携带初始权限")
        void shouldCreateSystemRole() {
            Role role = Role.createSystemRole(TENANT, "super_admin", "超级管理员", "内置角色",
                    Set.of(Permission.fromCode("*")), "system");

            assertThat(role.isSystem()).isTrue();
            assertThat(role.getPermissions()).hasSize(1);
            assertThat(role.hasPermission(Permission.of(Permission.ResourceType.COURSE, Permission.Action.DELETE, null)))
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("权限管理")
    class PermissionManagement {

        @Test
        @DisplayName("添加权限应产生事件")
        void shouldAddPermission() {
            Role role = plainRole();
            role.clearDomainEvents();

            role.addPermission(USER_READ, "admin");

            assertThat(role.getPermissions()).containsExactly(USER_READ);
            assertThat(role.getDomainEvents()).hasSize(1);
            assertThat(role.getDomainEvents().get(0)).isInstanceOf(PermissionAddedEvent.class);
        }

        @Test
        @DisplayName("重复添加权限不产生事件")
        void shouldNotAddDuplicatePermission() {
            Role role = plainRole();
            role.addPermission(USER_READ, "admin");
            role.clearDomainEvents();

            role.addPermission(USER_READ, "admin");

            assertThat(role.getPermissions()).hasSize(1);
            assertThat(role.getDomainEvents()).isEmpty();
        }

        @Test
        @DisplayName("移除权限应产生事件")
        void shouldRemovePermission() {
            Role role = plainRole();
            role.addPermission(USER_READ, "admin");
            role.clearDomainEvents();

            role.removePermission(USER_READ, "admin");

            assertThat(role.getPermissions()).isEmpty();
            assertThat(role.getDomainEvents().get(0)).isInstanceOf(PermissionRemovedEvent.class);
        }

        @Test
        @DisplayName("移除不存在的权限不产生事件")
        void shouldNotRemoveMissingPermission() {
            Role role = plainRole();
            role.clearDomainEvents();

            role.removePermission(USER_READ, "admin");

            assertThat(role.getDomainEvents()).isEmpty();
        }

        @Test
        @DisplayName("批量设置权限应替换全部")
        void shouldSetPermissions() {
            Role role = plainRole();
            role.addPermission(USER_READ, "admin");
            role.clearDomainEvents();

            Permission roleWrite = Permission.of(Permission.ResourceType.ROLE, Permission.Action.UPDATE, null);
            role.setPermissions(Set.of(roleWrite), "admin");

            assertThat(role.getPermissions()).containsExactly(roleWrite);
            assertThat(role.getDomainEvents()).hasSize(1);
        }

        @Test
        @DisplayName("精确权限匹配")
        void shouldMatchExactPermission() {
            Role role = plainRole();
            role.addPermission(USER_READ, "admin");

            assertThat(role.hasPermission(Permission.of(Permission.ResourceType.USER, Permission.Action.READ, "u-1")))
                    .isTrue();
            assertThat(role.hasPermission(Permission.of(Permission.ResourceType.USER, Permission.Action.DELETE, null)))
                    .isFalse();
        }

        @Test
        @DisplayName("通配权限匹配任意请求")
        void shouldMatchWildcardPermission() {
            Role role = plainRole();
            role.addPermission(Permission.fromCode("*"), "admin");

            assertThat(role.hasPermission(Permission.of(Permission.ResourceType.SYSTEM, Permission.Action.DELETE, "x")))
                    .isTrue();
        }

        @Test
        @DisplayName("按资源类型与动作校验权限")
        void shouldCheckByResourceTypeAndAction() {
            Role role = plainRole();
            role.addPermission(USER_READ, "admin");

            assertThat(role.hasPermission(Permission.ResourceType.USER, Permission.Action.READ, null)).isTrue();
            assertThat(role.hasPermission(Permission.ResourceType.USER, Permission.Action.UPDATE, null)).isFalse();
        }

        @Test
        @DisplayName("资源ID需一致才匹配")
        void shouldRespectResourceId() {
            Role role = plainRole();
            role.addPermission(Permission.of(Permission.ResourceType.COURSE, Permission.Action.READ, "course-1"), "admin");

            assertThat(role.hasPermission(Permission.of(Permission.ResourceType.COURSE, Permission.Action.READ, "course-1")))
                    .isTrue();
            assertThat(role.hasPermission(Permission.of(Permission.ResourceType.COURSE, Permission.Action.READ, "course-2")))
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("信息与状态")
    class InfoAndStatus {

        @Test
        @DisplayName("更新基本信息")
        void shouldUpdateInfo() {
            Role role = plainRole();
            role.clearDomainEvents();

            role.updateInfo("高级教师", "新描述", "admin");

            assertThat(role.getName()).isEqualTo("高级教师");
            assertThat(role.getDescription()).isEqualTo("新描述");
            assertThat(role.getDomainEvents()).hasSize(1);
        }

        @Test
        @DisplayName("普通角色可更新编码")
        void shouldUpdateCodeForPlainRole() {
            Role role = plainRole();

            role.updateCode("senior_teacher", "admin");

            assertThat(role.getCode()).isEqualTo("SENIOR_TEACHER");
        }

        @Test
        @DisplayName("系统角色禁止更新编码")
        void shouldThrowWhenUpdateSystemCode() {
            Role role = Role.createSystemRole(TENANT, "sys", "系统", null, Set.of(), "system");

            assertThatThrownBy(() -> role.updateCode("new_code", "admin"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("系统内置角色不允许修改编码");
        }

        @Test
        @DisplayName("普通角色可停用与激活")
        void shouldDeactivateAndActivate() {
            Role role = plainRole();
            role.clearDomainEvents();

            role.deactivate("admin");
            assertThat(role.getStatus()).isEqualTo(RoleStatus.INACTIVE);
            assertThat(role.isActive()).isFalse();
            assertThat(role.getDomainEvents().get(0)).isInstanceOf(RoleStatusChangedEvent.class);

            role.activate("admin");
            assertThat(role.getStatus()).isEqualTo(RoleStatus.ACTIVE);
        }

        @Test
        @DisplayName("系统角色禁止停用")
        void shouldThrowWhenDeactivateSystem() {
            Role role = Role.createSystemRole(TENANT, "sys", "系统", null, Set.of(), "system");

            assertThatThrownBy(() -> role.deactivate("admin"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("系统内置角色不允许停用");
        }
    }

    @Nested
    @DisplayName("删除")
    class Delete {

        @Test
        @DisplayName("普通角色可删除")
        void shouldDeletePlainRole() {
            Role role = plainRole();

            role.delete("admin", "不再使用");

            assertThat(role.getStatus()).isEqualTo(RoleStatus.DELETED);
            assertThat(role.isDeleted()).isTrue();
            assertThat(role.getDeletedAt()).isNotNull();
            assertThat(role.getDeleteReason()).isEqualTo("不再使用");
            List<DomainEvent> events = role.getDomainEvents();
            assertThat(events.get(events.size() - 1)).isInstanceOf(RoleDeletedEvent.class);
        }

        @Test
        @DisplayName("系统角色禁止删除")
        void shouldThrowWhenDeleteSystem() {
            Role role = Role.createSystemRole(TENANT, "sys", "系统", null, Set.of(), "system");

            assertThatThrownBy(() -> role.delete("admin", "原因"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("系统内置角色不允许删除");
        }

        @Test
        @DisplayName("重复删除应抛出异常")
        void shouldThrowWhenDeleteTwice() {
            Role role = plainRole();
            role.delete("admin", "第一次");

            assertThatThrownBy(() -> role.delete("admin", "第二次"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("角色已被删除");
        }
    }

    @Nested
    @DisplayName("重建与相等性")
    class ReconstructAndEquality {

        @Test
        @DisplayName("从持久化重建角色")
        void shouldReconstruct() {
            RoleId id = RoleId.generate();
            Instant createdAt = Instant.now().minusSeconds(7200);
            Instant updatedAt = Instant.now().minusSeconds(3600);

            Role role = Role.reconstruct(id, TENANT, "teacher", "教师", "描述",
                    Set.of(USER_READ), RoleStatus.INACTIVE, false, createdAt, updatedAt, null, null, null);

            assertThat(role.getId()).isEqualTo(id);
            assertThat(role.getCode()).isEqualTo("TEACHER");
            assertThat(role.getStatus()).isEqualTo(RoleStatus.INACTIVE);
            assertThat(role.getPermissions()).containsExactly(USER_READ);
            assertThat(role.getCreatedAt()).isEqualTo(createdAt);
            assertThat(role.getUpdatedAt()).isEqualTo(updatedAt);
            assertThat(role.getDomainEvents()).isEmpty();
        }

        @Test
        @DisplayName("相同ID角色相等")
        void shouldBeEqualWithSameId() {
            RoleId id = RoleId.generate();
            Role r1 = Role.reconstruct(id, TENANT, "a_role", "A", null, Set.of(), RoleStatus.ACTIVE,
                    false, Instant.now(), Instant.now(), null, null, null);
            Role r2 = Role.reconstruct(id, TENANT, "b_role", "B", null, Set.of(), RoleStatus.INACTIVE,
                    false, Instant.now(), Instant.now(), null, null, null);

            assertThat(r1).isEqualTo(r2).hasSameHashCodeAs(r2);
        }

        @Test
        @DisplayName("不同ID角色不相等")
        void shouldNotBeEqualWithDifferentId() {
            List<Role> roles = List.of(plainRole(), plainRole());

            assertThat(roles.get(0)).isNotEqualTo(roles.get(1));
            assertThat(roles.get(0).getDomainEvents()).isNotEmpty();
        }
    }
}
