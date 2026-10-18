package com.platform.teaching.application.command;

import com.platform.teaching.domain.model.valueobjects.LearningPathId;
import com.platform.teaching.domain.model.valueobjects.StepId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * 学习路径相关命令
 */
public class LearningPathCommands {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateLearningPathCommand {
        private UUID tenantId;
        private UUID courseId;
        private String title;
        private String description;
        private UUID createdBy;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateLearningPathCommand {
        private LearningPathId id;
        private String title;
        private String description;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PublishLearningPathCommand {
        private LearningPathId id;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ArchiveLearningPathCommand {
        private LearningPathId id;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompleteLearningPathCommand {
        private LearningPathId id;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeleteLearningPathCommand {
        private LearningPathId id;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddStepCommand {
        private LearningPathId learningPathId;
        private StepType stepType;
        private String title;
        private String description;
        private UUID contentId;
        private String contentType;
        private Integer estimatedMinutes;
        private Boolean isRequired;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateStepCommand {
        private StepId stepId;
        private String title;
        private String description;
        private Integer estimatedMinutes;
        private Boolean isRequired;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RemoveStepCommand {
        private StepId stepId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReorderStepsCommand {
        private LearningPathId learningPathId;
        private List<StepOrder> stepOrders;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class StepOrder {
            private StepId stepId;
            private Integer sequence;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StartStepCommand {
        private StepId stepId;
        private UUID userId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompleteStepCommand {
        private StepId stepId;
        private UUID userId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkipStepCommand {
        private StepId stepId;
        private UUID userId;
    }

    public enum StepType {
        VIDEO, DOCUMENT, QUIZ, ASSIGNMENT, EXAM, DISCUSSION, LIVE_SESSION, PRACTICE, READING, PROJECT
    }
}