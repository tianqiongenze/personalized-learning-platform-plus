package com.platform.course.infrastructure.persistence.query;

import com.platform.course.application.query.*;
import com.platform.course.domain.model.valueobjects.*;
import com.platform.course.infrastructure.persistence.entity.ChapterEntity;
import com.platform.course.infrastructure.persistence.repository.SpringDataChapterRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Chapter 域查询实现(CQRS 读侧) */
@Service
@Transactional(readOnly = true)
public class ChapterQueryServiceImpl implements ChapterQueryService {

    private final SpringDataChapterRepository chapterRepository;

    public ChapterQueryServiceImpl(SpringDataChapterRepository chapterRepository) {
        this.chapterRepository = chapterRepository;
    }

    @Override
    public ChapterDetailDto findById(ChapterId id) {
        return chapterRepository.findById(id.getValue()).map(ChapterQueryServiceImpl::toDetail)
                .orElseThrow(() -> new IllegalArgumentException("章节不存在: " + id));
    }

    @Override
    public ChapterDetailDto findByCode(CourseId courseId, String code) {
        return chapterRepository.findByCourseIdAndCode(courseId.getValue(), code)
                .map(ChapterQueryServiceImpl::toDetail)
                .orElseThrow(() -> new IllegalArgumentException("章节不存在: " + code));
    }

    @Override
    public PageResult<ChapterListDto> findPage(ChapterPageQuery query) {
        Page<ChapterEntity> page = chapterRepository.findByCourseId(query.courseId().getValue(),
                PageRequest.of(Math.max(query.page(), 0), Math.max(query.size(), 1), Sort.by("order")));
        List<ChapterListDto> items = page.getContent().stream()
                .map(ChapterQueryServiceImpl::toList).collect(Collectors.toList());
        return new PageResult<>(items, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
    }

    @Override
    public List<ChapterSimpleDto> findByCourseId(CourseId courseId) {
        return chapterRepository.findByCourseId(courseId.getValue()).stream()
                .map(c -> new ChapterSimpleDto(c.getId().toString(), c.getCode(), c.getName()))
                .collect(Collectors.toList());
    }

    @Override
    public List<ChapterSimpleDto> findByParentId(ChapterId parentId) {
        return chapterRepository.findByParentId(parentId.getValue()).stream()
                .map(c -> new ChapterSimpleDto(c.getId().toString(), c.getCode(), c.getName()))
                .collect(Collectors.toList());
    }

    static Set<KnowledgePointId> kpIdsOf(ChapterEntity c) {
        return c.getKnowledgePointIds() == null ? Set.of()
                : Arrays.stream(c.getKnowledgePointIds()).map(KnowledgePointId::of).collect(Collectors.toCollection(HashSet::new));
    }

    private static ChapterDetailDto toDetail(ChapterEntity c) {
        return new ChapterDetailDto(c.getId().toString(), c.getCourseId().toString(), c.getTenantId().toString(),
                c.getCode(), c.getName(), c.getDescription(),
                c.getParentId() == null ? null : ChapterId.of(c.getParentId()),
                c.getOrder(), c.getDifficulty(), c.getEstimatedHours(), kpIdsOf(c),
                c.getStatus() == null ? null : c.getStatus().name(),
                c.getCreatedAt(), c.getUpdatedAt(), c.getDeletedAt(), c.getDeletedBy(), c.getDeleteReason());
    }

    private static ChapterListDto toList(ChapterEntity c) {
        return new ChapterListDto(c.getId().toString(), c.getCourseId().toString(), c.getCode(),
                c.getName(), c.getDescription(),
                c.getParentId() == null ? null : ChapterId.of(c.getParentId()),
                c.getOrder(), c.getDifficulty(), c.getEstimatedHours(),
                c.getStatus() == null ? null : c.getStatus().name(), c.getCreatedAt());
    }
}
