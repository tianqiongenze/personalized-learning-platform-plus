package com.platform.course.application.service;

import com.platform.course.application.command.AddChapterKnowledgePointCommand;
import com.platform.course.application.command.AddKnowledgePointPrerequisiteCommand;
import com.platform.course.application.command.ArchiveCourseCommand;
import com.platform.course.application.command.CreateChapterCommand;
import com.platform.course.application.command.CreateCourseCommand;
import com.platform.course.application.command.CreateKnowledgePointCommand;
import com.platform.course.application.command.DeleteChapterCommand;
import com.platform.course.application.command.DeleteCourseCommand;
import com.platform.course.application.command.PublishChapterCommand;
import com.platform.course.application.command.PublishCourseCommand;
import com.platform.course.application.command.PublishResourceCommand;
import com.platform.course.application.command.RestoreCourseCommand;
import com.platform.course.application.command.UpdateChapterOrderCommand;
import com.platform.course.application.command.UpdateCourseCodeCommand;
import com.platform.course.application.command.UpdateCourseInfoCommand;
import com.platform.course.application.command.UpdateCourseSettingsCommand;
import com.platform.course.application.command.UpdateKnowledgePointInfoCommand;
import com.platform.course.application.command.UpdateResourceInfoCommand;
import com.platform.course.application.query.ChapterQueryService;
import com.platform.course.application.query.CourseDetailDto;
import com.platform.course.application.query.CourseListDto;
import com.platform.course.application.query.CoursePageQuery;
import com.platform.course.application.query.CourseQueryService;
import com.platform.course.application.query.CourseSimpleDto;
import com.platform.course.application.query.CourseStatisticsDto;
import com.platform.course.application.query.KnowledgePointQueryService;
import com.platform.course.application.query.PageResult;
import com.platform.course.application.query.ResourceQueryService;
import com.platform.course.domain.model.Chapter;
import com.platform.course.domain.model.Course;
import com.platform.course.domain.model.KnowledgePoint;
import com.platform.course.domain.model.Resource;
import com.platform.course.domain.model.valueobjects.ChapterStatus;
import com.platform.course.domain.model.valueobjects.CourseStatus;
import com.platform.course.domain.model.valueobjects.ResourceStatus;
import com.platform.course.domain.model.valueobjects.ResourceType;
import com.platform.course.domain.repository.ChapterRepository;
import com.platform.course.domain.repository.CourseRepository;
import com.platform.course.domain.repository.KnowledgePointRepository;
import com.platform.course.domain.repository.ResourceRepository;
import com.platform.course.infrastructure.event.DomainEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CourseApplicationService 测试")
class CourseApplicationServiceTest {

    private static final String TENANT = "tenant-1";

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private ChapterRepository chapterRepository;

    @Mock
    private KnowledgePointRepository knowledgePointRepository;

    @Mock
    private ResourceRepository resourceRepository;

    @Mock
    private CourseQueryService courseQueryService;

    @Mock
    private ChapterQueryService chapterQueryService;

    @Mock
    private KnowledgePointQueryService knowledgePointQueryService;

    @Mock
    private ResourceQueryService resourceQueryService;

    @Mock
    private DomainEventPublisher domainEventPublisher;

    @InjectMocks
    private CourseApplicationService service;

    private Course draftCourse() {
        Course course = Course.create(TENANT, "math_101", "数学基础", "描述", "数学", "一年级", "2026春",
                null, null, "creator-1");
        course.clearDomainEvents();
        return course;
    }

    private Chapter draftChapter(Course course) {
        Chapter chapter = Chapter.create(course.getId(), TENANT, "ch_01", "第一章", null, null, 1, 1, 1, "creator-1");
        chapter.clearDomainEvents();
        return chapter;
    }

    private KnowledgePoint knowledgePoint(Course course) {
        KnowledgePoint kp = KnowledgePoint.create(course.getId(), TENANT, "kp_01", "知识点", null, null, 1, "creator-1");
        kp.clearDomainEvents();
        return kp;
    }

    private Resource draftResource(Course course) {
        Resource resource = Resource.create(course.getId(), TENANT, null, ResourceType.VIDEO, "视频",
                null, "https://cdn/v.mp4", null, null, 1, null, "creator-1");
        resource.clearDomainEvents();
        return resource;
    }

    @Nested
    @DisplayName("课程命令")
    class CourseCommands {

        @Test
        @DisplayName("编码重复应抛出异常")
        void shouldThrowWhenCourseCodeExists() {
            CreateCourseCommand cmd = new CreateCourseCommand("math_101", "数学", "描述", "数学",
                    "一年级", "2026春", null, null, "creator-1");
            com.platform.common.tenant.TenantContext.set(TENANT);
            when(courseRepository.existsByCode(TENANT, "math_101")).thenReturn(true);

            assertThatThrownBy(() -> service.createCourse(cmd))
                    .isInstanceOf(CourseCodeAlreadyExistsException.class)
                    .hasMessageContaining("math_101");
            verify(courseRepository, never()).save(any());
            com.platform.common.tenant.TenantContext.clear();
        }

        @Test
        @DisplayName("未设租户时缺省 'public' 不再 NPE(修复: 原传 null 必抛)")
        void shouldFailFastWhenTenantMissing() {
            CreateCourseCommand cmd = new CreateCourseCommand("new_101", "数学", "描述", "数学",
                    "一年级", "2026春", null, null, "creator-1");
            com.platform.common.tenant.TenantContext.clear();
            when(courseRepository.existsByCode(com.platform.common.tenant.TenantContext.DEFAULT_TENANT, "new_101")).thenReturn(false);
            when(courseRepository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));

            var id = service.createCourse(cmd);
            org.assertj.core.api.Assertions.assertThat(id).isNotNull();
        }

        @Test
        @DisplayName("应更新课程基本信息")
        void shouldUpdateCourseInfo() {
            Course course = draftCourse();
            when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
            when(courseRepository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));

            service.updateCourseInfo(new UpdateCourseInfoCommand(course.getId(), "新名称", "新描述",
                    "语文", "二年级", "2026秋", null, "admin"));

            assertThat(course.getName()).isEqualTo("新名称");
            verify(domainEventPublisher).publishAll(anyList());
        }

        @Test
        @DisplayName("课程不存在应抛出异常")
        void shouldThrowWhenCourseNotFound() {
            Course course = draftCourse();
            when(courseRepository.findById(course.getId())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateCourseInfo(new UpdateCourseInfoCommand(
                    course.getId(), "新名称", null, "数学", "一年级", "2026春", null, "admin")))
                    .isInstanceOf(CourseNotFoundException.class);
        }

        @Test
        @DisplayName("草稿态应可更新编码")
        void shouldUpdateCourseCode() {
            Course course = draftCourse();
            when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
            when(courseRepository.existsByCodeAndIdNot("math_202", course.getId())).thenReturn(false);
            when(courseRepository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));

            service.updateCourseCode(new UpdateCourseCodeCommand(course.getId(), "math_202", "admin"));

            assertThat(course.getCode()).isEqualTo("MATH_202");
        }

        @Test
        @DisplayName("更新编码重复应抛出异常")
        void shouldThrowWhenUpdateCodeDuplicate() {
            Course course = draftCourse();
            when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
            when(courseRepository.existsByCodeAndIdNot("math_202", course.getId())).thenReturn(true);

            assertThatThrownBy(() -> service.updateCourseCode(
                    new UpdateCourseCodeCommand(course.getId(), "math_202", "admin")))
                    .isInstanceOf(CourseCodeAlreadyExistsException.class);
        }

        @Test
        @DisplayName("应发布课程")
        void shouldPublishCourse() {
            Course course = draftCourse();
            when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
            when(courseRepository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));

            service.publishCourse(new PublishCourseCommand(course.getId(), "admin"));

            assertThat(course.getStatus()).isEqualTo(CourseStatus.PUBLISHED);
        }

        @Test
        @DisplayName("应归档课程")
        void shouldArchiveCourse() {
            Course course = draftCourse();
            course.publish("admin");
            course.clearDomainEvents();
            when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
            when(courseRepository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));

            service.archiveCourse(new ArchiveCourseCommand(course.getId(), "admin"));

            assertThat(course.getStatus()).isEqualTo(CourseStatus.ARCHIVED);
        }

        @Test
        @DisplayName("应恢复发布课程")
        void shouldRestoreCourse() {
            Course course = draftCourse();
            course.publish("admin");
            course.archive("admin");
            course.clearDomainEvents();
            when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
            when(courseRepository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));

            service.restoreCourse(new RestoreCourseCommand(course.getId(), "admin"));

            assertThat(course.getStatus()).isEqualTo(CourseStatus.PUBLISHED);
        }

        @Test
        @DisplayName("应删除课程")
        void shouldDeleteCourse() {
            Course course = draftCourse();
            when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
            when(courseRepository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));

            service.deleteCourse(new DeleteCourseCommand(course.getId(), "admin", "不再使用"));

            assertThat(course.getStatus()).isEqualTo(CourseStatus.DELETED);
        }

        @Test
        @DisplayName("应更新课程设置")
        void shouldUpdateCourseSettings() {
            Course course = draftCourse();
            when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
            when(courseRepository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));

            service.updateCourseSettings(new UpdateCourseSettingsCommand(course.getId(),
                    Map.of("autoEnroll", true), "admin"));

            assertThat(course.getSettings()).containsEntry("autoEnroll", true);
        }
    }

    @Nested
    @DisplayName("章节命令")
    class ChapterCommands {

        @Test
        @DisplayName("章节编码重复应抛出异常")
        void shouldThrowWhenChapterCodeExists() {
            Course course = draftCourse();
            CreateChapterCommand cmd = new CreateChapterCommand(course.getId(), "ch_01", "第一章", null,
                    null, 1, 1, 1, "creator-1");
            when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
            when(chapterRepository.existsByCodeAndCourseId("ch_01", course.getId())).thenReturn(true);

            assertThatThrownBy(() -> service.createChapter(cmd))
                    .isInstanceOf(ChapterCodeAlreadyExistsException.class);
        }

        @Test
        @DisplayName("应更新章节排序")
        void shouldUpdateChapterOrder() {
            Course course = draftCourse();
            Chapter chapter = draftChapter(course);
            when(chapterRepository.findById(chapter.getId())).thenReturn(Optional.of(chapter));
            when(chapterRepository.save(any(Chapter.class))).thenAnswer(inv -> inv.getArgument(0));

            service.updateChapterOrder(new UpdateChapterOrderCommand(chapter.getId(), 9, "admin"));

            assertThat(chapter.getOrder()).isEqualTo(9);
        }

        @Test
        @DisplayName("应发布章节")
        void shouldPublishChapter() {
            Course course = draftCourse();
            Chapter chapter = draftChapter(course);
            when(chapterRepository.findById(chapter.getId())).thenReturn(Optional.of(chapter));
            when(chapterRepository.save(any(Chapter.class))).thenAnswer(inv -> inv.getArgument(0));

            service.publishChapter(new PublishChapterCommand(chapter.getId(), "admin"));

            assertThat(chapter.getStatus()).isEqualTo(ChapterStatus.PUBLISHED);
        }

        @Test
        @DisplayName("应删除章节")
        void shouldDeleteChapter() {
            Course course = draftCourse();
            Chapter chapter = draftChapter(course);
            when(chapterRepository.findById(chapter.getId())).thenReturn(Optional.of(chapter));
            when(chapterRepository.save(any(Chapter.class))).thenAnswer(inv -> inv.getArgument(0));

            service.deleteChapter(new DeleteChapterCommand(chapter.getId(), "admin", "不再使用"));

            assertThat(chapter.getStatus()).isEqualTo(ChapterStatus.DELETED);
        }

        @Test
        @DisplayName("应向章节添加知识点")
        void shouldAddChapterKnowledgePoint() {
            Course course = draftCourse();
            Chapter chapter = draftChapter(course);
            KnowledgePoint kp = knowledgePoint(course);
            when(chapterRepository.findById(chapter.getId())).thenReturn(Optional.of(chapter));
            when(chapterRepository.save(any(Chapter.class))).thenAnswer(inv -> inv.getArgument(0));

            service.addChapterKnowledgePoint(new AddChapterKnowledgePointCommand(
                    chapter.getId(), kp.getId(), "admin"));

            assertThat(chapter.getKnowledgePointIds()).containsExactly(kp.getId());
        }
    }

    @Nested
    @DisplayName("知识点命令")
    class KnowledgePointCommands {

        @Test
        @DisplayName("知识点编码重复应抛出异常")
        void shouldThrowWhenKnowledgePointCodeExists() {
            Course course = draftCourse();
            CreateKnowledgePointCommand cmd = new CreateKnowledgePointCommand(course.getId(), "kp_01",
                    "知识点", null, null, 1, "creator-1");
            when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
            when(knowledgePointRepository.existsByCodeAndCourseId("kp_01", course.getId())).thenReturn(true);

            assertThatThrownBy(() -> service.createKnowledgePoint(cmd))
                    .isInstanceOf(KnowledgePointCodeAlreadyExistsException.class);
        }

        @Test
        @DisplayName("应添加前置知识点")
        void shouldAddPrerequisite() {
            Course course = draftCourse();
            KnowledgePoint kp = knowledgePoint(course);
            KnowledgePoint pre = knowledgePoint(course);
            when(knowledgePointRepository.findById(kp.getId())).thenReturn(Optional.of(kp));
            when(knowledgePointRepository.save(any(KnowledgePoint.class))).thenAnswer(inv -> inv.getArgument(0));

            service.addKnowledgePointPrerequisite(new AddKnowledgePointPrerequisiteCommand(
                    kp.getId(), pre.getId(), "admin"));

            assertThat(kp.getPrerequisites()).containsExactly(pre.getId());
        }

        @Test
        @DisplayName("依赖自己应抛出异常")
        void shouldThrowWhenSelfPrerequisite() {
            Course course = draftCourse();
            KnowledgePoint kp = knowledgePoint(course);
            when(knowledgePointRepository.findById(kp.getId())).thenReturn(Optional.of(kp));

            assertThatThrownBy(() -> service.addKnowledgePointPrerequisite(
                    new AddKnowledgePointPrerequisiteCommand(kp.getId(), kp.getId(), "admin")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("知识点不能依赖自己");
        }

        @Test
        @DisplayName("应更新知识点信息")
        void shouldUpdateKnowledgePointInfo() {
            Course course = draftCourse();
            KnowledgePoint kp = knowledgePoint(course);
            when(knowledgePointRepository.findById(kp.getId())).thenReturn(Optional.of(kp));
            when(knowledgePointRepository.save(any(KnowledgePoint.class))).thenAnswer(inv -> inv.getArgument(0));

            service.updateKnowledgePointInfo(new UpdateKnowledgePointInfoCommand(kp.getId(), "新名称",
                    "新描述", 5, "admin"));

            assertThat(kp.getName()).isEqualTo("新名称");
        }
    }

    @Nested
    @DisplayName("资源命令")
    class ResourceCommands {

        @Test
        @DisplayName("应更新资源信息")
        void shouldUpdateResourceInfo() {
            Course course = draftCourse();
            Resource resource = draftResource(course);
            when(resourceRepository.findById(resource.getId())).thenReturn(Optional.of(resource));
            when(resourceRepository.save(any(Resource.class))).thenAnswer(inv -> inv.getArgument(0));

            service.updateResourceInfo(new UpdateResourceInfoCommand(resource.getId(), "新标题",
                    "新描述", "url2", null, 3, Set.of("tag"), "admin"));

            assertThat(resource.getTitle()).isEqualTo("新标题");
            assertThat(resource.getVersion()).isEqualTo(2);
        }

        @Test
        @DisplayName("应发布资源")
        void shouldPublishResource() {
            Course course = draftCourse();
            Resource resource = draftResource(course);
            when(resourceRepository.findById(resource.getId())).thenReturn(Optional.of(resource));
            when(resourceRepository.save(any(Resource.class))).thenAnswer(inv -> inv.getArgument(0));

            service.publishResource(new PublishResourceCommand(resource.getId(), "admin"));

            assertThat(resource.getStatus()).isEqualTo(ResourceStatus.PUBLISHED);
        }
    }

    @Nested
    @DisplayName("查询委托")
    class Queries {

        @Test
        @DisplayName("findCourseById 应委托查询服务")
        void shouldDelegateFindCourseById() {
            Course course = draftCourse();
            Instant now = Instant.now();
            CourseDetailDto expected = new CourseDetailDto(course.getId().toString(), TENANT, "MATH_101",
                    "数学", "描述", "数学", "一年级", "2026春", "DRAFT", null, Map.of(), "creator-1",
                    now, now, null, null, null, null, null);
            when(courseQueryService.findById(course.getId())).thenReturn(expected);

            assertThat(service.findCourseById(course.getId())).isSameAs(expected);
        }

        @Test
        @DisplayName("findCoursePage 应委托查询服务")
        void shouldDelegateFindCoursePage() {
            CoursePageQuery query = new CoursePageQuery(TENANT, 0, 20, null, null, null, null,
                    "createdAt", "desc");
            PageResult<CourseListDto> expected = new PageResult<>(List.of(), 0, 20, 0, 0, true, true);
            when(courseQueryService.findPage(query)).thenReturn(expected);

            assertThat(service.findCoursePage(query)).isSameAs(expected);
        }

        @Test
        @DisplayName("findCoursesByCreatorId 应委托查询服务")
        void shouldDelegateFindByCreatorId() {
            CourseSimpleDto dto = new CourseSimpleDto("id-1", "MATH_101", "数学");
            when(courseQueryService.findByCreatorId("creator-1")).thenReturn(List.of(dto));

            assertThat(service.findCoursesByCreatorId("creator-1")).containsExactly(dto);
        }

        @Test
        @DisplayName("courseStatistics 应委托查询服务")
        void shouldDelegateStatistics() {
            CourseStatisticsDto expected = new CourseStatisticsDto(5, 1, 3, 1, 0);
            when(courseQueryService.statistics(TENANT)).thenReturn(expected);

            assertThat(service.courseStatistics(TENANT)).isSameAs(expected);
        }
    }
}
