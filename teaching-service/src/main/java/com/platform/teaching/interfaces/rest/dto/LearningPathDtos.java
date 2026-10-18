package com.platform.teaching.interfaces.rest.dto;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.platform.teaching.domain.model.valueobjects.LearningPathId;
import com.platform.teaching.domain.model.valueobjects.StepId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * 学习路径相关 DTO
 */
public class LearningPathDtos {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateLearningPathRequest {
        private UUID tenantId;
        private UUID courseId;
        private String title;
        private String description;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateLearningPathRequest {
        private String title;
        private String description;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LearningPathResponse {
        private UUID id;
        private UUID tenantId;
        private UUID courseId;
        private String title;
        private String description;
        private String status;
        private Long version;
        private UUID createdBy;
        private java.time.LocalDateTime createdAt;
        private java.time.LocalDateTime updatedAt;
        private java.time.LocalDateTime publishedAt;
        private List<StepResponse> steps;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StepResponse {
        private UUID id;
        private Integer sequence;
        private String stepType;
        private String title;
        private String description;
        private UUID contentId;
        private String contentType;
        private Integer estimatedMinutes;
        private Boolean isRequired;
        private String status;
        private java.time.LocalDateTime startedAt;
        private java.time.LocalDateTime completedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddStepRequest {
        private String stepType;
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
    public static class UpdateStepRequest {
        private String title;
        private String description;
        private Integer estimatedMinutes;
        private Boolean isRequired;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReorderStepsRequest {
        private List<StepOrder> stepOrders;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class StepOrder {
            private UUID stepId;
            private Integer sequence;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StartStepRequest {
        private UUID userId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompleteStepRequest {
        private UUID userId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkipStepRequest {
        private UUID userId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LearningPathPageResponse {
        private List<LearningPathResponse> content;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
        private boolean first;
        private boolean last;
    }
}