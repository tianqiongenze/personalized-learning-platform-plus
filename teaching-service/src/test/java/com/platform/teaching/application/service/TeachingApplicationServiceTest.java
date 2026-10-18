package com.platform.teaching.application.service;

import com.platform.teaching.application.command.LearningPathCommands;
import com.platform.teaching.application.command.TeachingCommands;
import com.platform.teaching.application.query.LearningPathQueries;
import com.platform.teaching.domain.model.Assignment;
import com.platform.teaching.domain.model.Exam;
import com.platform.teaching.domain.model.LearningPath;
import com.platform.teaching.domain.model.LearningPathStatus;
import com.platform.teaching.domain.model.LearningPathStep;
import com.platform.teaching.domain.model.StepStatus;
import com.platform.teaching.domain.model.StepType;
import com.platform.teaching.domain.model.valueobjects.AssignmentId;
import com.platform.teaching.domain.model.valueobjects.AssignmentStatus;
import com.platform.teaching.domain.model.valueobjects.ExamId;
import com.platform.teaching.domain.model.valueobjects.ExamStatus;
import com.platform.teaching.domain.model.valueobjects.LearningPathId;
import com.platform.teaching.domain.model.valueobjects.StepId;
import com.platform.teaching.domain.repository.AssignmentRepository;
import com.platform.teaching.domain.repository.DiscussionRepository;
import com.platform.teaching.domain.repository.ExamRepository;
import com.platform.teaching.domain.repository.LearningPathRepository;
import com.platform.teaching.domain.repository.LearningPathStepRepository;
import com.platform.teaching.domain.repository.LiveSessionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TeachingApplicationService 测试")
class TeachingApplicationServiceTest {

    @Mock
    private LearningPathRepository learningPathRepository;

    @Mock
    private LearningPathStepRepository stepRepository;

    @Mock
    private AssignmentRepository assignmentRepository;

    @Mock
    private ExamRepository examRepository;

    @Mock
    private DiscussionRepository discussionRepository;

    @Mock
    private LiveSessionRepository liveSessionRepository;

    @InjectMocks
    private TeachingApplicationService service;

    private LearningPath newPath() {
        return LearningPath.create(UUID.randomUUID(), UUID.randomUUID(), "Java 入门", "描述", UUID.randomUUID());
    }

    private LearningPathStep newStep() {
        return new LearningPathStep(UUID.randomUUID(), null, 1, StepType.VIDEO, "第一课", null,
                null, "video/mp4", 10, true);
    }

    @Nested
    @DisplayName("学习路径命令")
    class LearningPathCommandsTests {

        @Test
        @DisplayName("应创建学习路径")
        void shouldCreateLearningPath() {
            LearningPathCommands.CreateLearningPathCommand cmd = LearningPathCommands.CreateLearningPathCommand.builder()
                    .tenantId(UUID.randomUUID()).courseId(UUID.randomUUID())
                    .title("Java 入门").description("描述").createdBy(UUID.randomUUID()).build();
            when(learningPathRepository.save(any(LearningPath.class))).thenAnswer(inv -> inv.getArgument(0));

            LearningPath result = service.createLearningPath(cmd);

            assertThat(result.getTitle()).isEqualTo("Java 入门");
            assertThat(result.getStatus()).isEqualTo(LearningPathStatus.DRAFT);
        }

        @Test
        @DisplayName("应发布学习路径")
        void shouldPublishLearningPath() {
            LearningPath path = newPath();
            path.addStep(newStep());
            LearningPathId id = LearningPathId.of(path.getId());
            when(learningPathRepository.findById(id)).thenReturn(Optional.of(path));
            when(learningPathRepository.save(any(LearningPath.class))).thenAnswer(inv -> inv.getArgument(0));

            LearningPath result = service.publishLearningPath(
                    LearningPathCommands.PublishLearningPathCommand.builder().id(id).build());

            assertThat(result.getStatus()).isEqualTo(LearningPathStatus.PUBLISHED);
        }

        @Test
        @DisplayName("学习路径不存在时发布应抛出异常")
        void shouldThrowWhenPublishMissingPath() {
            LearningPathId id = LearningPathId.generate();
            when(learningPathRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.publishLearningPath(
                    LearningPathCommands.PublishLearningPathCommand.builder().id(id).build()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("学习路径不存在");
        }

        @Test
        @DisplayName("应归档学习路径")
        void shouldArchiveLearningPath() {
            LearningPath path = newPath();
            path.addStep(newStep());
            path.publish();
            LearningPathId id = LearningPathId.of(path.getId());
            when(learningPathRepository.findById(id)).thenReturn(Optional.of(path));
            when(learningPathRepository.save(any(LearningPath.class))).thenAnswer(inv -> inv.getArgument(0));

            LearningPath result = service.archiveLearningPath(
                    LearningPathCommands.ArchiveLearningPathCommand.builder().id(id).build());

            assertThat(result.getStatus()).isEqualTo(LearningPathStatus.ARCHIVED);
        }

        @Test
        @DisplayName("应完成学习路径")
        void shouldCompleteLearningPath() {
            LearningPath path = newPath();
            path.addStep(newStep());
            path.publish();
            LearningPathId id = LearningPathId.of(path.getId());
            when(learningPathRepository.findById(id)).thenReturn(Optional.of(path));
            when(learningPathRepository.save(any(LearningPath.class))).thenAnswer(inv -> inv.getArgument(0));

            LearningPath result = service.completeLearningPath(
                    LearningPathCommands.CompleteLearningPathCommand.builder().id(id).build());

            assertThat(result.getStatus()).isEqualTo(LearningPathStatus.COMPLETED);
        }

        @Test
        @DisplayName("应添加步骤并自动排序")
        void shouldAddStep() {
            LearningPath path = newPath();
            LearningPathId id = LearningPathId.of(path.getId());
            when(learningPathRepository.findById(id)).thenReturn(Optional.of(path));
            when(learningPathRepository.save(any(LearningPath.class))).thenAnswer(inv -> inv.getArgument(0));
            LearningPathCommands.AddStepCommand cmd = LearningPathCommands.AddStepCommand.builder()
                    .learningPathId(id).stepType(LearningPathCommands.StepType.VIDEO).title("第一课")
                    .description("描述").contentId(UUID.randomUUID()).contentType("video/mp4")
                    .estimatedMinutes(10).isRequired(true).build();

            LearningPathStep step = service.addStep(cmd);

            assertThat(step.getSequence()).isEqualTo(1);
            assertThat(step.getStepType()).isEqualTo(StepType.VIDEO);
            assertThat(path.getSteps()).hasSize(1);
        }

        @Test
        @DisplayName("学习路径不存在时添加步骤应抛出异常")
        void shouldThrowWhenAddStepMissingPath() {
            LearningPathId id = LearningPathId.generate();
            when(learningPathRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.addStep(LearningPathCommands.AddStepCommand.builder()
                    .learningPathId(id).stepType(LearningPathCommands.StepType.VIDEO).title("t").build()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("学习路径不存在");
        }

        @Test
        @DisplayName("应开始步骤")
        void shouldStartStep() {
            LearningPathStep step = newStep();
            StepId stepId = StepId.of(step.getId());
            when(stepRepository.findById(stepId)).thenReturn(Optional.of(step));
            when(stepRepository.save(any(LearningPathStep.class))).thenAnswer(inv -> inv.getArgument(0));

            service.startStep(LearningPathCommands.StartStepCommand.builder()
                    .stepId(stepId).userId(UUID.randomUUID()).build());

            assertThat(step.getStatus()).isEqualTo(StepStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("应完成步骤")
        void shouldCompleteStep() {
            LearningPathStep step = newStep();
            step.start();
            StepId stepId = StepId.of(step.getId());
            when(stepRepository.findById(stepId)).thenReturn(Optional.of(step));
            when(stepRepository.save(any(LearningPathStep.class))).thenAnswer(inv -> inv.getArgument(0));

            service.completeStep(LearningPathCommands.CompleteStepCommand.builder()
                    .stepId(stepId).userId(UUID.randomUUID()).build());

            assertThat(step.getStatus()).isEqualTo(StepStatus.COMPLETED);
        }

        @Test
        @DisplayName("应跳过步骤")
        void shouldSkipStep() {
            LearningPathStep step = newStep();
            StepId stepId = StepId.of(step.getId());
            when(stepRepository.findById(stepId)).thenReturn(Optional.of(step));
            when(stepRepository.save(any(LearningPathStep.class))).thenAnswer(inv -> inv.getArgument(0));

            service.skipStep(LearningPathCommands.SkipStepCommand.builder()
                    .stepId(stepId).userId(UUID.randomUUID()).build());

            assertThat(step.getStatus()).isEqualTo(StepStatus.SKIPPED);
        }
    }

    @Nested
    @DisplayName("作业命令")
    class AssignmentCommands {

        @Test
        @DisplayName("应创建作业并应用配置")
        void shouldCreateAssignment() {
            TeachingCommands.CreateAssignmentCommand cmd = TeachingCommands.CreateAssignmentCommand.builder()
                    .tenantId(UUID.randomUUID()).courseId(UUID.randomUUID()).title("第一次作业")
                    .description("描述").instructions("说明").maxScore(50).passingScore(30)
                    .allowLateSubmission(true).latePenaltyPercent(5).maxAttempts(2)
                    .startAt(LocalDateTime.now()).dueAt(LocalDateTime.now().plusDays(3))
                    .createdBy(UUID.randomUUID()).build();
            when(assignmentRepository.save(any(Assignment.class))).thenAnswer(inv -> inv.getArgument(0));

            Assignment result = service.createAssignment(cmd);

            assertThat(result.getTitle()).isEqualTo("第一次作业");
            assertThat(result.getMaxScore()).isEqualTo(50);
            assertThat(result.getPassingScore()).isEqualTo(30);
            assertThat(result.getAllowLateSubmission()).isTrue();
            assertThat(result.getStatus()).isEqualTo(AssignmentStatus.DRAFT);
        }

        @Test
        @DisplayName("应发布作业")
        void shouldPublishAssignment() {
            Assignment assignment = Assignment.create(UUID.randomUUID(), UUID.randomUUID(), "作业",
                    null, null, UUID.randomUUID());
            AssignmentId id = AssignmentId.of(assignment.getId());
            when(assignmentRepository.findById(id)).thenReturn(Optional.of(assignment));
            when(assignmentRepository.save(any(Assignment.class))).thenAnswer(inv -> inv.getArgument(0));

            Assignment result = service.publishAssignment(
                    TeachingCommands.PublishAssignmentCommand.builder().id(id).build());

            assertThat(result.getStatus()).isEqualTo(AssignmentStatus.PUBLISHED);
        }

        @Test
        @DisplayName("应归档作业")
        void shouldArchiveAssignment() {
            Assignment assignment = Assignment.create(UUID.randomUUID(), UUID.randomUUID(), "作业",
                    null, null, UUID.randomUUID());
            assignment.publish();
            AssignmentId id = AssignmentId.of(assignment.getId());
            when(assignmentRepository.findById(id)).thenReturn(Optional.of(assignment));
            when(assignmentRepository.save(any(Assignment.class))).thenAnswer(inv -> inv.getArgument(0));

            Assignment result = service.archiveAssignment(
                    TeachingCommands.ArchiveAssignmentCommand.builder().id(id).build());

            assertThat(result.getStatus()).isEqualTo(AssignmentStatus.ARCHIVED);
        }

        @Test
        @DisplayName("作业不存在时发布应抛出异常")
        void shouldThrowWhenPublishMissingAssignment() {
            AssignmentId id = AssignmentId.generate();
            when(assignmentRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.publishAssignment(
                    TeachingCommands.PublishAssignmentCommand.builder().id(id).build()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("作业不存在");
        }
    }

    @Nested
    @DisplayName("考试命令")
    class ExamCommands {

        @Test
        @DisplayName("应创建考试并应用配置")
        void shouldCreateExam() {
            TeachingCommands.CreateExamCommand cmd = TeachingCommands.CreateExamCommand.builder()
                    .tenantId(UUID.randomUUID()).courseId(UUID.randomUUID()).title("期中考试")
                    .description("描述").instructions("说明").durationMinutes(90).maxScore(100)
                    .passingScore(60).maxAttempts(1).shuffleQuestions(true).shuffleOptions(false)
                    .showResultsImmediately(true).createdBy(UUID.randomUUID()).build();
            when(examRepository.save(any(Exam.class))).thenAnswer(inv -> inv.getArgument(0));

            Exam result = service.createExam(cmd);

            assertThat(result.getTitle()).isEqualTo("期中考试");
            assertThat(result.getDurationMinutes()).isEqualTo(90);
            assertThat(result.getShuffleOptions()).isFalse();
            assertThat(result.getStatus()).isEqualTo(ExamStatus.DRAFT);
        }

        @Test
        @DisplayName("应发布考试")
        void shouldPublishExam() {
            Exam exam = Exam.create(UUID.randomUUID(), UUID.randomUUID(), "考试", null, null, UUID.randomUUID());
            ExamId id = ExamId.of(exam.getId());
            when(examRepository.findById(id)).thenReturn(Optional.of(exam));
            when(examRepository.save(any(Exam.class))).thenAnswer(inv -> inv.getArgument(0));

            Exam result = service.publishExam(TeachingCommands.PublishExamCommand.builder().id(id).build());

            assertThat(result.getStatus()).isEqualTo(ExamStatus.PUBLISHED);
        }

        @Test
        @DisplayName("应开始与结束考试")
        void shouldStartAndEndExam() {
            Exam exam = Exam.create(UUID.randomUUID(), UUID.randomUUID(), "考试", null, null, UUID.randomUUID());
            exam.publish();
            ExamId id = ExamId.of(exam.getId());
            when(examRepository.findById(id)).thenReturn(Optional.of(exam));
            when(examRepository.save(any(Exam.class))).thenAnswer(inv -> inv.getArgument(0));

            service.startExam(TeachingCommands.StartExamCommand.builder().id(id).build());
            assertThat(exam.getStatus()).isEqualTo(ExamStatus.ONGOING);

            service.endExam(TeachingCommands.EndExamCommand.builder().id(id).build());
            assertThat(exam.getStatus()).isEqualTo(ExamStatus.ENDED);
        }
    }

    @Nested
    @DisplayName("查询委托")
    class Queries {

        @Test
        @DisplayName("getLearningPath 应委托仓储")
        void shouldGetLearningPath() {
            LearningPath path = newPath();
            LearningPathId id = LearningPathId.of(path.getId());
            when(learningPathRepository.findById(id)).thenReturn(Optional.of(path));

            Optional<LearningPath> result = service.getLearningPath(
                    LearningPathQueries.GetLearningPathQuery.builder().id(id).build());

            assertThat(result).contains(path);
        }

        @Test
        @DisplayName("getLearningPathSteps 应按顺序委托仓储")
        void shouldGetLearningPathSteps() {
            LearningPath path = newPath();
            LearningPathId id = LearningPathId.of(path.getId());
            LearningPathStep step = newStep();
            when(stepRepository.findByLearningPathIdOrderBySequenceAsc(path.getId())).thenReturn(List.of(step));

            List<LearningPathStep> result = service.getLearningPathSteps(
                    LearningPathQueries.GetLearningPathStepsQuery.builder().learningPathId(id).build());

            assertThat(result).containsExactly(step);
        }
    }
}
