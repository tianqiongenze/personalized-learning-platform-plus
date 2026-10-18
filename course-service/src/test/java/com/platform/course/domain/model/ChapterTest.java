package com.platform.course.domain.model;

import com.platform.course.domain.event.ChapterCreatedEvent;
import com.platform.course.domain.event.ChapterDeletedEvent;
import com.platform.course.domain.event.ChapterUpdatedEvent;
import com.platform.course.domain.model.valueobjects.ChapterId;
import com.platform.course.domain.model.valueobjects.ChapterStatus;
import com.platform.course.domain.model.valueobjects.CourseId;
import com.platform.course.domain.model.valueobjects.KnowledgePointId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Chapter 聚合根测试")
class ChapterTest {

    private static final String TENANT = "tenant-1";
    private static final CourseId COURSE_ID = CourseId.generate();

    private Chapter draftChapter() {
        return Chapter.create(COURSE_ID, TENANT, "ch_01", "第一章", "描述", null, 1, 2, 3, "creator-1");
    }

    @Nested
    @DisplayName("创建章节")
    class Create {

        @Test
        @DisplayName("应创建草稿态章节")
        void shouldCreateDraftChapter() {
            Chapter chapter = draftChapter();

            assertThat(chapter.getId()).isNotNull();
            assertThat(chapter.getCourseId()).isEqualTo(COURSE_ID);
            assertThat(chapter.getTenantId()).isEqualTo(TENANT);
            assertThat(chapter.getCode()).isEqualTo("CH_01");
            assertThat(chapter.getName()).isEqualTo("第一章");
            assertThat(chapter.getOrder()).isEqualTo(1);
            assertThat(chapter.getDifficulty()).isEqualTo(2);
            assertThat(chapter.getEstimatedHours()).isEqualTo(3);
            assertThat(chapter.getStatus()).isEqualTo(ChapterStatus.DRAFT);
            assertThat(chapter.getDomainEvents().get(0)).isInstanceOf(ChapterCreatedEvent.class);
        }

        @Test
        @DisplayName("编码为空应抛出异常")
        void shouldThrowWhenCodeBlank() {
            assertThatThrownBy(() -> Chapter.create(COURSE_ID, TENANT, "", "名称", null, null, 1, 1, 1, "c"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("章节编码不能为空");
        }

        @Test
        @DisplayName("名称为空应抛出异常")
        void shouldThrowWhenNameBlank() {
            assertThatThrownBy(() -> Chapter.create(COURSE_ID, TENANT, "ch_01", "", null, null, 1, 1, 1, "c"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("章节名称不能为空");
        }
    }

    @Nested
    @DisplayName("状态与排序")
    class StatusAndOrder {

        @Test
        @DisplayName("发布章节")
        void shouldPublish() {
            Chapter chapter = draftChapter();
            chapter.clearDomainEvents();

            chapter.publish("admin");

            assertThat(chapter.getStatus()).isEqualTo(ChapterStatus.PUBLISHED);
            assertThat(chapter.isPublished()).isTrue();
            assertThat(chapter.getDomainEvents().get(0)).isInstanceOf(ChapterUpdatedEvent.class);
        }

        @Test
        @DisplayName("更新章节排序")
        void shouldUpdateOrder() {
            Chapter chapter = draftChapter();

            chapter.updateOrder(5, "admin");

            assertThat(chapter.getOrder()).isEqualTo(5);
        }

        @Test
        @DisplayName("删除章节")
        void shouldDelete() {
            Chapter chapter = draftChapter();
            chapter.clearDomainEvents();

            chapter.delete("admin", "不再使用");

            assertThat(chapter.getStatus()).isEqualTo(ChapterStatus.DELETED);
            assertThat(chapter.isDeleted()).isTrue();
            assertThat(chapter.getDeleteReason()).isEqualTo("不再使用");
            assertThat(chapter.getDomainEvents().get(0)).isInstanceOf(ChapterDeletedEvent.class);
        }

        @Test
        @DisplayName("重复删除应抛出异常")
        void shouldThrowWhenDeleteTwice() {
            Chapter chapter = draftChapter();
            chapter.delete("admin", "第一次");

            assertThatThrownBy(() -> chapter.delete("admin", "第二次"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("章节已被删除");
        }
    }

    @Nested
    @DisplayName("信息与知识点")
    class InfoAndKnowledgePoints {

        @Test
        @DisplayName("更新基本信息")
        void shouldUpdateInfo() {
            Chapter chapter = draftChapter();

            chapter.updateInfo("新章节", "新描述", 4, 8, "admin");

            assertThat(chapter.getName()).isEqualTo("新章节");
            assertThat(chapter.getDifficulty()).isEqualTo(4);
            assertThat(chapter.getEstimatedHours()).isEqualTo(8);
        }

        @Test
        @DisplayName("草稿态可更新编码")
        void shouldUpdateCodeForDraft() {
            Chapter chapter = draftChapter();

            chapter.updateCode("ch_99", "admin");

            assertThat(chapter.getCode()).isEqualTo("CH_99");
        }

        @Test
        @DisplayName("非草稿态禁止更新编码")
        void shouldThrowWhenUpdateCodeNotDraft() {
            Chapter chapter = draftChapter();
            chapter.publish("admin");

            assertThatThrownBy(() -> chapter.updateCode("ch_99", "admin"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("仅草稿态章节允许修改编码");
        }

        @Test
        @DisplayName("添加与移除知识点")
        void shouldAddAndRemoveKnowledgePoint() {
            Chapter chapter = draftChapter();
            KnowledgePointId kpId = KnowledgePointId.generate();
            chapter.clearDomainEvents();

            chapter.addKnowledgePoint(kpId, "admin");
            assertThat(chapter.getKnowledgePointIds()).containsExactly(kpId);
            assertThat(chapter.getDomainEvents()).hasSize(1);

            chapter.removeKnowledgePoint(kpId, "admin");
            assertThat(chapter.getKnowledgePointIds()).isEmpty();
        }

        @Test
        @DisplayName("重复添加知识点不产生事件")
        void shouldNotAddDuplicateKnowledgePoint() {
            Chapter chapter = draftChapter();
            KnowledgePointId kpId = KnowledgePointId.generate();
            chapter.addKnowledgePoint(kpId, "admin");
            chapter.clearDomainEvents();

            chapter.addKnowledgePoint(kpId, "admin");

            assertThat(chapter.getDomainEvents()).isEmpty();
        }
    }

    @Nested
    @DisplayName("重建与相等性")
    class ReconstructAndEquality {

        @Test
        @DisplayName("从持久化重建章节")
        void shouldReconstruct() {
            ChapterId id = ChapterId.generate();
            KnowledgePointId kpId = KnowledgePointId.generate();
            Instant createdAt = Instant.now().minusSeconds(3600);

            Chapter chapter = Chapter.reconstruct(id, COURSE_ID, TENANT, "ch_01", "第一章", "描述",
                    null, 2, 3, 4, Set.of(kpId), ChapterStatus.PUBLISHED,
                    createdAt, createdAt, null, null, null);

            assertThat(chapter.getId()).isEqualTo(id);
            assertThat(chapter.getOrder()).isEqualTo(2);
            assertThat(chapter.getStatus()).isEqualTo(ChapterStatus.PUBLISHED);
            assertThat(chapter.getKnowledgePointIds()).containsExactly(kpId);
            assertThat(chapter.getDomainEvents()).isEmpty();
        }

        @Test
        @DisplayName("相同ID章节相等")
        void shouldBeEqualWithSameId() {
            ChapterId id = ChapterId.generate();
            Chapter c1 = Chapter.reconstruct(id, COURSE_ID, TENANT, "a_1", "A", null, null, 1, 1, 1,
                    Set.of(), ChapterStatus.DRAFT, Instant.now(), Instant.now(), null, null, null);
            Chapter c2 = Chapter.reconstruct(id, COURSE_ID, TENANT, "b_2", "B", null, null, 2, 2, 2,
                    Set.of(), ChapterStatus.PUBLISHED, Instant.now(), Instant.now(), null, null, null);

            assertThat(c1).isEqualTo(c2).hasSameHashCodeAs(c2);
        }
    }
}
