package com.platform.teaching.interfaces.rest.dto;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.platform.teaching.domain.model.valueobjects.AssignmentId;
import com.platform.teaching.domain.model.valueobjects.ExamId;
import com.platform.teaching.domain.model.valueobjects.DiscussionId;
import com.platform.teaching.domain.model.valueobjects.LiveSessionId;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 作业、考试、讨论、直播相关 DTO
 */
public class TeachingDtos {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateAssignmentRequest {
        private UUID tenantId;
        private UUID courseId;
        private UUID learningPathId;
        private UUID stepId;
        private String title;
        private String description;
        private String instructions;
        private Integer maxScore;
        private Integer passingScore;
        private Boolean allowLateSubmission;
        private Integer latePenaltyPercent;
        private Integer maxAttempts;
        private LocalDateTime startAt;
        private LocalDateTime dueAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateAssignmentRequest {
        private String title;
        private String description;
        private String instructions;
        private Integer maxScore;
        private Integer passingScore;
        private Boolean allowLateSubmission;
        private Integer latePenaltyPercent;
        private Integer maxAttempts;
        private LocalDateTime startAt;
        private LocalDateTime dueAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssignmentResponse {
        private UUID id;
        private UUID tenantId;
        private UUID courseId;
        private UUID learningPathId;
        private UUID stepId;
        private String title;
        private String description;
        private String instructions;
        private String status;
        private Integer maxScore;
        private Integer passingScore;
        private Boolean allowLateSubmission;
        private Integer latePenaltyPercent;
        private Integer maxAttempts;
        private LocalDateTime startAt;
        private LocalDateTime dueAt;
        private UUID createdBy;
        private java.time.LocalDateTime createdAt;
        private java.time.LocalDateTime updatedAt;
        private java.time.LocalDateTime publishedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssignmentPageResponse {
        private List<AssignmentResponse> content;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
        private boolean first;
        private boolean last;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateExamRequest {
        private UUID tenantId;
        private UUID courseId;
        private UUID learningPathId;
        private UUID stepId;
        private String title;
        private String description;
        private String instructions;
        private Integer durationMinutes;
        private Integer maxScore;
        private Integer passingScore;
        private Integer maxAttempts;
        private Boolean shuffleQuestions;
        private Boolean shuffleOptions;
        private Boolean showResultsImmediately;
        private LocalDateTime startAt;
        private LocalDateTime endAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateExamRequest {
        private String title;
        private String description;
        private String instructions;
        private Integer durationMinutes;
        private Integer maxScore;
        private Integer passingScore;
        private Integer maxAttempts;
        private Boolean shuffleQuestions;
        private Boolean shuffleOptions;
        private Boolean showResultsImmediately;
        private LocalDateTime startAt;
        private LocalDateTime endAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExamResponse {
        private UUID id;
        private UUID tenantId;
        private UUID courseId;
        private UUID learningPathId;
        private UUID stepId;
        private String title;
        private String description;
        private String instructions;
        private String status;
        private Integer durationMinutes;
        private Integer maxScore;
        private Integer passingScore;
        private Integer maxAttempts;
        private Boolean shuffleQuestions;
        private Boolean shuffleOptions;
        private Boolean showResultsImmediately;
        private LocalDateTime startAt;
        private LocalDateTime endAt;
        private UUID createdBy;
        private java.time.LocalDateTime createdAt;
        private java.time.LocalDateTime updatedAt;
        private java.time.LocalDateTime publishedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExamPageResponse {
        private List<ExamResponse> content;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
        private boolean first;
        private boolean last;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateDiscussionRequest {
        private UUID tenantId;
        private UUID courseId;
        private UUID learningPathId;
        private UUID stepId;
        private String title;
        private String content;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateDiscussionRequest {
        private String title;
        private String content;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiscussionResponse {
        private UUID id;
        private UUID tenantId;
        private UUID courseId;
        private UUID learningPathId;
        private UUID stepId;
        private String title;
        private String content;
        private UUID authorId;
        private String authorName;
        private String status;
        private Boolean isPinned;
        private Boolean isLocked;
        private Long viewCount;
        private Long replyCount;
        private java.time.LocalDateTime lastReplyAt;
        private UUID lastReplyBy;
        private java.time.LocalDateTime createdAt;
        private java.time.LocalDateTime updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiscussionPageResponse {
        private List<DiscussionResponse> content;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
        private boolean first;
        private boolean last;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateLiveSessionRequest {
        private UUID tenantId;
        private UUID courseId;
        private UUID learningPathId;
        private UUID stepId;
        private String title;
        private String description;
        private UUID instructorId;
        private String instructorName;
        private LocalDateTime scheduledStartAt;
        private LocalDateTime scheduledEndAt;
        private Integer maxParticipants;
        private Boolean recordingEnabled;
        private Boolean chatEnabled;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateLiveSessionRequest {
        private String title;
        private String description;
        private LocalDateTime scheduledStartAt;
        private LocalDateTime scheduledEndAt;
        private Integer maxParticipants;
        private Boolean recordingEnabled;
        private Boolean chatEnabled;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LiveSessionResponse {
        private UUID id;
        private UUID tenantId;
        private UUID courseId;
        private UUID learningPathId;
        private UUID stepId;
        private String title;
        private String description;
        private UUID instructorId;
        private String instructorName;
        private String status;
        private String streamKey;
        private String playbackUrl;
        private Integer maxParticipants;
        private Integer currentParticipants;
        private LocalDateTime scheduledStartAt;
        private LocalDateTime scheduledEndAt;
        private LocalDateTime actualStartAt;
        private LocalDateTime actualEndAt;
        private Boolean recordingEnabled;
        private Boolean chatEnabled;
        private UUID createdBy;
        private java.time.LocalDateTime createdAt;
        private java.time.LocalDateTime updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LiveSessionPageResponse {
        private List<LiveSessionResponse> content;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
        private boolean first;
        private boolean last;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JoinLiveSessionRequest {
        private UUID participantId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SetPlaybackUrlRequest {
        private String playbackUrl;
    }
}