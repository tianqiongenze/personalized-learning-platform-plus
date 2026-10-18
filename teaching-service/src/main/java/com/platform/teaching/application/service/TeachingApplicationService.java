package com.platform.teaching.application.service;

import com.platform.teaching.application.command.LearningPathCommands;
import com.platform.teaching.application.command.TeachingCommands;
import com.platform.teaching.application.query.LearningPathQueries;
import com.platform.teaching.application.query.TeachingQueries;
import com.platform.teaching.domain.model.LearningPath;
import com.platform.teaching.domain.model.LearningPathStatus;
import com.platform.teaching.domain.model.valueobjects.ExamStatus;
import com.platform.teaching.domain.model.valueobjects.AssignmentStatus;
import com.platform.teaching.domain.model.valueobjects.DiscussionStatus;
import com.platform.teaching.domain.model.valueobjects.LiveSessionStatus;
import com.platform.teaching.domain.model.StepStatus;
import com.platform.teaching.domain.model.StepType;
import com.platform.teaching.domain.model.LearningPathStep;
import com.platform.teaching.domain.model.Assignment;
import com.platform.teaching.domain.model.Exam;
import com.platform.teaching.domain.model.Discussion;
import com.platform.teaching.domain.model.LiveSession;
import com.platform.teaching.domain.model.valueobjects.LearningPathId;
import com.platform.teaching.domain.model.valueobjects.StepId;
import com.platform.teaching.domain.model.valueobjects.AssignmentId;
import com.platform.teaching.domain.model.valueobjects.ExamId;
import com.platform.teaching.domain.model.valueobjects.DiscussionId;
import com.platform.teaching.domain.model.valueobjects.LiveSessionId;
import com.platform.teaching.domain.repository.LearningPathRepository;
import com.platform.teaching.domain.repository.LearningPathStepRepository;
import com.platform.teaching.domain.repository.AssignmentRepository;
import com.platform.teaching.domain.repository.ExamRepository;
import com.platform.teaching.domain.repository.DiscussionRepository;
import com.platform.teaching.domain.repository.LiveSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 教学服务应用层 - 处理所有命令和查询
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TeachingApplicationService {

    private final LearningPathRepository learningPathRepository;
    private final LearningPathStepRepository stepRepository;
    private final AssignmentRepository assignmentRepository;
    private final ExamRepository examRepository;
    private final DiscussionRepository discussionRepository;
    private final LiveSessionRepository liveSessionRepository;

    // ==========================================
    // Learning Path Commands
    // ==========================================

    public LearningPath createLearningPath(LearningPathCommands.CreateLearningPathCommand cmd) {
        LearningPath learningPath = LearningPath.create(
                cmd.getTenantId(),
                cmd.getCourseId(),
                cmd.getTitle(),
                cmd.getDescription(),
                cmd.getCreatedBy()
        );
        return learningPathRepository.save(learningPath);
    }

    public LearningPath updateLearningPath(LearningPathCommands.UpdateLearningPathCommand cmd) {
        LearningPath learningPath = learningPathRepository.findById(cmd.getId())
                .orElseThrow(() -> new IllegalArgumentException("学习路径不存在: " + cmd.getId()));
        
        // Update logic would go here
        return learningPathRepository.save(learningPath);
    }

    public LearningPath publishLearningPath(LearningPathCommands.PublishLearningPathCommand cmd) {
        LearningPath learningPath = learningPathRepository.findById(cmd.getId())
                .orElseThrow(() -> new IllegalArgumentException("学习路径不存在: " + cmd.getId()));
        learningPath.publish();
        return learningPathRepository.save(learningPath);
    }

    public LearningPath archiveLearningPath(LearningPathCommands.ArchiveLearningPathCommand cmd) {
        LearningPath learningPath = learningPathRepository.findById(cmd.getId())
                .orElseThrow(() -> new IllegalArgumentException("学习路径不存在: " + cmd.getId()));
        learningPath.archive();
        return learningPathRepository.save(learningPath);
    }

    public LearningPath completeLearningPath(LearningPathCommands.CompleteLearningPathCommand cmd) {
        LearningPath learningPath = learningPathRepository.findById(cmd.getId())
                .orElseThrow(() -> new IllegalArgumentException("学习路径不存在: " + cmd.getId()));
        learningPath.complete();
        return learningPathRepository.save(learningPath);
    }

    public void deleteLearningPath(LearningPathCommands.DeleteLearningPathCommand cmd) {
        learningPathRepository.deleteById(cmd.getId());
    }

    public LearningPathStep addStep(LearningPathCommands.AddStepCommand cmd) {
        LearningPath learningPath = learningPathRepository.findById(cmd.getLearningPathId())
                .orElseThrow(() -> new IllegalArgumentException("学习路径不存在: " + cmd.getLearningPathId()));

        LearningPathStep step = new LearningPathStep(
                StepId.generate().getValue(),
                learningPath,
                learningPath.getSteps().size() + 1,
                StepType.valueOf(cmd.getStepType().name()),
                cmd.getTitle(),
                cmd.getDescription(),
                cmd.getContentId(),
                cmd.getContentType(),
                cmd.getEstimatedMinutes(),
                cmd.getIsRequired()
        );

        learningPath.addStep(step);
        learningPathRepository.save(learningPath);
        return step;
    }

    public LearningPathStep updateStep(LearningPathCommands.UpdateStepCommand cmd) {
        LearningPathStep step = stepRepository.findById(cmd.getStepId())
                .orElseThrow(() -> new IllegalArgumentException("步骤不存在: " + cmd.getStepId()));
        // Update logic
        return stepRepository.save(step);
    }

    public void removeStep(LearningPathCommands.RemoveStepCommand cmd) {
        LearningPathStep step = stepRepository.findById(cmd.getStepId())
                .orElseThrow(() -> new IllegalArgumentException("步骤不存在: " + cmd.getStepId()));
        LearningPath learningPath = step.getLearningPath();
        learningPath.removeStep(cmd.getStepId());
        learningPathRepository.save(learningPath);
    }

    public void reorderSteps(LearningPathCommands.ReorderStepsCommand cmd) {
        LearningPath learningPath = learningPathRepository.findById(cmd.getLearningPathId())
                .orElseThrow(() -> new IllegalArgumentException("学习路径不存在: " + cmd.getLearningPathId()));
        // Reorder logic
        learningPathRepository.save(learningPath);
    }

    public void startStep(LearningPathCommands.StartStepCommand cmd) {
        LearningPathStep step = stepRepository.findById(cmd.getStepId())
                .orElseThrow(() -> new IllegalArgumentException("步骤不存在: " + cmd.getStepId()));
        step.start();
        stepRepository.save(step);
    }

    public void completeStep(LearningPathCommands.CompleteStepCommand cmd) {
        LearningPathStep step = stepRepository.findById(cmd.getStepId())
                .orElseThrow(() -> new IllegalArgumentException("步骤不存在: " + cmd.getStepId()));
        step.complete();
        stepRepository.save(step);
    }

    public void skipStep(LearningPathCommands.SkipStepCommand cmd) {
        LearningPathStep step = stepRepository.findById(cmd.getStepId())
                .orElseThrow(() -> new IllegalArgumentException("步骤不存在: " + cmd.getStepId()));
        step.skip();
        stepRepository.save(step);
    }

    // ==========================================
    // Assignment Commands
    // ==========================================

    public Assignment createAssignment(TeachingCommands.CreateAssignmentCommand cmd) {
        Assignment assignment = Assignment.create(
                cmd.getTenantId(),
                cmd.getCourseId(),
                cmd.getTitle(),
                cmd.getDescription(),
                cmd.getInstructions(),
                cmd.getCreatedBy()
        );
        // Set additional fields
        if (cmd.getLearningPathId() != null) assignment.setLearningPathId(cmd.getLearningPathId());
        if (cmd.getStepId() != null) assignment.setStepId(cmd.getStepId());
        if (cmd.getMaxScore() != null) assignment.setMaxScore(cmd.getMaxScore());
        if (cmd.getPassingScore() != null) assignment.setPassingScore(cmd.getPassingScore());
        if (cmd.getAllowLateSubmission() != null) assignment.setAllowLateSubmission(cmd.getAllowLateSubmission());
        if (cmd.getLatePenaltyPercent() != null) assignment.setLatePenaltyPercent(cmd.getLatePenaltyPercent());
        if (cmd.getMaxAttempts() != null) assignment.setMaxAttempts(cmd.getMaxAttempts());
        if (cmd.getStartAt() != null) assignment.setStartAt(cmd.getStartAt());
        if (cmd.getDueAt() != null) assignment.setDueAt(cmd.getDueAt());

        return assignmentRepository.save(assignment);
    }

    public Assignment updateAssignment(TeachingCommands.UpdateAssignmentCommand cmd) {
        Assignment assignment = assignmentRepository.findById(cmd.getId())
                .orElseThrow(() -> new IllegalArgumentException("作业不存在: " + cmd.getId()));
        // Update logic
        return assignmentRepository.save(assignment);
    }

    public Assignment publishAssignment(TeachingCommands.PublishAssignmentCommand cmd) {
        Assignment assignment = assignmentRepository.findById(cmd.getId())
                .orElseThrow(() -> new IllegalArgumentException("作业不存在: " + cmd.getId()));
        assignment.publish();
        return assignmentRepository.save(assignment);
    }

    public Assignment startAssignment(TeachingCommands.StartAssignmentCommand cmd) {
        Assignment assignment = assignmentRepository.findById(cmd.getId())
                .orElseThrow(() -> new IllegalArgumentException("作业不存在: " + cmd.getId()));
        assignment.start();
        return assignmentRepository.save(assignment);
    }

    public Assignment endAssignment(TeachingCommands.EndAssignmentCommand cmd) {
        Assignment assignment = assignmentRepository.findById(cmd.getId())
                .orElseThrow(() -> new IllegalArgumentException("作业不存在: " + cmd.getId()));
        assignment.end();
        return assignmentRepository.save(assignment);
    }

    public Assignment archiveAssignment(TeachingCommands.ArchiveAssignmentCommand cmd) {
        Assignment assignment = assignmentRepository.findById(cmd.getId())
                .orElseThrow(() -> new IllegalArgumentException("作业不存在: " + cmd.getId()));
        assignment.archive();
        return assignmentRepository.save(assignment);
    }

    public void deleteAssignment(TeachingCommands.DeleteAssignmentCommand cmd) {
        assignmentRepository.deleteById(cmd.getId());
    }

    // ==========================================
    // Exam Commands
    // ==========================================

    public Exam createExam(TeachingCommands.CreateExamCommand cmd) {
        Exam exam = Exam.create(
                cmd.getTenantId(),
                cmd.getCourseId(),
                cmd.getTitle(),
                cmd.getDescription(),
                cmd.getInstructions(),
                cmd.getCreatedBy()
        );
        // Set additional fields
        if (cmd.getLearningPathId() != null) exam.setLearningPathId(cmd.getLearningPathId());
        if (cmd.getStepId() != null) exam.setStepId(cmd.getStepId());
        if (cmd.getDurationMinutes() != null) exam.setDurationMinutes(cmd.getDurationMinutes());
        if (cmd.getMaxScore() != null) exam.setMaxScore(cmd.getMaxScore());
        if (cmd.getPassingScore() != null) exam.setPassingScore(cmd.getPassingScore());
        if (cmd.getMaxAttempts() != null) exam.setMaxAttempts(cmd.getMaxAttempts());
        if (cmd.getShuffleQuestions() != null) exam.setShuffleQuestions(cmd.getShuffleQuestions());
        if (cmd.getShuffleOptions() != null) exam.setShuffleOptions(cmd.getShuffleOptions());
        if (cmd.getShowResultsImmediately() != null) exam.setShowResultsImmediately(cmd.getShowResultsImmediately());
        if (cmd.getStartAt() != null) exam.setStartAt(cmd.getStartAt());
        if (cmd.getEndAt() != null) exam.setEndAt(cmd.getEndAt());

        return examRepository.save(exam);
    }

    public Exam updateExam(TeachingCommands.UpdateExamCommand cmd) {
        Exam exam = examRepository.findById(cmd.getId())
                .orElseThrow(() -> new IllegalArgumentException("考试不存在: " + cmd.getId()));
        // Update logic
        return examRepository.save(exam);
    }

    public Exam publishExam(TeachingCommands.PublishExamCommand cmd) {
        Exam exam = examRepository.findById(cmd.getId())
                .orElseThrow(() -> new IllegalArgumentException("考试不存在: " + cmd.getId()));
        exam.publish();
        return examRepository.save(exam);
    }

    public Exam startExam(TeachingCommands.StartExamCommand cmd) {
        Exam exam = examRepository.findById(cmd.getId())
                .orElseThrow(() -> new IllegalArgumentException("考试不存在: " + cmd.getId()));
        exam.start();
        return examRepository.save(exam);
    }

    public Exam endExam(TeachingCommands.EndExamCommand cmd) {
        Exam exam = examRepository.findById(cmd.getId())
                .orElseThrow(() -> new IllegalArgumentException("考试不存在: " + cmd.getId()));
        exam.end();
        return examRepository.save(exam);
    }

    public Exam archiveExam(TeachingCommands.ArchiveExamCommand cmd) {
        Exam exam = examRepository.findById(cmd.getId())
                .orElseThrow(() -> new IllegalArgumentException("考试不存在: " + cmd.getId()));
        exam.archive();
        return examRepository.save(exam);
    }

    public void deleteExam(TeachingCommands.DeleteExamCommand cmd) {
        examRepository.deleteById(cmd.getId());
    }

    // ==========================================
    // Discussion Commands
    // ==========================================

    public Discussion createDiscussion(TeachingCommands.CreateDiscussionCommand cmd) {
        Discussion discussion = Discussion.create(
                cmd.getTenantId(),
                cmd.getCourseId(),
                cmd.getTitle(),
                cmd.getContent(),
                cmd.getAuthorId(),
                cmd.getAuthorName()
        );
        if (cmd.getLearningPathId() != null) discussion.setLearningPathId(cmd.getLearningPathId());
        if (cmd.getStepId() != null) discussion.setStepId(cmd.getStepId());
        return discussionRepository.save(discussion);
    }

    public Discussion updateDiscussion(TeachingCommands.UpdateDiscussionCommand cmd) {
        Discussion discussion = discussionRepository.findById(cmd.getId())
                .orElseThrow(() -> new IllegalArgumentException("讨论不存在: " + cmd.getId()));
        // Update logic
        return discussionRepository.save(discussion);
    }

    public Discussion closeDiscussion(TeachingCommands.CloseDiscussionCommand cmd) {
        Discussion discussion = discussionRepository.findById(cmd.getId())
                .orElseThrow(() -> new IllegalArgumentException("讨论不存在: " + cmd.getId()));
        discussion.close();
        return discussionRepository.save(discussion);
    }

    public Discussion reopenDiscussion(TeachingCommands.ReopenDiscussionCommand cmd) {
        Discussion discussion = discussionRepository.findById(cmd.getId())
                .orElseThrow(() -> new IllegalArgumentException("讨论不存在: " + cmd.getId()));
        discussion.reopen();
        return discussionRepository.save(discussion);
    }

    public Discussion pinDiscussion(TeachingCommands.PinDiscussionCommand cmd) {
        Discussion discussion = discussionRepository.findById(cmd.getId())
                .orElseThrow(() -> new IllegalArgumentException("讨论不存在: " + cmd.getId()));
        discussion.pin();
        return discussionRepository.save(discussion);
    }

    public Discussion unpinDiscussion(TeachingCommands.UnpinDiscussionCommand cmd) {
        Discussion discussion = discussionRepository.findById(cmd.getId())
                .orElseThrow(() -> new IllegalArgumentException("讨论不存在: " + cmd.getId()));
        discussion.unpin();
        return discussionRepository.save(discussion);
    }

    public Discussion lockDiscussion(TeachingCommands.LockDiscussionCommand cmd) {
        Discussion discussion = discussionRepository.findById(cmd.getId())
                .orElseThrow(() -> new IllegalArgumentException("讨论不存在: " + cmd.getId()));
        discussion.lock();
        return discussionRepository.save(discussion);
    }

    public Discussion unlockDiscussion(TeachingCommands.UnlockDiscussionCommand cmd) {
        Discussion discussion = discussionRepository.findById(cmd.getId())
                .orElseThrow(() -> new IllegalArgumentException("讨论不存在: " + cmd.getId()));
        discussion.unlock();
        return discussionRepository.save(discussion);
    }

    public void deleteDiscussion(TeachingCommands.DeleteDiscussionCommand cmd) {
        discussionRepository.deleteById(cmd.getId());
    }

    // ==========================================
    // Live Session Commands
    // ==========================================

    public LiveSession createLiveSession(TeachingCommands.CreateLiveSessionCommand cmd) {
        LiveSession session = LiveSession.create(
                cmd.getTenantId(),
                cmd.getCourseId(),
                cmd.getTitle(),
                cmd.getDescription(),
                cmd.getInstructorId(),
                cmd.getInstructorName(),
                cmd.getScheduledStartAt(),
                cmd.getScheduledEndAt(),
                cmd.getCreatedBy()
        );
        if (cmd.getLearningPathId() != null) session.setLearningPathId(cmd.getLearningPathId());
        if (cmd.getStepId() != null) session.setStepId(cmd.getStepId());
        if (cmd.getMaxParticipants() != null) session.setMaxParticipants(cmd.getMaxParticipants());
        if (cmd.getRecordingEnabled() != null) session.setRecordingEnabled(cmd.getRecordingEnabled());
        if (cmd.getChatEnabled() != null) session.setChatEnabled(cmd.getChatEnabled());
        return liveSessionRepository.save(session);
    }

    public LiveSession updateLiveSession(TeachingCommands.UpdateLiveSessionCommand cmd) {
        LiveSession session = liveSessionRepository.findById(cmd.getId())
                .orElseThrow(() -> new IllegalArgumentException("直播不存在: " + cmd.getId()));
        // Update logic
        return liveSessionRepository.save(session);
    }

    public LiveSession prepareLiveSession(TeachingCommands.PrepareLiveSessionCommand cmd) {
        LiveSession session = liveSessionRepository.findById(cmd.getId())
                .orElseThrow(() -> new IllegalArgumentException("直播不存在: " + cmd.getId()));
        session.prepare();
        return liveSessionRepository.save(session);
    }

    public LiveSession startLiveSession(TeachingCommands.StartLiveSessionCommand cmd) {
        LiveSession session = liveSessionRepository.findById(cmd.getId())
                .orElseThrow(() -> new IllegalArgumentException("直播不存在: " + cmd.getId()));
        session.start();
        return liveSessionRepository.save(session);
    }

    public LiveSession endLiveSession(TeachingCommands.EndLiveSessionCommand cmd) {
        LiveSession session = liveSessionRepository.findById(cmd.getId())
                .orElseThrow(() -> new IllegalArgumentException("直播不存在: " + cmd.getId()));
        session.end();
        return liveSessionRepository.save(session);
    }

    public LiveSession cancelLiveSession(TeachingCommands.CancelLiveSessionCommand cmd) {
        LiveSession session = liveSessionRepository.findById(cmd.getId())
                .orElseThrow(() -> new IllegalArgumentException("直播不存在: " + cmd.getId()));
        session.cancel();
        return liveSessionRepository.save(session);
    }

    public LiveSession joinLiveSession(TeachingCommands.JoinLiveSessionCommand cmd) {
        LiveSession session = liveSessionRepository.findById(cmd.getId())
                .orElseThrow(() -> new IllegalArgumentException("直播不存在: " + cmd.getId()));
        session.join(cmd.getParticipantId());
        return liveSessionRepository.save(session);
    }

    public LiveSession leaveLiveSession(TeachingCommands.LeaveLiveSessionCommand cmd) {
        LiveSession session = liveSessionRepository.findById(cmd.getId())
                .orElseThrow(() -> new IllegalArgumentException("直播不存在: " + cmd.getId()));
        session.leave(cmd.getParticipantId());
        return liveSessionRepository.save(session);
    }

    public LiveSession setPlaybackUrl(TeachingCommands.SetPlaybackUrlCommand cmd) {
        LiveSession session = liveSessionRepository.findById(cmd.getId())
                .orElseThrow(() -> new IllegalArgumentException("直播不存在: " + cmd.getId()));
        session.setPlaybackUrl(cmd.getPlaybackUrl());
        return liveSessionRepository.save(session);
    }

    public void deleteLiveSession(TeachingCommands.DeleteLiveSessionCommand cmd) {
        liveSessionRepository.deleteById(cmd.getId());
    }

    // ==========================================
    // Queries
    // ==========================================

    @Transactional(readOnly = true)
    public Optional<LearningPath> getLearningPath(LearningPathQueries.GetLearningPathQuery query) {
        return learningPathRepository.findById(query.getId());
    }

    @Transactional(readOnly = true)
    public Optional<LearningPath> getLearningPathById(LearningPathQueries.GetLearningPathByIdQuery query) {
        return learningPathRepository.findById(query.getId());
    }

    @Transactional(readOnly = true)
    public Page<LearningPath> listLearningPaths(LearningPathQueries.ListLearningPathsQuery query) {
        Pageable pageable = PageRequest.of(query.getPage(), query.getSize(),
                Sort.by(Sort.Direction.fromString(query.getSortDirection()), query.getSortBy()));
        if (query.getTenantId() != null && query.getCourseId() != null) {
            return learningPathRepository.findByTenantIdAndStatus(query.getTenantId(),
                    LearningPathStatus.valueOf(query.getStatus()), pageable);
        } else if (query.getTenantId() != null) {
            return learningPathRepository.findByTenantId(query.getTenantId(), pageable);
        } else if (query.getCourseId() != null) {
            return learningPathRepository.findByCourseId(query.getCourseId(), pageable);
        }
        return Page.empty(pageable);
    }

    @Transactional(readOnly = true)
    public List<LearningPathStep> getLearningPathSteps(LearningPathQueries.GetLearningPathStepsQuery query) {
        return stepRepository.findByLearningPathIdOrderBySequenceAsc(query.getLearningPathId().getValue());
    }

    @Transactional(readOnly = true)
    public Optional<LearningPathStep> getStep(LearningPathQueries.GetStepQuery query) {
        return stepRepository.findById(query.getStepId());
    }

    @Transactional(readOnly = true)
    public Optional<LearningPathStep> getStepById(LearningPathQueries.GetStepByIdQuery query) {
        return stepRepository.findById(query.getStepId());
    }

    @Transactional(readOnly = true)
    public Optional<Assignment> getAssignment(TeachingQueries.GetAssignmentQuery query) {
        return assignmentRepository.findById(query.getId());
    }

    @Transactional(readOnly = true)
    public Optional<Assignment> getAssignmentById(TeachingQueries.GetAssignmentByIdQuery query) {
        return assignmentRepository.findById(query.getId());
    }

    @Transactional(readOnly = true)
    public Page<Assignment> listAssignments(TeachingQueries.ListAssignmentsQuery query) {
        Pageable pageable = PageRequest.of(query.getPage(), query.getSize(),
                Sort.by(Sort.Direction.fromString(query.getSortDirection()), query.getSortBy()));
        if (query.getTenantId() != null && query.getStatus() != null) {
            return assignmentRepository.findByTenantIdAndStatus(query.getTenantId(),
                    AssignmentStatus.valueOf(query.getStatus()), pageable);
        } else if (query.getTenantId() != null) {
            return assignmentRepository.findByTenantId(query.getTenantId(), pageable);
        } else if (query.getCourseId() != null) {
            return assignmentRepository.findByCourseId(query.getCourseId(), pageable);
        } else if (query.getLearningPathId() != null) {
            return assignmentRepository.findByLearningPathId(query.getLearningPathId(), pageable);
        } else if (query.getStepId() != null) {
            return assignmentRepository.findByStepId(query.getStepId(), pageable);
        }
        return Page.empty(pageable);
    }

    @Transactional(readOnly = true)
    public List<Assignment> getOpenAssignments(TeachingQueries.GetOpenAssignmentsQuery query) {
        return assignmentRepository.findOpenForSubmission(query.getTenantId(), query.getNow());
    }

    @Transactional(readOnly = true)
    public Optional<Exam> getExam(TeachingQueries.GetExamQuery query) {
        return examRepository.findById(query.getId());
    }

    @Transactional(readOnly = true)
    public Optional<Exam> getExamById(TeachingQueries.GetExamByIdQuery query) {
        return examRepository.findById(query.getId());
    }

    @Transactional(readOnly = true)
    public Page<Exam> listExams(TeachingQueries.ListExamsQuery query) {
        Pageable pageable = PageRequest.of(query.getPage(), query.getSize(),
                Sort.by(Sort.Direction.fromString(query.getSortDirection()), query.getSortBy()));
        if (query.getTenantId() != null && query.getStatus() != null) {
            return examRepository.findByTenantIdAndStatus(query.getTenantId(),
                    ExamStatus.valueOf(query.getStatus()), pageable);
        } else if (query.getTenantId() != null) {
            return examRepository.findByTenantId(query.getTenantId(), pageable);
        } else if (query.getCourseId() != null) {
            return examRepository.findByCourseId(query.getCourseId(), pageable);
        } else if (query.getLearningPathId() != null) {
            return examRepository.findByLearningPathId(query.getLearningPathId(), pageable);
        } else if (query.getStepId() != null) {
            return examRepository.findByStepId(query.getStepId(), pageable);
        }
        return Page.empty(pageable);
    }

    @Transactional(readOnly = true)
    public List<Exam> getAvailableExams(TeachingQueries.GetAvailableExamsQuery query) {
        return examRepository.findAvailableExams(query.getTenantId(), query.getNow());
    }

    @Transactional(readOnly = true)
    public Optional<Discussion> getDiscussion(TeachingQueries.GetDiscussionQuery query) {
        return discussionRepository.findById(query.getId());
    }

    @Transactional(readOnly = true)
    public Optional<Discussion> getDiscussionById(TeachingQueries.GetDiscussionByIdQuery query) {
        return discussionRepository.findById(query.getId());
    }

    @Transactional(readOnly = true)
    public Page<Discussion> listDiscussions(TeachingQueries.ListDiscussionsQuery query) {
        Pageable pageable = PageRequest.of(query.getPage(), query.getSize(),
                Sort.by(Sort.Direction.fromString(query.getSortDirection()), query.getSortBy()));
        if (query.getTenantId() != null && query.getStatus() != null) {
            return discussionRepository.findByTenantIdAndStatus(query.getTenantId(),
                    DiscussionStatus.valueOf(query.getStatus()), pageable);
        } else if (query.getTenantId() != null) {
            return discussionRepository.findByTenantId(query.getTenantId(), pageable);
        } else if (query.getCourseId() != null) {
            return discussionRepository.findByCourseId(query.getCourseId(), pageable);
        } else if (query.getLearningPathId() != null) {
            return discussionRepository.findByLearningPathId(query.getLearningPathId(), pageable);
        } else if (query.getStepId() != null) {
            return discussionRepository.findByStepId(query.getStepId(), pageable);
        } else if (query.getAuthorId() != null) {
            return discussionRepository.findByAuthorId(query.getAuthorId(), pageable);
        }
        return Page.empty(pageable);
    }

    @Transactional(readOnly = true)
    public List<Discussion> getPinnedDiscussions(TeachingQueries.GetPinnedDiscussionsQuery query) {
        return discussionRepository.findPinnedDiscussions(query.getTenantId());
    }

    @Transactional(readOnly = true)
    public Optional<LiveSession> getLiveSession(TeachingQueries.GetLiveSessionQuery query) {
        return liveSessionRepository.findById(query.getId());
    }

    @Transactional(readOnly = true)
    public Optional<LiveSession> getLiveSessionById(TeachingQueries.GetLiveSessionByIdQuery query) {
        return liveSessionRepository.findById(query.getId());
    }

    @Transactional(readOnly = true)
    public Page<LiveSession> listLiveSessions(TeachingQueries.ListLiveSessionsQuery query) {
        Pageable pageable = PageRequest.of(query.getPage(), query.getSize(),
                Sort.by(Sort.Direction.fromString(query.getSortDirection()), query.getSortBy()));
        if (query.getTenantId() != null && query.getStatus() != null) {
            return liveSessionRepository.findByTenantIdAndStatus(query.getTenantId(),
                    LiveSessionStatus.valueOf(query.getStatus()), pageable);
        } else if (query.getTenantId() != null) {
            return liveSessionRepository.findByTenantId(query.getTenantId(), pageable);
        } else if (query.getCourseId() != null) {
            return liveSessionRepository.findByCourseId(query.getCourseId(), pageable);
        } else if (query.getLearningPathId() != null) {
            return liveSessionRepository.findByLearningPathId(query.getLearningPathId(), pageable);
        } else if (query.getInstructorId() != null) {
            return liveSessionRepository.findByInstructorId(query.getInstructorId(), pageable);
        }
        return Page.empty(pageable);
    }

    @Transactional(readOnly = true)
    public List<LiveSession> getUpcomingSessions(TeachingQueries.GetUpcomingSessionsQuery query) {
        return liveSessionRepository.findUpcomingSessions(query.getTenantId(), query.getFrom(), query.getTo());
    }

    @Transactional(readOnly = true)
    public List<LiveSession> getLiveSessions(TeachingQueries.GetLiveSessionsQuery query) {
        return liveSessionRepository.findLiveSessions(query.getTenantId());
    }
}