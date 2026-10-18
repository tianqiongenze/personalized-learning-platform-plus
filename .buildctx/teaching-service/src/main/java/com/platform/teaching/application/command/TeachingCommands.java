package com.platform.teaching.application.command;

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
 * 作业、考试、讨论、直播相关命令
 */
public class TeachingCommands {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateAssignmentCommand {
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
        private UUID createdBy;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateAssignmentCommand {
        private AssignmentId id;
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
    public static class PublishAssignmentCommand {
        private AssignmentId id;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StartAssignmentCommand {
        private AssignmentId id;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EndAssignmentCommand {
        private AssignmentId id;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ArchiveAssignmentCommand {
        private AssignmentId id;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeleteAssignmentCommand {
        private AssignmentId id;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateExamCommand {
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
        private UUID createdBy;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateExamCommand {
        private ExamId id;
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
    public static class PublishExamCommand {
        private ExamId id;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StartExamCommand {
        private ExamId id;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EndExamCommand {
        private ExamId id;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ArchiveExamCommand {
        private ExamId id;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeleteExamCommand {
        private ExamId id;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateDiscussionCommand {
        private UUID tenantId;
        private UUID courseId;
        private UUID learningPathId;
        private UUID stepId;
        private String title;
        private String content;
        private UUID authorId;
        private String authorName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateDiscussionCommand {
        private DiscussionId id;
        private String title;
        private String content;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CloseDiscussionCommand {
        private DiscussionId id;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReopenDiscussionCommand {
        private DiscussionId id;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PinDiscussionCommand {
        private DiscussionId id;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UnpinDiscussionCommand {
        private DiscussionId id;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LockDiscussionCommand {
        private DiscussionId id;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UnlockDiscussionCommand {
        private DiscussionId id;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeleteDiscussionCommand {
        private DiscussionId id;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateLiveSessionCommand {
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
        private UUID createdBy;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateLiveSessionCommand {
        private LiveSessionId id;
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
    public static class PrepareLiveSessionCommand {
        private LiveSessionId id;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StartLiveSessionCommand {
        private LiveSessionId id;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EndLiveSessionCommand {
        private LiveSessionId id;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CancelLiveSessionCommand {
        private LiveSessionId id;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JoinLiveSessionCommand {
        private LiveSessionId id;
        private UUID participantId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LeaveLiveSessionCommand {
        private LiveSessionId id;
        private UUID participantId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SetPlaybackUrlCommand {
        private LiveSessionId id;
        private String playbackUrl;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeleteLiveSessionCommand {
        private LiveSessionId id;
    }
}