package com.platform.teaching.domain.model;

import com.platform.teaching.domain.model.valueobjects.AssignmentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Assignment 聚合根测试")
class AssignmentTest {

    private Assignment assignment() {
        return Assignment.create(UUID.randomUUID(), UUID.randomUUID(), "第一次作业", "描述", "说明", UUID.randomUUID());
    }

    private Assignment ongoingAssignment() {
        Assignment assignment = assignment();
        assignment.publish();
        assignment.start();
        return assignment;
    }

    @Nested
    @DisplayName("创建作业")
    class Create {

        @Test
        @DisplayName("应创建草稿态作业并带默认分值")
        void shouldCreateDraftAssignment() {
            Assignment assignment = assignment();

            assertThat(assignment.getId()).isNotNull();
            assertThat(assignment.getTitle()).isEqualTo("第一次作业");
            assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.DRAFT);
            assertThat(assignment.getMaxScore()).isEqualTo(100);
            assertThat(assignment.getPassingScore()).isEqualTo(60);
            assertThat(assignment.getMaxAttempts()).isEqualTo(3);
            assertThat(assignment.getAllowLateSubmission()).isFalse();
            assertThat(assignment.getCreatedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("状态流转")
    class StatusTransition {

        @Test
        @DisplayName("草稿可发布")
        void shouldPublish() {
            Assignment assignment = assignment();

            assignment.publish();

            assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.PUBLISHED);
            assertThat(assignment.getPublishedAt()).isNotNull();
        }

        @Test
        @DisplayName("重复发布应抛出异常")
        void shouldThrowWhenPublishTwice() {
            Assignment assignment = assignment();
            assignment.publish();

            assertThatThrownBy(assignment::publish)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("只能发布草稿状态的作业");
        }

        @Test
        @DisplayName("已发布可开始")
        void shouldStartFromPublished() {
            Assignment assignment = assignment();
            assignment.publish();

            assignment.start();

            assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.ONGOING);
        }

        @Test
        @DisplayName("草稿不可开始")
        void shouldThrowWhenStartFromDraft() {
            Assignment assignment = assignment();

            assertThatThrownBy(assignment::start)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("只能开始已发布的作业");
        }

        @Test
        @DisplayName("进行中可结束")
        void shouldEndFromOngoing() {
            Assignment assignment = ongoingAssignment();

            assignment.end();

            assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.ENDED);
        }

        @Test
        @DisplayName("已发布可结束")
        void shouldEndFromPublished() {
            Assignment assignment = assignment();
            assignment.publish();

            assignment.end();

            assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.ENDED);
        }

        @Test
        @DisplayName("草稿不可结束")
        void shouldThrowWhenEndFromDraft() {
            Assignment assignment = assignment();

            assertThatThrownBy(assignment::end)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("只能结束进行中或已发布的作业");
        }

        @Test
        @DisplayName("已结束可归档")
        void shouldArchiveFromEnded() {
            Assignment assignment = ongoingAssignment();
            assignment.end();

            assignment.archive();

            assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.ARCHIVED);
        }

        @Test
        @DisplayName("已发布可归档")
        void shouldArchiveFromPublished() {
            Assignment assignment = assignment();
            assignment.publish();

            assignment.archive();

            assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.ARCHIVED);
        }

        @Test
        @DisplayName("草稿不可归档")
        void shouldThrowWhenArchiveFromDraft() {
            Assignment assignment = assignment();

            assertThatThrownBy(assignment::archive)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("只能归档已结束或已发布的作业");
        }
    }

    @Nested
    @DisplayName("提交窗口")
    class SubmissionWindow {

        @Test
        @DisplayName("进行中且无时间限制可提交")
        void shouldBeOpenWithoutWindow() {
            Assignment assignment = ongoingAssignment();

            assertThat(assignment.isOpenForSubmission(LocalDateTime.now())).isTrue();
        }

        @Test
        @DisplayName("开始时间之前不可提交")
        void shouldBeClosedBeforeStart() {
            Assignment assignment = ongoingAssignment();
            assignment.setStartAt(LocalDateTime.now().plusDays(1));

            assertThat(assignment.isOpenForSubmission(LocalDateTime.now())).isFalse();
        }

        @Test
        @DisplayName("截止时间之后不可提交")
        void shouldBeClosedAfterDue() {
            Assignment assignment = ongoingAssignment();
            assignment.setDueAt(LocalDateTime.now().minusDays(1));

            assertThat(assignment.isOpenForSubmission(LocalDateTime.now())).isFalse();
        }

        @Test
        @DisplayName("允许迟交时截止后仍可提交")
        void shouldBeOpenAfterDueWhenLateAllowed() {
            Assignment assignment = ongoingAssignment();
            assignment.setDueAt(LocalDateTime.now().minusDays(1));
            assignment.setAllowLateSubmission(true);

            assertThat(assignment.isOpenForSubmission(LocalDateTime.now())).isTrue();
        }

        @Test
        @DisplayName("草稿态不可提交")
        void shouldBeClosedWhenDraft() {
            Assignment assignment = assignment();

            assertThat(assignment.isOpenForSubmission(LocalDateTime.now())).isFalse();
        }
    }
}
