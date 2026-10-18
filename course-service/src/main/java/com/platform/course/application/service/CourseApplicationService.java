package com.platform.course.application.service;

import com.platform.course.application.command.*;
import com.platform.course.application.query.*;
import com.platform.course.domain.event.DomainEvent;
import com.platform.course.domain.model.*;
import com.platform.course.domain.model.valueobjects.*;
import com.platform.course.domain.repository.*;
import com.platform.course.infrastructure.event.DomainEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 课程应用服务
 */
@Service
@Transactional

public class CourseApplicationService {

    private final CourseRepository courseRepository;
    private final ChapterRepository chapterRepository;
    private final KnowledgePointRepository knowledgePointRepository;
    private final ResourceRepository resourceRepository;
    private final CourseQueryService courseQueryService;
    private final ChapterQueryService chapterQueryService;
    private final KnowledgePointQueryService knowledgePointQueryService;
    private final ResourceQueryService resourceQueryService;
    private final DomainEventPublisher domainEventPublisher;

    public CourseApplicationService(CourseRepository courseRepository,
                                    ChapterRepository chapterRepository,
                                    KnowledgePointRepository knowledgePointRepository,
                                    ResourceRepository resourceRepository,
                                    CourseQueryService courseQueryService,
                                    ChapterQueryService chapterQueryService,
                                    KnowledgePointQueryService knowledgePointQueryService,
                                    ResourceQueryService resourceQueryService,
                                    DomainEventPublisher domainEventPublisher) {
        this.courseRepository = courseRepository;
        this.chapterRepository = chapterRepository;
        this.knowledgePointRepository = knowledgePointRepository;
        this.resourceRepository = resourceRepository;
        this.courseQueryService = courseQueryService;
        this.chapterQueryService = chapterQueryService;
        this.knowledgePointQueryService = knowledgePointQueryService;
        this.resourceQueryService = resourceQueryService;
        this.domainEventPublisher = domainEventPublisher;
    }

    // ========== 课程命令处理 ==========

    public CourseId createCourse(CreateCourseCommand cmd) {
        String tenantId = com.platform.common.tenant.TenantContext.get();
        if (courseRepository.existsByCode(tenantId, cmd.code())) {
            throw new CourseCodeAlreadyExistsException(cmd.code());
        }

        // 租户 ID 由 TenantContextFilter 从 X-Tenant-Id 装载, 缺省 "public"。
        // 此前 createCourse 传 null 会命中 Course 构造器的 requireNonNull("租户ID不能为空") 必抛 NPE。
        Course course = Course.create(tenantId,
            cmd.code(), cmd.name(), cmd.description(),
            cmd.subject(), cmd.gradeLevel(), cmd.semester(), cmd.coverUrl(), cmd.settings(), cmd.creatorId());

        Course saved = courseRepository.save(course);
        publishDomainEvents(saved);
        return saved.getId();
    }

    public void updateCourseInfo(UpdateCourseInfoCommand cmd) {
        Course course = getCourseOrThrow(cmd.courseId());
        course.updateInfo(cmd.name(), cmd.description(), cmd.subject(), cmd.gradeLevel(), cmd.semester(), cmd.coverUrl(), cmd.updatedBy());
        Course saved = courseRepository.save(course);
        publishDomainEvents(saved);
    }

    public void updateCourseCode(UpdateCourseCodeCommand cmd) {
        Course course = getCourseOrThrow(cmd.courseId());
        
        if (courseRepository.existsByCodeAndIdNot(cmd.code(), cmd.courseId())) {
            throw new CourseCodeAlreadyExistsException(cmd.code());
        }
        
        course.updateCode(cmd.code(), cmd.updatedBy());
        Course saved = courseRepository.save(course);
        publishDomainEvents(saved);
    }

    public void updateCourseSettings(UpdateCourseSettingsCommand cmd) {
        Course course = getCourseOrThrow(cmd.courseId());
        course.updateSettings(cmd.settings(), cmd.updatedBy());
        Course saved = courseRepository.save(course);
        publishDomainEvents(saved);
    }

    public void publishCourse(PublishCourseCommand cmd) {
        Course course = getCourseOrThrow(cmd.courseId());
        course.publish(cmd.publishedBy());
        Course saved = courseRepository.save(course);
        publishDomainEvents(saved);
    }

    public void archiveCourse(ArchiveCourseCommand cmd) {
        Course course = getCourseOrThrow(cmd.courseId());
        course.archive(cmd.archivedBy());
        Course saved = courseRepository.save(course);
        publishDomainEvents(saved);
    }

    public void restoreCourse(RestoreCourseCommand cmd) {
        Course course = getCourseOrThrow(cmd.courseId());
        course.restorePublish(cmd.restoredBy());
        Course saved = courseRepository.save(course);
        publishDomainEvents(saved);
    }

    public void deleteCourse(DeleteCourseCommand cmd) {
        Course course = getCourseOrThrow(cmd.courseId());
        course.delete(cmd.deletedBy(), cmd.reason());
        Course saved = courseRepository.save(course);
        publishDomainEvents(saved);
    }

    // ========== 章节命令处理 ==========

    public ChapterId createChapter(CreateChapterCommand cmd) {
        Course course = getCourseOrThrow(cmd.courseId());
        
        if (chapterRepository.existsByCodeAndCourseId(cmd.code(), cmd.courseId())) {
            throw new ChapterCodeAlreadyExistsException(cmd.code());
        }

        Chapter chapter = Chapter.create(cmd.courseId(), null, cmd.code(), cmd.name(),
            cmd.description(), cmd.parentId(), cmd.order(), cmd.difficulty(), cmd.estimatedHours(), cmd.createdBy());

        Chapter saved = chapterRepository.save(chapter);
        publishDomainEvents(saved);
        return saved.getId();
    }

    public void updateChapterInfo(UpdateChapterInfoCommand cmd) {
        Chapter chapter = getChapterOrThrow(cmd.chapterId());
        chapter.updateInfo(cmd.name(), cmd.description(), cmd.difficulty(), cmd.estimatedHours(), cmd.updatedBy());
        Chapter saved = chapterRepository.save(chapter);
        publishDomainEvents(saved);
    }

    public void updateChapterCode(UpdateChapterCodeCommand cmd) {
        Chapter chapter = getChapterOrThrow(cmd.chapterId());
        
        if (chapterRepository.existsByCodeAndCourseIdAndIdNot(cmd.code(), chapter.getCourseId(), cmd.chapterId())) {
            throw new ChapterCodeAlreadyExistsException(cmd.code());
        }
        
        chapter.updateCode(cmd.code(), cmd.updatedBy());
        Chapter saved = chapterRepository.save(chapter);
        publishDomainEvents(saved);
    }

    public void updateChapterOrder(UpdateChapterOrderCommand cmd) {
        Chapter chapter = getChapterOrThrow(cmd.chapterId());
        chapter.updateOrder(cmd.order(), cmd.updatedBy());
        Chapter saved = chapterRepository.save(chapter);
        publishDomainEvents(saved);
    }

    public void addChapterKnowledgePoint(AddChapterKnowledgePointCommand cmd) {
        Chapter chapter = getChapterOrThrow(cmd.chapterId());
        chapter.addKnowledgePoint(cmd.kpId(), cmd.updatedBy());
        Chapter saved = chapterRepository.save(chapter);
        publishDomainEvents(saved);
    }

    public void removeChapterKnowledgePoint(RemoveChapterKnowledgePointCommand cmd) {
        Chapter chapter = getChapterOrThrow(cmd.chapterId());
        chapter.removeKnowledgePoint(cmd.kpId(), cmd.updatedBy());
        Chapter saved = chapterRepository.save(chapter);
        publishDomainEvents(saved);
    }

    public void publishChapter(PublishChapterCommand cmd) {
        Chapter chapter = getChapterOrThrow(cmd.chapterId());
        chapter.publish(cmd.publishedBy());
        Chapter saved = chapterRepository.save(chapter);
        publishDomainEvents(saved);
    }

    public void deleteChapter(DeleteChapterCommand cmd) {
        Chapter chapter = getChapterOrThrow(cmd.chapterId());
        chapter.delete(cmd.deletedBy(), cmd.reason());
        Chapter saved = chapterRepository.save(chapter);
        publishDomainEvents(saved);
    }

    // ========== 知识点命令处理 ==========

    public KnowledgePointId createKnowledgePoint(CreateKnowledgePointCommand cmd) {
        Course course = getCourseOrThrow(cmd.courseId());
        
        if (knowledgePointRepository.existsByCodeAndCourseId(cmd.code(), cmd.courseId())) {
            throw new KnowledgePointCodeAlreadyExistsException(cmd.code());
        }

        KnowledgePoint kp = KnowledgePoint.create(cmd.courseId(), null, cmd.code(), cmd.name(),
            cmd.description(), cmd.chapterId(), cmd.difficulty(), cmd.createdBy());

        KnowledgePoint saved = knowledgePointRepository.save(kp);
        publishDomainEvents(saved);
        return saved.getId();
    }

    public void updateKnowledgePointInfo(UpdateKnowledgePointInfoCommand cmd) {
        KnowledgePoint kp = getKnowledgePointOrThrow(cmd.kpId());
        kp.updateInfo(cmd.name(), cmd.description(), cmd.difficulty(), cmd.updatedBy());
        KnowledgePoint saved = knowledgePointRepository.save(kp);
        publishDomainEvents(saved);
    }

    public void updateKnowledgePointCode(UpdateKnowledgePointCodeCommand cmd) {
        KnowledgePoint kp = getKnowledgePointOrThrow(cmd.kpId());
        
        if (knowledgePointRepository.existsByCodeAndCourseIdAndIdNot(cmd.code(), kp.getCourseId(), cmd.kpId())) {
            throw new KnowledgePointCodeAlreadyExistsException(cmd.code());
        }
        
        kp.updateCode(cmd.code(), cmd.updatedBy());
        KnowledgePoint saved = knowledgePointRepository.save(kp);
        publishDomainEvents(saved);
    }

    public void addKnowledgePointPrerequisite(AddKnowledgePointPrerequisiteCommand cmd) {
        KnowledgePoint kp = getKnowledgePointOrThrow(cmd.kpId());
        kp.addPrerequisite(cmd.prerequisiteId(), cmd.updatedBy());
        KnowledgePoint saved = knowledgePointRepository.save(kp);
        publishDomainEvents(saved);
    }

    public void removeKnowledgePointPrerequisite(RemoveKnowledgePointPrerequisiteCommand cmd) {
        KnowledgePoint kp = getKnowledgePointOrThrow(cmd.kpId());
        kp.removePrerequisite(cmd.prerequisiteId(), cmd.updatedBy());
        KnowledgePoint saved = knowledgePointRepository.save(kp);
        publishDomainEvents(saved);
    }

    public void addKnowledgePointRelatedPoint(AddKnowledgePointRelatedCommand cmd) {
        KnowledgePoint kp = getKnowledgePointOrThrow(cmd.kpId());
        kp.addRelatedPoint(cmd.relatedId(), cmd.updatedBy());
        KnowledgePoint saved = knowledgePointRepository.save(kp);
        publishDomainEvents(saved);
    }

    public void removeKnowledgePointRelatedPoint(RemoveKnowledgePointRelatedCommand cmd) {
        KnowledgePoint kp = getKnowledgePointOrThrow(cmd.kpId());
        kp.removeRelatedPoint(cmd.relatedId(), cmd.updatedBy());
        KnowledgePoint saved = knowledgePointRepository.save(kp);
        publishDomainEvents(saved);
    }

    public void updateKnowledgePointMetadata(UpdateKnowledgePointMetadataCommand cmd) {
        KnowledgePoint kp = getKnowledgePointOrThrow(cmd.kpId());
        kp.updateMetadata(cmd.metadata(), cmd.updatedBy());
        KnowledgePoint saved = knowledgePointRepository.save(kp);
        publishDomainEvents(saved);
    }

    public void deleteKnowledgePoint(DeleteKnowledgePointCommand cmd) {
        KnowledgePoint kp = getKnowledgePointOrThrow(cmd.kpId());
        kp.delete(cmd.deletedBy(), cmd.reason());
        KnowledgePoint saved = knowledgePointRepository.save(kp);
        publishDomainEvents(saved);
    }

    // ========== 资源命令处理 ==========

    public ResourceId createResource(CreateResourceCommand cmd) {
        Course course = getCourseOrThrow(cmd.courseId());
        
        Resource resource = Resource.create(cmd.courseId(), null, cmd.chapterId(),
            cmd.type(), cmd.title(), cmd.description(), cmd.contentUrl(), cmd.coverUrl(),
            cmd.metadata(), cmd.difficulty(), cmd.tags(), cmd.creatorId());

        Resource saved = resourceRepository.save(resource);
        publishDomainEvents(saved);
        return saved.getId();
    }

    public void updateResourceInfo(UpdateResourceInfoCommand cmd) {
        Resource resource = getResourceOrThrow(cmd.resourceId());
        resource.updateInfo(cmd.title(), cmd.description(), cmd.contentUrl(), cmd.coverUrl(),
            cmd.difficulty(), cmd.tags(), cmd.updatedBy());
        Resource saved = resourceRepository.save(resource);
        publishDomainEvents(saved);
    }

    public void updateResourceMetadata(UpdateResourceMetadataCommand cmd) {
        Resource resource = getResourceOrThrow(cmd.resourceId());
        resource.updateMetadata(cmd.metadata(), cmd.updatedBy());
        Resource saved = resourceRepository.save(resource);
        publishDomainEvents(saved);
    }

    public void publishResource(PublishResourceCommand cmd) {
        Resource resource = getResourceOrThrow(cmd.resourceId());
        resource.publish(cmd.publishedBy());
        Resource saved = resourceRepository.save(resource);
        publishDomainEvents(saved);
    }

    public void deleteResource(DeleteResourceCommand cmd) {
        Resource resource = getResourceOrThrow(cmd.resourceId());
        resource.delete(cmd.deletedBy(), cmd.reason());
        Resource saved = resourceRepository.save(resource);
        publishDomainEvents(saved);
    }

    // ========== 查询处理 ==========

    @Transactional(readOnly = true)
    public CourseDetailDto findCourseById(CourseId id) {
        return courseQueryService.findById(id);
    }

    @Transactional(readOnly = true)
    public CourseDetailDto findCourseByCode(String tenantId, String code) {
        return courseQueryService.findByCode(tenantId, code);
    }

    @Transactional(readOnly = true)
    public PageResult<CourseListDto> findCoursePage(CoursePageQuery query) {
        return courseQueryService.findPage(query);
    }

    @Transactional(readOnly = true)
    public List<CourseSimpleDto> findCoursesByCreatorId(String creatorId) {
        return courseQueryService.findByCreatorId(creatorId);
    }

    @Transactional(readOnly = true)
    public CourseStatisticsDto courseStatistics(String tenantId) {
        return courseQueryService.statistics(tenantId);
    }

    @Transactional(readOnly = true)
    public ChapterDetailDto findChapterById(ChapterId id) {
        return chapterQueryService.findById(id);
    }

    @Transactional(readOnly = true)
    public PageResult<ChapterListDto> findChapterPage(ChapterPageQuery query) {
        return chapterQueryService.findPage(query);
    }

    @Transactional(readOnly = true)
    public List<ChapterSimpleDto> findChaptersByCourseId(CourseId courseId) {
        return chapterQueryService.findByCourseId(courseId);
    }

    @Transactional(readOnly = true)
    public List<ChapterSimpleDto> findChaptersByParentId(ChapterId parentId) {
        return chapterQueryService.findByParentId(parentId);
    }

    @Transactional(readOnly = true)
    public KnowledgePointDetailDto findKnowledgePointById(KnowledgePointId id) {
        return knowledgePointQueryService.findById(id);
    }

    @Transactional(readOnly = true)
    public PageResult<KnowledgePointListDto> findKnowledgePointPage(KnowledgePointPageQuery query) {
        return knowledgePointQueryService.findPage(query);
    }

    @Transactional(readOnly = true)
    public List<KnowledgePointSimpleDto> findKnowledgePointsByCourseId(CourseId courseId) {
        return knowledgePointQueryService.findByCourseId(courseId);
    }

    @Transactional(readOnly = true)
    public List<KnowledgePointSimpleDto> findKnowledgePointsByChapterId(ChapterId chapterId) {
        return knowledgePointQueryService.findByChapterId(chapterId);
    }

    @Transactional(readOnly = true)
    public ResourceDetailDto findResourceById(ResourceId id) {
        return resourceQueryService.findById(id);
    }

    @Transactional(readOnly = true)
    public PageResult<ResourceListDto> findResourcePage(ResourcePageQuery query) {
        return resourceQueryService.findPage(query);
    }

    @Transactional(readOnly = true)
    public List<ResourceSimpleDto> findResourcesByCourseId(CourseId courseId) {
        return resourceQueryService.findByCourseId(courseId);
    }

    @Transactional(readOnly = true)
    public List<ResourceSimpleDto> findResourcesByChapterId(ChapterId chapterId) {
        return resourceQueryService.findByChapterId(chapterId);
    }

    @Transactional(readOnly = true)
    public List<ResourceSimpleDto> findResourcesByKpId(KnowledgePointId kpId) {
        return resourceQueryService.findByKpId(kpId);
    }

    @Transactional(readOnly = true)
    public List<ResourceSimpleDto> findResourcesByType(ResourceType type) {
        return resourceQueryService.findByType(type);
    }

    // ========== 私有方法 ==========

    private Course getCourseOrThrow(CourseId id) {
        return courseRepository.findById(id)
            .orElseThrow(() -> new CourseNotFoundException(id));
    }

    private Chapter getChapterOrThrow(ChapterId id) {
        return chapterRepository.findById(id)
            .orElseThrow(() -> new ChapterNotFoundException(id));
    }

    private KnowledgePoint getKnowledgePointOrThrow(KnowledgePointId id) {
        return knowledgePointRepository.findById(id)
            .orElseThrow(() -> new KnowledgePointNotFoundException(id));
    }

    private Resource getResourceOrThrow(ResourceId id) {
        return resourceRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(id));
    }

    private void publishDomainEvents(Course course) {
        List<DomainEvent> events = course.getDomainEvents();
        if (!events.isEmpty()) {
            domainEventPublisher.publishAll(events);
            course.clearDomainEvents();
        }
    }

    private void publishDomainEvents(Chapter chapter) {
        List<DomainEvent> events = chapter.getDomainEvents();
        if (!events.isEmpty()) {
            domainEventPublisher.publishAll(events);
            chapter.clearDomainEvents();
        }
    }

    private void publishDomainEvents(KnowledgePoint kp) {
        List<DomainEvent> events = kp.getDomainEvents();
        if (!events.isEmpty()) {
            domainEventPublisher.publishAll(events);
            kp.clearDomainEvents();
        }
    }

    private void publishDomainEvents(Resource resource) {
        List<DomainEvent> events = resource.getDomainEvents();
        if (!events.isEmpty()) {
            domainEventPublisher.publishAll(events);
            resource.clearDomainEvents();
        }
    }
}
