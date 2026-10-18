package com.platform.teaching.application.query;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.platform.teaching.domain.model.valueobjects.AssignmentId;
import com.platform.teaching.domain.model.valueobjects.ExamId;
import com.platform.teaching.domain.model.valueobjects.DiscussionId;
import com.platform.teaching.domain.model.valueobjects.LiveSessionId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 作业、考试、讨论、直播相关查询
 */
public class TeachingQueries {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GetAssignmentQuery {
        private AssignmentId id;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GetAssignmentByIdQuery {
        private UUID id;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ListAssignmentsQuery {
        private UUID tenantId;
        private UUID courseId;
        private UUID learningPathId;
        private UUID stepId;
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
    public static class GetOpenAssignmentsQuery {
        private UUID tenantId;
        private UUID userId;
        private LocalDateTime now;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GetExamQuery {
        private ExamId id;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GetExamByIdQuery {
        private UUID id;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ListExamsQuery {
        private UUID tenantId;
        private UUID courseId;
        private UUID learningPathId;
        private UUID stepId;
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
    public static class GetAvailableExamsQuery {
        private UUID tenantId;
        private UUID userId;
        private LocalDateTime now;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GetDiscussionQuery {
        private DiscussionId id;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GetDiscussionByIdQuery {
        private UUID id;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ListDiscussionsQuery {
        private UUID tenantId;
        private UUID courseId;
        private UUID learningPathId;
        private UUID stepId;
        private UUID authorId;
        private String status;
        private Boolean pinnedOnly;
        private int page;
        private int size;
        private String sortBy;
        private String sortDirection;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GetPinnedDiscussionsQuery {
        private UUID tenantId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GetLiveSessionQuery {
        private LiveSessionId id;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GetLiveSessionByIdQuery {
        private UUID id;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ListLiveSessionsQuery {
        private UUID tenantId;
        private UUID courseId;
        private UUID learningPathId;
        private UUID instructorId;
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
    public static class GetUpcomingSessionsQuery {
        private UUID tenantId;
        private LocalDateTime from;
        private LocalDateTime to;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GetLiveSessionsQuery {
        private UUID tenantId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GetUserLiveSessionStatusQuery {
        private UUID userId;
        private LiveSessionId sessionId;
    }
}