package com.platform.tenant.domain.model.valueobjects;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * 配额值对象测试 —— 依据 Quota 真实 API 重写。
 * 原实现引用了 professional()/enterprise()/unlimited() 等不存在的方法, 无法编译。
 */
@DisplayName("配额 Quota 测试")
class QuotaTest {

    private static final Quota BASIC = Quota.of(100, 50, 10, 1000, 50);

    @Nested
    @DisplayName("创建配额")
    class CreateQuota {

        @Test
        @DisplayName("of() 应按给定值创建")
        void shouldCreateFromParameters() {
            assertThat(BASIC.getMaxUsers()).isEqualTo(100);
            assertThat(BASIC.getMaxCourses()).isEqualTo(50);
            assertThat(BASIC.getMaxStorageGB()).isEqualTo(10);
            assertThat(BASIC.getMaxStorageBytes()).isEqualTo(10L * 1024 * 1024 * 1024);
            assertThat(BASIC.getMaxApiCallsPerMin()).isEqualTo(1000);
            assertThat(BASIC.getMaxApiCallsPerMinute()).isEqualTo(1000);
            assertThat(BASIC.getMaxConcurrentSessions()).isEqualTo(50);
        }

        @Test
        @DisplayName("of() 应为知识点/资源使用默认上限")
        void shouldUseDefaultsForKpAndResource() {
            assertThat(BASIC.getMaxKnowledgePoints()).isEqualTo(2000);
            assertThat(BASIC.getMaxResourcesPerCourse()).isEqualTo(100);
        }

        @Test
        @DisplayName("defaultQuota() 应返回标准档位")
        void shouldCreateDefaultQuota() {
            Quota q = Quota.defaultQuota();
            assertThat(q.getMaxUsers()).isEqualTo(1000);
            assertThat(q.getMaxCourses()).isEqualTo(100);
            assertThat(q.getMaxStorageGB()).isEqualTo(10);
            assertThat(q.getMaxApiCallsPerMin()).isEqualTo(10000);
            assertThat(q.getMaxConcurrentSessions()).isEqualTo(500);
        }

        @Test
        @DisplayName("enterpriseQuota() 应高于 defaultQuota()")
        void shouldCreateEnterpriseQuota() {
            Quota q = Quota.enterpriseQuota();
            assertThat(q.getMaxUsers()).isEqualTo(10000);
            assertThat(q.getMaxCourses()).isEqualTo(1000);
            assertThat(q.getMaxStorageGB()).isEqualTo(100);
            assertThat(q.getMaxApiCallsPerMin()).isEqualTo(100000);
            assertThat(q.getMaxConcurrentSessions()).isEqualTo(5000);
            assertThat(q.getMaxUsers()).isGreaterThan(Quota.defaultQuota().getMaxUsers());
        }
    }

    @Nested
    @DisplayName("参数校验")
    class Validation {

        @Test
        @DisplayName("最大用户数为 0 应抛异常")
        void shouldThrowWhenUsersIsZero() {
            assertThatThrownBy(() -> Quota.of(0, 50, 10, 1000, 50))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("最大用户数");
        }

        @Test
        @DisplayName("最大课程数为负应抛异常")
        void shouldThrowWhenCoursesIsNegative() {
            assertThatThrownBy(() -> Quota.of(100, -1, 10, 1000, 50))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("最大课程数");
        }

        @Test
        @DisplayName("最大存储为 0 应抛异常")
        void shouldThrowWhenStorageIsZero() {
            assertThatThrownBy(() -> Quota.of(100, 50, 0, 1000, 50))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("最大存储");
        }

        @Test
        @DisplayName("最大并发会话为 0 应抛异常")
        void shouldThrowWhenSessionsIsZero() {
            assertThatThrownBy(() -> Quota.of(100, 50, 10, 1000, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("最大并发会话数");
        }
    }

    @Nested
    @DisplayName("配额判定")
    class Allocation {

        @Test
        @DisplayName("用户数未达上限可分配")
        void shouldAllowUserWhenBelowLimit() {
            assertThat(BASIC.canAllocateUser(0)).isTrue();
            assertThat(BASIC.canAllocateUser(99)).isTrue();
            assertThat(BASIC.canAllocateUser(100)).isFalse();
        }

        @Test
        @DisplayName("课程数未达上限可分配")
        void shouldAllowCourseWhenBelowLimit() {
            assertThat(BASIC.canAllocateCourse(49)).isTrue();
            assertThat(BASIC.canAllocateCourse(50)).isFalse();
        }

        @Test
        @DisplayName("存储未达上限可分配")
        void shouldAllowStorageWhenBelowLimit() {
            assertThat(BASIC.canAllocateStorage(9)).isTrue();
            assertThat(BASIC.canAllocateStorage(10)).isFalse();
        }

        @Test
        @DisplayName("存储超出字节限制应判定超限")
        void shouldDetectStorageExceeded() {
            assertThat(BASIC.isStorageExceeded(9L * 1024 * 1024 * 1024)).isFalse();
            assertThat(BASIC.isStorageExceeded(11L * 1024 * 1024 * 1024)).isTrue();
        }
    }

    @Nested
    @DisplayName("不可变更新")
    class WithMethods {

        @Test
        @DisplayName("withMaxUsers 应返回新实例而非修改原实例")
        void shouldWithMaxUsers() {
            Quota updated = BASIC.withMaxUsers(500);
            assertThat(updated.getMaxUsers()).isEqualTo(500);
            assertThat(BASIC.getMaxUsers()).isEqualTo(100);
            assertThat(updated.getMaxCourses()).isEqualTo(50);
        }

        @Test
        @DisplayName("withMaxCourses / withMaxStorageGB / withMaxApiCallsPerMin / withMaxConcurrentSessions 均可生效")
        void shouldWithOtherFields() {
            Quota q = BASIC
                    .withMaxCourses(200)
                    .withMaxStorageGB(20)
                    .withMaxApiCallsPerMin(5000)
                    .withMaxConcurrentSessions(100);
            assertThat(q.getMaxCourses()).isEqualTo(200);
            assertThat(q.getMaxStorageGB()).isEqualTo(20);
            assertThat(q.getMaxApiCallsPerMin()).isEqualTo(5000);
            assertThat(q.getMaxConcurrentSessions()).isEqualTo(100);
        }
    }

    @Nested
    @DisplayName("JSON 序列化")
    class Json {

        @Test
        @DisplayName("toJson / fromJson 应可往返")
        void shouldRoundTripJson() {
            String json = BASIC.toJson();
            Quota parsed = Quota.fromJson(json);
            assertThat(parsed.getMaxUsers()).isEqualTo(BASIC.getMaxUsers());
            assertThat(parsed.getMaxCourses()).isEqualTo(BASIC.getMaxCourses());
            assertThat(parsed.getMaxStorageGB()).isEqualTo(BASIC.getMaxStorageGB());
            assertThat(parsed.getMaxApiCallsPerMin()).isEqualTo(BASIC.getMaxApiCallsPerMin());
            assertThat(parsed.getMaxConcurrentSessions()).isEqualTo(BASIC.getMaxConcurrentSessions());
        }
    }
}
