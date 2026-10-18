package com.platform.teaching.application.query;

import com.platform.teaching.domain.model.valueobjects.LearningPathId;
import com.platform.teaching.domain.model.valueobjects.StepId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 学习路径相关查询
 */
public class LearningPathQueries {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GetLearningPathQuery {
        private LearningPathId id;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GetLearningPathByIdQuery {
        private UUID id;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ListLearningPathsQuery {
        private UUID tenantId;
        private UUID courseId;
        private String status;
        private int page;
        private int size;
        private String sortBy;
        private String sortDirection;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GetLearningPathStepsQuery {
        private LearningPathId learningPathId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GetStepQuery {
        private StepId stepId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GetStepByIdQuery {
        private UUID stepId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GetUserLearningProgressQuery {
        private UUID userId;
        private LearningPathId learningPathId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GetNextStepQuery {
        private UUID userId;
        private LearningPathId learningPathId;
    }
}