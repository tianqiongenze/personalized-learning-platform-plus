package com.platform.course.domain.model;

import com.platform.course.domain.event.KnowledgePointCreatedEvent;
import com.platform.course.domain.event.KnowledgePointDeletedEvent;
import com.platform.course.domain.event.KnowledgePointUpdatedEvent;
import com.platform.course.domain.model.valueobjects.ChapterId;
import com.platform.course.domain.model.valueobjects.CourseId;
import com.platform.course.domain.model.valueobjects.KnowledgePointId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("KnowledgePoint 聚合根测试")
class KnowledgePointTest {

    private static final String TENANT = "tenant-1";
    private static final CourseId COURSE_ID = CourseId.generate();

    private KnowledgePoint knowledgePoint() {
        return KnowledgePoint.create(COURSE_ID, TENANT, "kp_01", "一元二次方程", "描述",
                ChapterId.generate(), 3, "creator-1");
    }

    @Nested
    @DisplayName("创建知识点")
    class Create {

        @Test
        @DisplayName("应创建知识点")
        void shouldCreate() {
            KnowledgePoint kp = knowledgePoint();

            assertThat(kp.getId()).isNotNull();
            assertThat(kp.getCourseId()).isEqualTo(COURSE_ID);
            assertThat(kp.getCode()).isEqualTo("KP_01");
            assertThat(kp.getName()).isEqualTo("一元二次方程");
            assertThat(kp.getDifficulty()).isEqualTo(3);
            assertThat(kp.getPrerequisites()).isEmpty();
            assertThat(kp.getRelatedPoints()).isEmpty();
            assertThat(kp.isDeleted()).isFalse();
            assertThat(kp.getDomainEvents().get(0)).isInstanceOf(KnowledgePointCreatedEvent.class);
        }

        @Test
        @DisplayName("编码为空应抛出异常")
        void shouldThrowWhenCodeBlank() {
            assertThatThrownBy(() -> KnowledgePoint.create(COURSE_ID, TENANT, "", "名称", null, null, 1, "c"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("知识点编码不能为空");
        }

        @Test
        @DisplayName("名称为空应抛出异常")
        void shouldThrowWhenNameBlank() {
            assertThatThrownBy(() -> KnowledgePoint.create(COURSE_ID, TENANT, "kp_01", "", null, null, 1, "c"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("知识点名称不能为空");
        }
    }

    @Nested
    @DisplayName("前置依赖")
    class Prerequisites {

        @Test
        @DisplayName("添加前置知识点")
        void shouldAddPrerequisite() {
            KnowledgePoint kp = knowledgePoint();
            KnowledgePointId pre = KnowledgePointId.generate();
            kp.clearDomainEvents();

            kp.addPrerequisite(pre, "admin");

            assertThat(kp.getPrerequisites()).containsExactly(pre);
            assertThat(kp.getDomainEvents().get(0)).isInstanceOf(KnowledgePointUpdatedEvent.class);
        }

        @Test
        @DisplayName("不能依赖自己")
        void shouldThrowWhenSelfPrerequisite() {
            KnowledgePoint kp = knowledgePoint();

            assertThatThrownBy(() -> kp.addPrerequisite(kp.getId(), "admin"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("知识点不能依赖自己");
        }

        @Test
        @DisplayName("重复添加前置不产生事件")
        void shouldNotAddDuplicatePrerequisite() {
            KnowledgePoint kp = knowledgePoint();
            KnowledgePointId pre = KnowledgePointId.generate();
            kp.addPrerequisite(pre, "admin");
            kp.clearDomainEvents();

            kp.addPrerequisite(pre, "admin");

            assertThat(kp.getDomainEvents()).isEmpty();
        }

        @Test
        @DisplayName("移除前置知识点")
        void shouldRemovePrerequisite() {
            KnowledgePoint kp = knowledgePoint();
            KnowledgePointId pre = KnowledgePointId.generate();
            kp.addPrerequisite(pre, "admin");
            kp.clearDomainEvents();

            kp.removePrerequisite(pre, "admin");

            assertThat(kp.getPrerequisites()).isEmpty();
            assertThat(kp.getDomainEvents()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("关联知识点")
    class RelatedPoints {

        @Test
        @DisplayName("添加关联知识点")
        void shouldAddRelatedPoint() {
            KnowledgePoint kp = knowledgePoint();
            KnowledgePointId related = KnowledgePointId.generate();

            kp.addRelatedPoint(related, "admin");

            assertThat(kp.getRelatedPoints()).containsExactly(related);
        }

        @Test
        @DisplayName("不能关联自己")
        void shouldThrowWhenSelfRelated() {
            KnowledgePoint kp = knowledgePoint();

            assertThatThrownBy(() -> kp.addRelatedPoint(kp.getId(), "admin"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("知识点不能关联自己");
        }

        @Test
        @DisplayName("移除关联知识点")
        void shouldRemoveRelatedPoint() {
            KnowledgePoint kp = knowledgePoint();
            KnowledgePointId related = KnowledgePointId.generate();
            kp.addRelatedPoint(related, "admin");

            kp.removeRelatedPoint(related, "admin");

            assertThat(kp.getRelatedPoints()).isEmpty();
        }
    }

    @Nested
    @DisplayName("信息与元数据")
    class InfoAndMetadata {

        @Test
        @DisplayName("更新基本信息")
        void shouldUpdateInfo() {
            KnowledgePoint kp = knowledgePoint();

            kp.updateInfo("新名称", "新描述", 5, "admin");

            assertThat(kp.getName()).isEqualTo("新名称");
            assertThat(kp.getDifficulty()).isEqualTo(5);
        }

        @Test
        @DisplayName("更新编码")
        void shouldUpdateCode() {
            KnowledgePoint kp = knowledgePoint();

            kp.updateCode("kp_99", "admin");

            assertThat(kp.getCode()).isEqualTo("KP_99");
        }

        @Test
        @DisplayName("更新元数据应整体替换")
        void shouldUpdateMetadata() {
            KnowledgePoint kp = knowledgePoint();

            kp.updateMetadata(Map.of("tag", "algebra"), "admin");

            assertThat(kp.getMetadata()).containsEntry("tag", "algebra");
        }
    }

    @Nested
    @DisplayName("删除")
    class Delete {

        @Test
        @DisplayName("删除知识点")
        void shouldDelete() {
            KnowledgePoint kp = knowledgePoint();
            kp.clearDomainEvents();

            kp.delete("admin", "不再使用");

            assertThat(kp.isDeleted()).isTrue();
            assertThat(kp.getDeletedAt()).isNotNull();
            assertThat(kp.getDeleteReason()).isEqualTo("不再使用");
            assertThat(kp.getDomainEvents().get(0)).isInstanceOf(KnowledgePointDeletedEvent.class);
        }
    }

    @Nested
    @DisplayName("重建与相等性")
    class ReconstructAndEquality {

        @Test
        @DisplayName("从持久化重建知识点")
        void shouldReconstruct() {
            KnowledgePointId id = KnowledgePointId.generate();
            KnowledgePointId pre = KnowledgePointId.generate();
            Instant createdAt = Instant.now().minusSeconds(3600);

            KnowledgePoint kp = KnowledgePoint.reconstruct(id, COURSE_ID, TENANT, "kp_01", "一元二次方程",
                    "描述", ChapterId.generate(), 3, Set.of(pre), Set.of(), Map.of("k", "v"),
                    createdAt, createdAt, null, null, null);

            assertThat(kp.getId()).isEqualTo(id);
            assertThat(kp.getPrerequisites()).containsExactly(pre);
            assertThat(kp.getMetadata()).containsEntry("k", "v");
            assertThat(kp.getDomainEvents()).isEmpty();
        }

        @Test
        @DisplayName("相同ID知识点相等")
        void shouldBeEqualWithSameId() {
            KnowledgePointId id = KnowledgePointId.generate();
            KnowledgePoint kp1 = KnowledgePoint.reconstruct(id, COURSE_ID, TENANT, "a_1", "A", null, null, 1,
                    Set.of(), Set.of(), Map.of(), Instant.now(), Instant.now(), null, null, null);
            KnowledgePoint kp2 = KnowledgePoint.reconstruct(id, COURSE_ID, TENANT, "b_2", "B", null, null, 2,
                    Set.of(), Set.of(), Map.of(), Instant.now(), Instant.now(), null, null, null);

            assertThat(kp1).isEqualTo(kp2).hasSameHashCodeAs(kp2);
        }
    }
}
