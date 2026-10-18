package com.platform.teaching.domain.model;

import com.platform.teaching.domain.model.valueobjects.ExamStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Exam 聚合根测试")
class ExamTest {

    private Exam exam() {
        return Exam.create(UUID.randomUUID(), UUID.randomUUID(), "期中考试", "描述", "说明", UUID.randomUUID());
    }

    private Exam ongoingExam() {
        Exam exam = exam();
        exam.publish();
        exam.start();
        return exam;
    }

    @Nested
    @DisplayName("创建考试")
    class Create {

        @Test
        @DisplayName("应创建草稿态考试并带默认配置")
        void shouldCreateDraftExam() {
            Exam exam = exam();

            assertThat(exam.getId()).isNotNull();
            assertThat(exam.getTitle()).isEqualTo("期中考试");
            assertThat(exam.getStatus()).isEqualTo(ExamStatus.DRAFT);
            assertThat(exam.getDurationMinutes()).isEqualTo(60);
            assertThat(exam.getMaxScore()).isEqualTo(100);
            assertThat(exam.getMaxAttempts()).isEqualTo(1);
            assertThat(exam.getShuffleQuestions()).isTrue();
            assertThat(exam.getShowResultsImmediately()).isTrue();
        }
    }

    @Nested
    @DisplayName("状态流转")
    class StatusTransition {

        @Test
        @DisplayName("草稿可发布")
        void shouldPublish() {
            Exam exam = exam();

            exam.publish();

            assertThat(exam.getStatus()).isEqualTo(ExamStatus.PUBLISHED);
            assertThat(exam.getPublishedAt()).isNotNull();
        }

        @Test
        @DisplayName("重复发布应抛出异常")
        void shouldThrowWhenPublishTwice() {
            Exam exam = exam();
            exam.publish();

            assertThatThrownBy(exam::publish)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("只能发布草稿状态的考试");
        }

        @Test
        @DisplayName("已发布可开始")
        void shouldStartFromPublished() {
            Exam exam = exam();
            exam.publish();

            exam.start();

            assertThat(exam.getStatus()).isEqualTo(ExamStatus.ONGOING);
        }

        @Test
        @DisplayName("进行中可重复开始")
        void shouldStartFromOngoing() {
            Exam exam = ongoingExam();

            exam.start();

            assertThat(exam.getStatus()).isEqualTo(ExamStatus.ONGOING);
        }

        @Test
        @DisplayName("草稿不可开始")
        void shouldThrowWhenStartFromDraft() {
            Exam exam = exam();

            assertThatThrownBy(exam::start)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("只能开始已发布或进行中的考试");
        }

        @Test
        @DisplayName("进行中可结束")
        void shouldEndFromOngoing() {
            Exam exam = ongoingExam();

            exam.end();

            assertThat(exam.getStatus()).isEqualTo(ExamStatus.ENDED);
        }

        @Test
        @DisplayName("已发布可结束")
        void shouldEndFromPublished() {
            Exam exam = exam();
            exam.publish();

            exam.end();

            assertThat(exam.getStatus()).isEqualTo(ExamStatus.ENDED);
        }

        @Test
        @DisplayName("草稿不可结束")
        void shouldThrowWhenEndFromDraft() {
            Exam exam = exam();

            assertThatThrownBy(exam::end)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("只能结束进行中或已发布的考试");
        }

        @Test
        @DisplayName("已结束可归档")
        void shouldArchiveFromEnded() {
            Exam exam = ongoingExam();
            exam.end();

            exam.archive();

            assertThat(exam.getStatus()).isEqualTo(ExamStatus.ARCHIVED);
        }

        @Test
        @DisplayName("已发布可归档")
        void shouldArchiveFromPublished() {
            Exam exam = exam();
            exam.publish();

            exam.archive();

            assertThat(exam.getStatus()).isEqualTo(ExamStatus.ARCHIVED);
        }

        @Test
        @DisplayName("草稿不可归档")
        void shouldThrowWhenArchiveFromDraft() {
            Exam exam = exam();

            assertThatThrownBy(exam::archive)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("只能归档已结束或已发布的考试");
        }
    }

    @Nested
    @DisplayName("开考窗口")
    class AttemptWindow {

        @Test
        @DisplayName("进行中且无时间限制可开考")
        void shouldBeOpenWithoutWindow() {
            Exam exam = ongoingExam();

            assertThat(exam.isOpenForAttempt(LocalDateTime.now())).isTrue();
        }

        @Test
        @DisplayName("开始时间之前不可开考")
        void shouldBeClosedBeforeStart() {
            Exam exam = ongoingExam();
            exam.setStartAt(LocalDateTime.now().plusDays(1));

            assertThat(exam.isOpenForAttempt(LocalDateTime.now())).isFalse();
        }

        @Test
        @DisplayName("结束时间之后不可开考")
        void shouldBeClosedAfterEnd() {
            Exam exam = ongoingExam();
            exam.setEndAt(LocalDateTime.now().minusHours(1));

            assertThat(exam.isOpenForAttempt(LocalDateTime.now())).isFalse();
        }

        @Test
        @DisplayName("草稿态不可开考")
        void shouldBeClosedWhenDraft() {
            Exam exam = exam();

            assertThat(exam.isOpenForAttempt(LocalDateTime.now())).isFalse();
        }
    }
}
