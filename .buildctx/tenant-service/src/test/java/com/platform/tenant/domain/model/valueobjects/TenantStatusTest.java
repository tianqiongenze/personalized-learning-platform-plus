package com.platform.tenant.domain.model.valueobjects;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("TenantStatus 枚举测试")
class TenantStatusTest {

    @Nested
    @DisplayName("状态属性")
    class StatusProperties {

        @Test
        @DisplayName("显示名称应正确")
        void shouldHaveCorrectDisplayNames() {
            assertThat(TenantStatus.DRAFT.getDisplayName()).isEqualTo("草稿");
            assertThat(TenantStatus.ACTIVE.getDisplayName()).isEqualTo("激活");
            assertThat(TenantStatus.SUSPENDED.getDisplayName()).isEqualTo("暂停");
            assertThat(TenantStatus.EXPIRED.getDisplayName()).isEqualTo("过期");
            assertThat(TenantStatus.DELETED.getDisplayName()).isEqualTo("已删除");
            assertThat(TenantStatus.LOCKED.getDisplayName()).isEqualTo("锁定");
        }

        @Test
        @DisplayName("激活态判断应正确")
        void shouldCorrectlyIdentifyActiveStatus() {
            assertThat(TenantStatus.DRAFT.isActive()).isFalse();
            assertThat(TenantStatus.ACTIVE.isActive()).isTrue();
            assertThat(TenantStatus.SUSPENDED.isActive()).isFalse();
            assertThat(TenantStatus.EXPIRED.isActive()).isFalse();
            assertThat(TenantStatus.DELETED.isActive()).isFalse();
            assertThat(TenantStatus.LOCKED.isActive()).isFalse();
        }
    }

    @Nested
    @DisplayName("状态流转")
    class StatusTransition {

        @Test
        @DisplayName("草稿态可流转到激活态和删除态")
        void shouldAllowDraftTransitions() {
            assertThat(TenantStatus.DRAFT.canTransitionTo(TenantStatus.ACTIVE)).isTrue();
            assertThat(TenantStatus.DRAFT.canTransitionTo(TenantStatus.DELETED)).isTrue();
            assertThat(TenantStatus.DRAFT.canTransitionTo(TenantStatus.SUSPENDED)).isFalse();
            assertThat(TenantStatus.DRAFT.canTransitionTo(TenantStatus.EXPIRED)).isFalse();
            assertThat(TenantStatus.DRAFT.canTransitionTo(TenantStatus.LOCKED)).isFalse();
        }

        @Test
        @DisplayName("激活态可流转到暂停、过期、锁定、删除")
        void shouldAllowActiveTransitions() {
            assertThat(TenantStatus.ACTIVE.canTransitionTo(TenantStatus.SUSPENDED)).isTrue();
            assertThat(TenantStatus.ACTIVE.canTransitionTo(TenantStatus.EXPIRED)).isTrue();
            assertThat(TenantStatus.ACTIVE.canTransitionTo(TenantStatus.LOCKED)).isTrue();
            assertThat(TenantStatus.ACTIVE.canTransitionTo(TenantStatus.DELETED)).isTrue();
            assertThat(TenantStatus.ACTIVE.canTransitionTo(TenantStatus.DRAFT)).isFalse();
        }

        @Test
        @DisplayName("暂停态可流转到激活和删除")
        void shouldAllowSuspendedTransitions() {
            assertThat(TenantStatus.SUSPENDED.canTransitionTo(TenantStatus.ACTIVE)).isTrue();
            assertThat(TenantStatus.SUSPENDED.canTransitionTo(TenantStatus.DELETED)).isTrue();
            assertThat(TenantStatus.SUSPENDED.canTransitionTo(TenantStatus.EXPIRED)).isFalse();
            assertThat(TenantStatus.SUSPENDED.canTransitionTo(TenantStatus.LOCKED)).isFalse();
        }

        @Test
        @DisplayName("过期态可流转到激活和删除")
        void shouldAllowExpiredTransitions() {
            assertThat(TenantStatus.EXPIRED.canTransitionTo(TenantStatus.ACTIVE)).isTrue();
            assertThat(TenantStatus.EXPIRED.canTransitionTo(TenantStatus.DELETED)).isTrue();
        }

        @Test
        @DisplayName("锁定态可流转到激活和删除")
        void shouldAllowLockedTransitions() {
            assertThat(TenantStatus.LOCKED.canTransitionTo(TenantStatus.ACTIVE)).isTrue();
            assertThat(TenantStatus.LOCKED.canTransitionTo(TenantStatus.DELETED)).isTrue();
        }

        @Test
        @DisplayName("删除态仅可流转到激活")
        void shouldAllowDeletedTransitions() {
            assertThat(TenantStatus.DELETED.canTransitionTo(TenantStatus.ACTIVE)).isTrue();
            assertThat(TenantStatus.DELETED.canTransitionTo(TenantStatus.SUSPENDED)).isFalse();
            assertThat(TenantStatus.DELETED.canTransitionTo(TenantStatus.EXPIRED)).isFalse();
            assertThat(TenantStatus.DELETED.canTransitionTo(TenantStatus.LOCKED)).isFalse();
        }

        @Test
        @DisplayName("同状态流转应返回true")
        void shouldAllowSelfTransition() {
            for (TenantStatus status : TenantStatus.values()) {
                assertThat(status.canTransitionTo(status)).isTrue();
            }
        }
    }

    @Nested
    @DisplayName("transitionTo 方法")
    class TransitionToMethod {

        @Test
        @DisplayName("合法流转应返回目标状态")
        void shouldReturnTargetStatusOnValidTransition() {
            TenantStatus result = TenantStatus.DRAFT.transitionTo(TenantStatus.ACTIVE);
            assertThat(result).isEqualTo(TenantStatus.ACTIVE);
        }

        @Test
        @DisplayName("非法流转应抛出异常")
        void shouldThrowExceptionOnInvalidTransition() {
            assertThatThrownBy(() -> TenantStatus.DRAFT.transitionTo(TenantStatus.SUSPENDED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不允许从 草稿 流转到 暂停");
        }

        @Test
        @DisplayName("同状态流转应返回自身")
        void shouldReturnSelfOnSelfTransition() {
            TenantStatus result = TenantStatus.ACTIVE.transitionTo(TenantStatus.ACTIVE);
            assertThat(result).isEqualTo(TenantStatus.ACTIVE);
        }
    }

    @Nested
    @DisplayName("可用流转")
    class AvailableTransitions {

        @Test
        @DisplayName("应返回正确的可用流转")
        void shouldReturnCorrectAvailableTransitions() {
            assertThat(TenantStatus.DRAFT.getAvailableTransitions())
                .containsExactlyInAnyOrder(TenantStatus.ACTIVE, TenantStatus.DELETED);
            
            assertThat(TenantStatus.ACTIVE.getAvailableTransitions())
                .containsExactlyInAnyOrder(TenantStatus.SUSPENDED, TenantStatus.EXPIRED, 
                    TenantStatus.LOCKED, TenantStatus.DELETED);
            
            assertThat(TenantStatus.DELETED.getAvailableTransitions())
                .containsExactly(TenantStatus.ACTIVE);
        }
    }
}