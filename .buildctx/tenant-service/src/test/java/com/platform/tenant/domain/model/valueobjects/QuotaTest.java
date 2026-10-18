package com.platform.tenant.domain.model.valueobjects;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Quota 值对象测试")
class QuotaTest {

    @Nested
    @DisplayName("创建配额")
    class CreateQuota {

        @Test
        @DisplayName("应该成功创建有效配额")
        void shouldCreateValidQuota() {
            assertThat(quota.getMaxUsers()).isEqualTo(100);
            assertThat(quota.getMaxCourses()).isEqualTo(50);
            assertThat(quota.getMaxStorageBytes()).isEqualTo(10L * 1024 * 1024 * 1024);
            assertThat(quota.getMaxApiCallsPerMinute()).isEqualTo(1000);
            assertThat(quota.getMaxConcurrentSessions()).isEqualTo(50);
            assertThat(quota.getMaxKnowledgePoints()).isEqualTo(5000);
            assertThat(quota.getMaxResourcesPerCourse()).isEqualTo(200);
        }

        @Test
        @DisplayName("预设配额应正确")
        void shouldHaveCorrectPresetQuotas() {
            assertThat(Quota.starter().getMaxUsers()).isEqualTo(100);
            assertThat(Quota.standard().getMaxUsers()).isEqualTo(1000);
            assertThat(Quota.professional().getMaxUsers()).isEqualTo(10000);
            assertThat(Quota.enterprise().getMaxUsers()).isEqualTo(100000);
            assertThat(Quota.unlimited().getMaxUsers()).isEqualTo(Integer.MAX_VALUE);
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1, -100})
        @DisplayName("最大用户数必须大于0")
        void shouldThrowWhenMaxUsersNotPositive(int maxUsers) {
            assertThatThrownBy(() -> Quota.of(maxUsers, 50, 10L * 1024 * 1024 * 1024, 1000, 50, 5000, 200))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("最大用户数必须大于0");
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1})
        @DisplayName("最大课程数必须大于0")
        void shouldThrowWhenMaxCoursesNotPositive(int maxCourses) {
            assertThatThrownBy(() -> Quota.of(100, maxCourses, 10L * 1024 * 1024 * 1024, 1000, 50, 5000, 200))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("最大课程数必须大于0");
        }

        @ParameterizedTest
        @ValueSource(longs = {0, -1})
        @DisplayName("最大存储必须大于0")
        void shouldThrowWhenMaxStorageNotPositive(long maxStorage) {
            assertThatThrownBy(() -> Quota.of(100, 50, maxStorage, 1000, 50, 5000, 200))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("最大存储空间必须大于0");
        }
    }

    @Nested
    @DisplayName("配额检查")
    class QuotaCheck {

        @Test
        @DisplayName("用户配额检查")
        void shouldCheckUserQuota() {
            Quota quota = Quota.starter(); // maxUsers = 100
            
            assertThat(quota.canAllocateUser(50)).isTrue();
            assertThat(quota.canAllocateUser(99)).isTrue();
            assertThat(quota.canAllocateUser(100)).isFalse();
            assertThat(quota.canAllocateUser(101)).isFalse();
        }

        @Test
        @DisplayName("课程配额检查")
        void shouldCheckCourseQuota() {
            Quota quota = Quota.starter(); // maxCourses = 50
            
            assertThat(quota.canAllocateCourse(25)).isTrue();
            assertThat(quota.canAllocateCourse(49)).isTrue();
            assertThat(quota.canAllocateCourse(50)).isFalse();
        }

        @Test
        @DisplayName("存储配额检查")
        void shouldCheckStorageQuota() {
            Quota quota = Quota.starter(); // 10GB
            long tenGB = 10L * 1024 * 1024 * 1024;
            
            assertThat(quota.isStorageExceeded(5L * 1024 * 1024 * 1024)).isFalse();
            assertThat(quota.isStorageExceeded(tenGB - 1)).isFalse();
            assertThat(quota.isStorageExceeded(tenGB)).isTrue();
            assertThat(quota.isStorageExceeded(tenGB + 1)).isTrue();
        }

        @Test
        @DisplayName("存储使用率计算")
        void shouldCalculateStorageUsagePercent() {
            Quota quota = Quota.starter(); // 10GB
            long fiveGB = 5L * 1024 * 1024 * 1024;
            
            assertThat(quota.getStorageUsagePercent(fiveGB)).isEqualTo(50.0);
            assertThat(quota.getStorageUsagePercent(0)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("无限配额存储使用率应为0")
        void shouldReturnZeroForUnlimitedStorage() {
            Quota quota = Quota.unlimited();
            
            assertThat(quota.getStorageUsagePercent(100L * 1024 * 1024 * 1024)).isEqualTo(0.0);
            assertThat(quota.isStorageExceeded(Long.MAX_VALUE)).isFalse();
        }
    }

    @Nested
    @DisplayName("With 方法")
    class WithMethods {

        @Test
        @DisplayName("withMaxUsers应返回新实例")
        void shouldReturnNewInstanceWithMaxUsers() {
            Quota original = Quota.starter();
            Quota modified = original.withMaxUsers(200);
            
            assertThat(modified.getMaxUsers()).isEqualTo(200);
            assertThat(original.getMaxUsers()).isEqualTo(100); // 原实例不变
            assertThat(modified).isNotSameAs(original);
        }

        @Test
        @DisplayName("withMaxCourses应返回新实例")
        void shouldReturnNewInstanceWithMaxCourses() {
            Quota original = Quota.starter();
            Quota modified = original.withMaxCourses(100);
            
            assertThat(modified.getMaxCourses()).isEqualTo(100);
            assertThat(original.getMaxCourses()).isEqualTo(50);
        }

        @Test
        @DisplayName("withMaxStorageBytes应返回新实例")
        void shouldReturnNewInstanceWithMaxStorage() {
            Quota original = Quota.starter();
            Quota modified = original.withMaxStorageBytes(20L * 1024 * 1024 * 1024);
            
            assertThat(modified.getMaxStorageBytes()).isEqualTo(20L * 1024 * 1024 * 1024);
            assertThat(original.getMaxStorageBytes()).isEqualTo(10L * 1024 * 1024 * 1024);
        }
    }

    @Nested
    @DisplayName("相等性与哈希")
    class EqualityAndHash {

        @Test
        @DisplayName("相同值的配额应相等")
        void shouldBeEqualWhenSameValues() {
            Quota q1 = Quota.of(100, 50, 10L * 1024 * 1024 * 1024, 1000, 50, 5000, 200);
            Quota q2 = Quota.of(100, 50, 10L * 1024 * 1024 * 1024, 1000, 50, 5000, 200);
            
            assertThat(q1).isEqualTo(q2);
            assertThat(q1.hashCode()).isEqualTo(q2.hashCode());
        }

        @Test
        @DisplayName("不同值的配额不相等")
        void shouldNotBeEqualWhenDifferentValues() {
            Quota q1 = Quota.starter();
            Quota q2 = Quota.standard();
            
            assertThat(q1).isNotEqualTo(q2);
        }
    }
}