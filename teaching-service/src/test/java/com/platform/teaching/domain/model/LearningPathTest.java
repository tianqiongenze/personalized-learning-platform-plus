package com.platform.teaching.domain.model;

import com.platform.teaching.domain.model.valueobjects.StepId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("LearningPath 聚合根测试")
class LearningPathTest {

    private LearningPath learningPath() {
        return LearningPath.create(UUID.randomUUID(), UUID.randomUUID(), "Java 入门", "描述", UUID.randomUUID());
    }

    private LearningPathStep step(String title) {
        return new LearningPathStep(UUID.randomUUID(), null, null, StepType.VIDEO, title, null,
                null, "video/mp4", 10, true);
    }

    @Nested
    @DisplayName("创建学习路径")
    class Create {

        @Test
        @DisplayName("应创建草稿态学习路径")
        void shouldCreateDraft() {
            LearningPath path = learningPath();

            assertThat(path.getId()).isNotNull();
            assertThat(path.getTitle()).isEqualTo("Java 入门");
            assertThat(path.getStatus()).isEqualTo(LearningPathStatus.DRAFT);
            assertThat(path.getVersion()).isZero();
            assertThat(path.getSteps()).isEmpty();
            assertThat(path.getCreatedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("步骤管理")
    class Steps {

        @Test
        @DisplayName("添加步骤应自动排序并递增版本")
        void shouldAddStep() {
            LearningPath path = learningPath();

            path.addStep(step("第一课"));

            assertThat(path.getSteps()).hasSize(1);
            assertThat(path.getSteps().get(0).getSequence()).isEqualTo(1);
            assertThat(path.getSteps().get(0).getLearningPath()).isSameAs(path);
            assertThat(path.getVersion()).isEqualTo(1);
        }

        @Test
        @DisplayName("多个步骤应依次排序")
        void shouldSequenceSteps() {
            LearningPath path = learningPath();

            path.addStep(step("第一课"));
            path.addStep(step("第二课"));
            path.addStep(step("第三课"));

            assertThat(path.getSteps()).extracting(LearningPathStep::getSequence)
                    .containsExactly(1, 2, 3);
            assertThat(path.getVersion()).isEqualTo(3);
        }

        @Test
        @DisplayName("移除步骤应重新排序")
        void shouldRemoveStepAndResequence() {
            LearningPath path = learningPath();
            path.addStep(step("第一课"));
            path.addStep(step("第二课"));
            path.addStep(step("第三课"));
            StepId middleId = StepId.of(path.getSteps().get(1).getId());

            path.removeStep(middleId);

            assertThat(path.getSteps()).hasSize(2);
            assertThat(path.getSteps()).extracting(LearningPathStep::getSequence).containsExactly(1, 2);
            assertThat(path.getSteps()).extracting(LearningPathStep::getTitle)
                    .containsExactly("第一课", "第三课");
        }
    }

    @Nested
    @DisplayName("发布与归档")
    class PublishAndArchive {

        @Test
        @DisplayName("含步骤的草稿可发布")
        void shouldPublishWithSteps() {
            LearningPath path = learningPath();
            path.addStep(step("第一课"));

            path.publish();

            assertThat(path.getStatus()).isEqualTo(LearningPathStatus.PUBLISHED);
            assertThat(path.getPublishedAt()).isNotNull();
        }

        @Test
        @DisplayName("空步骤不可发布")
        void shouldThrowWhenPublishWithoutSteps() {
            LearningPath path = learningPath();

            assertThatThrownBy(path::publish)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("学习路径至少需要一个步骤才能发布");
        }

        @Test
        @DisplayName("非草稿态不可发布")
        void shouldThrowWhenPublishNonDraft() {
            LearningPath path = learningPath();
            path.addStep(step("第一课"));
            path.publish();

            assertThatThrownBy(path::publish)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("只能发布草稿状态的学习路径");
        }

        @Test
        @DisplayName("已发布可归档")
        void shouldArchiveFromPublished() {
            LearningPath path = learningPath();
            path.addStep(step("第一课"));
            path.publish();

            path.archive();

            assertThat(path.getStatus()).isEqualTo(LearningPathStatus.ARCHIVED);
        }

        @Test
        @DisplayName("已完成可归档")
        void shouldArchiveFromCompleted() {
            LearningPath path = learningPath();
            path.addStep(step("第一课"));
            path.publish();
            path.complete();

            path.archive();

            assertThat(path.getStatus()).isEqualTo(LearningPathStatus.ARCHIVED);
        }

        @Test
        @DisplayName("草稿态不可归档")
        void shouldThrowWhenArchiveDraft() {
            LearningPath path = learningPath();

            assertThatThrownBy(path::archive)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("只能归档已发布或已完成的学习路径");
        }

        @Test
        @DisplayName("已发布可完成")
        void shouldCompleteFromPublished() {
            LearningPath path = learningPath();
            path.addStep(step("第一课"));
            path.publish();

            path.complete();

            assertThat(path.getStatus()).isEqualTo(LearningPathStatus.COMPLETED);
        }

        @Test
        @DisplayName("草稿态不可完成")
        void shouldThrowWhenCompleteDraft() {
            LearningPath path = learningPath();

            assertThatThrownBy(path::complete)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("只能完成已发布的学习路径");
        }

        @Test
        @DisplayName("状态流转会递增版本")
        void shouldBumpVersionOnTransition() {
            LearningPath path = learningPath();
            path.addStep(step("第一课"));   // version 1
            path.publish();                  // version 2

            assertThat(path.getVersion()).isEqualTo(2L);
        }
    }
}
