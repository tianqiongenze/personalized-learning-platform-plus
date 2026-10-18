package com.platform.course.infrastructure.persistence.query;

import com.platform.course.application.query.*;
import com.platform.course.domain.model.valueobjects.*;
import com.platform.course.infrastructure.persistence.entity.KnowledgePointEntity;
import com.platform.course.infrastructure.persistence.repository.SpringDataKnowledgePointRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** KnowledgePoint 域查询实现(CQRS 读侧) */
@Service
@Transactional(readOnly = true)
public class KnowledgePointQueryServiceImpl implements KnowledgePointQueryService {

    private final SpringDataKnowledgePointRepository kpRepository;

    public KnowledgePointQueryServiceImpl(SpringDataKnowledgePointRepository kpRepository) {
        this.kpRepository = kpRepository;
    }

    @Override
    public KnowledgePointDetailDto findById(KnowledgePointId id) {
        return kpRepository.findById(id.getValue()).map(KnowledgePointQueryServiceImpl::toDetail)
                .orElseThrow(() -> new IllegalArgumentException("知识点不存在: " + id));
    }

    @Override
    public KnowledgePointDetailDto findByCode(CourseId courseId, String code) {
        return kpRepository.findByCourseIdAndCode(courseId.getValue(), code)
                .map(KnowledgePointQueryServiceImpl::toDetail)
                .orElseThrow(() -> new IllegalArgumentException("知识点不存在: " + code));
    }

    @Override
    public PageResult<KnowledgePointListDto> findPage(KnowledgePointPageQuery query) {
        Page<KnowledgePointEntity> page = kpRepository.findByCourseId(query.courseId().getValue(),
                PageRequest.of(Math.max(query.page(), 0), Math.max(query.size(), 1)));
        List<KnowledgePointListDto> items = page.getContent().stream()
                .map(KnowledgePointQueryServiceImpl::toList).collect(Collectors.toList());
        return new PageResult<>(items, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
    }

    @Override
    public List<KnowledgePointSimpleDto> findByCourseId(CourseId courseId) {
        return kpRepository.findByCourseId(courseId.getValue()).stream()
                .map(k -> new KnowledgePointSimpleDto(k.getId().toString(), k.getCode(), k.getName()))
                .collect(Collectors.toList());
    }

    @Override
    public List<KnowledgePointSimpleDto> findByChapterId(ChapterId chapterId) {
        return kpRepository.findByChapterId(chapterId.getValue()).stream()
                .map(k -> new KnowledgePointSimpleDto(k.getId().toString(), k.getCode(), k.getName()))
                .collect(Collectors.toList());
    }

    private static Map<String, Object> parseJson(String json) {
        try {
            return json == null ? Map.of() : CourseQueryServiceImpl.MAPPER.readValue(json,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static KnowledgePointDetailDto toDetail(KnowledgePointEntity k) {
        Set<KnowledgePointId> pre = k.getPrerequisites() == null ? Set.of()
                : Arrays.stream(k.getPrerequisites()).map(KnowledgePointId::of).collect(Collectors.toCollection(HashSet::new));
        Set<KnowledgePointId> rel = k.getRelatedPoints() == null ? Set.of()
                : Arrays.stream(k.getRelatedPoints()).map(KnowledgePointId::of).collect(Collectors.toCollection(HashSet::new));
        return new KnowledgePointDetailDto(k.getId().toString(), k.getCourseId().toString(),
                k.getTenantId().toString(), k.getCode(), k.getName(), k.getDescription(),
                k.getChapterId() == null ? null : ChapterId.of(k.getChapterId()),
                k.getDifficulty(), pre, rel, parseJson(k.getMetadataJson()),
                k.getCreatedAt(), k.getUpdatedAt(), k.getDeletedAt(), k.getDeletedBy(), k.getDeleteReason());
    }

    private static KnowledgePointListDto toList(KnowledgePointEntity k) {
        return new KnowledgePointListDto(k.getId().toString(), k.getCourseId().toString(), k.getCode(),
                k.getName(), k.getDescription(),
                k.getChapterId() == null ? null : ChapterId.of(k.getChapterId()),
                k.getDifficulty(), k.getStatus(), k.getCreatedAt());
    }
}
