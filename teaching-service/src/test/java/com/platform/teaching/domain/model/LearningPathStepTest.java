package com.platform.teaching.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("LearningPathStep 实体测试")
class LearningPathStepTest {

    private LearningPathStep step() {
        return new LearningPathStep(UUID.randomUUID(), null, 1, StepType.VIDEO, "第一课", "描述",
                UUID.randomUUID(), "video/mp4", 15, true);
    }

    @Test
    @DisplayName("新建步骤默认为未开始")
    void shouldDefaultToNotStarted() {
        LearningPathStep step = step();

        assertThat(step.getId()).isNotNull();
        assertThat(step.getStatus()).isEqualTo(StepStatus.NOT_STARTED);
        assertThat(step.getStepType()).isEqualTo(StepType.VIDEO);
        assertThat(step.getIsRequired()).isTrue();
        assertThat(step.getCreatedAt()).isNotNull();
        assertThat(step.getStartedAt()).isNull();
        assertThat(step.getCompletedAt()).isNull();
    }

    @Test
    @DisplayName("未开始步骤可开始")
    void shouldStart() {
        LearningPathStep step = step();

        step.start();

        assertThat(step.getStatus()).isEqualTo(StepStatus.IN_PROGRESS);
        assertThat(step.getStartedAt()).isNotNull();
    }

    @Test
    @DisplayName("进行中步骤不可重复开始")
    void shouldThrowWhenStartTwice() {
        LearningPathStep step = step();
        step.start();

        assertThatThrownBy(step::start)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("只能开始未开始的步骤");
    }

    @Test
    @DisplayName("进行中步骤可完成")
    void shouldComplete() {
        LearningPathStep step = step();
        step.start();

        step.complete();

        assertThat(step.getStatus()).isEqualTo(StepStatus.COMPLETED);
        assertThat(step.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("未开始步骤不可直接完成")
    void shouldThrowWhenCompleteWithoutStart() {
        LearningPathStep step = step();

        assertThatThrownBy(step::complete)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("只能完成进行中的步骤");
    }

    @Test
    @DisplayName("未开始步骤可跳过")
    void shouldSkip() {
        LearningPathStep step = step();

        step.skip();

        assertThat(step.getStatus()).isEqualTo(StepStatus.SKIPPED);
    }

    @Test
    @DisplayName("已完成步骤不可跳过")
    void shouldThrowWhenSkipCompleted() {
        LearningPathStep step = step();
        step.start();
        step.complete();

        assertThatThrownBy(step::skip)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已完成的步骤不能跳过");
    }
}
