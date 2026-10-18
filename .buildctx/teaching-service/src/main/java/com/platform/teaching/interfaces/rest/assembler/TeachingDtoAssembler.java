package com.platform.teaching.interfaces.rest.assembler;

import com.platform.teaching.domain.model.Assignment;
import com.platform.teaching.domain.model.Exam;
import com.platform.teaching.domain.model.Discussion;
import com.platform.teaching.domain.model.LiveSession;
import com.platform.teaching.interfaces.rest.dto.TeachingDtos;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 教学相关 DTO 组装器
 */
@Component
public class TeachingDtoAssembler {

    public TeachingDtos.AssignmentResponse toResponse(Assignment assignment) {
        if (assignment == null) return null;

        return TeachingDtos.AssignmentResponse.builder()
                .id(assignment.getId())
                .tenantId(assignment.getTenantId())
                .courseId(assignment.getCourseId())
                .learningPathId(assignment.getLearningPathId())
                .stepId(assignment.getStepId())
                .title(assignment.getTitle())
                .description(assignment.getDescription())
                .instructions(assignment.getInstructions())
                .status(assignment.getStatus().name())
                .maxScore(assignment.getMaxScore())
                .passingScore(assignment.getPassingScore())
                .allowLateSubmission(assignment.getAllowLateSubmission())
                .latePenaltyPercent(assignment.getLatePenaltyPercent())
                .maxAttempts(assignment.getMaxAttempts())
                .startAt(assignment.getStartAt())
                .dueAt(assignment.getDueAt())
                .createdBy(assignment.getCreatedBy())
                .createdAt(assignment.getCreatedAt())
                .updatedAt(assignment.getUpdatedAt())
                .publishedAt(assignment.getPublishedAt())
                .build();
    }

    public TeachingDtos.ExamResponse toResponse(Exam exam) {
        if (exam == null) return null;

        return TeachingDtos.ExamResponse.builder()
                .id(exam.getId())
                .tenantId(exam.getTenantId())
                .courseId(exam.getCourseId())
                .learningPathId(exam.getLearningPathId())
                .stepId(exam.getStepId())
                .title(exam.getTitle())
                .description(exam.getDescription())
                .instructions(exam.getInstructions())
                .status(exam.getStatus().name())
                .durationMinutes(exam.getDurationMinutes())
                .maxScore(exam.getMaxScore())
                .passingScore(exam.getPassingScore())
                .maxAttempts(exam.getMaxAttempts())
                .shuffleQuestions(exam.getShuffleQuestions())
                .shuffleOptions(exam.getShuffleOptions())
                .showResultsImmediately(exam.getShowResultsImmediately())
                .startAt(exam.getStartAt())
                .endAt(exam.getEndAt())
                .createdBy(exam.getCreatedBy())
                .createdAt(exam.getCreatedAt())
                .updatedAt(exam.getUpdatedAt())
                .publishedAt(exam.getPublishedAt())
                .build();
    }

    public TeachingDtos.DiscussionResponse toResponse(Discussion discussion) {
        if (discussion == null) return null;

        return TeachingDtos.DiscussionResponse.builder()
                .id(discussion.getId())
                .tenantId(discussion.getTenantId())
                .courseId(discussion.getCourseId())
                .learningPathId(discussion.getLearningPathId())
                .stepId(discussion.getStepId())
                .title(discussion.getTitle())
                .content(discussion.getContent())
                .authorId(discussion.getAuthorId())
                .authorName(discussion.getAuthorName())
                .status(discussion.getStatus().name())
                .isPinned(discussion.getIsPinned())
                .isLocked(discussion.getIsLocked())
                .viewCount(discussion.getViewCount())
                .replyCount(discussion.getReplyCount())
                .lastReplyAt(discussion.getLastReplyAt())
                .lastReplyBy(discussion.getLastReplyBy())
                .createdAt(discussion.getCreatedAt())
                .updatedAt(discussion.getUpdatedAt())
                .build();
    }

    public TeachingDtos.LiveSessionResponse toResponse(LiveSession session) {
        if (session == null) return null;

        return TeachingDtos.LiveSessionResponse.builder()
                .id(session.getId())
                .tenantId(session.getTenantId())
                .courseId(session.getCourseId())
                .learningPathId(session.getLearningPathId())
                .stepId(session.getStepId())
                .title(session.getTitle())
                .description(session.getDescription())
                .instructorId(session.getInstructorId())
                .instructorName(session.getInstructorName())
                .status(session.getStatus().name())
                .streamKey(session.getStreamKey())
                .playbackUrl(session.getPlaybackUrl())
                .maxParticipants(session.getMaxParticipants())
                .currentParticipants(session.getCurrentParticipants())
                .scheduledStartAt(session.getScheduledStartAt())
                .scheduledEndAt(session.getScheduledEndAt())
                .actualStartAt(session.getActualStartAt())
                .actualEndAt(session.getActualEndAt())
                .recordingEnabled(session.getRecordingEnabled())
                .chatEnabled(session.getChatEnabled())
                .createdBy(session.getCreatedBy())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .build();
    }

    public TeachingDtos.LiveSessionPageResponse toLiveSessionPageResponse(org.springframework.data.domain.Page<LiveSession> page) {
        if (page == null) return null;

        List<TeachingDtos.LiveSessionResponse> content = page.getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return TeachingDtos.LiveSessionPageResponse.builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }

    public TeachingDtos.AssignmentPageResponse toAssignmentPageResponse(org.springframework.data.domain.Page<Assignment> page) {
        if (page == null) return null;

        List<TeachingDtos.AssignmentResponse> content = page.getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return TeachingDtos.AssignmentPageResponse.builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }

    public TeachingDtos.ExamPageResponse toExamPageResponse(org.springframework.data.domain.Page<Exam> page) {
        if (page == null) return null;

        List<TeachingDtos.ExamResponse> content = page.getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return TeachingDtos.ExamPageResponse.builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }

    public TeachingDtos.DiscussionPageResponse toDiscussionPageResponse(org.springframework.data.domain.Page<Discussion> page) {
        if (page == null) return null;

        List<TeachingDtos.DiscussionResponse> content = page.getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return TeachingDtos.DiscussionPageResponse.builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }
}