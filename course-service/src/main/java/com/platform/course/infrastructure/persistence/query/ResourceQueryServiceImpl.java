package com.platform.course.infrastructure.persistence.query;

import com.platform.course.application.query.*;
import com.platform.course.domain.model.valueobjects.*;
import com.platform.course.infrastructure.persistence.entity.ResourceEntity;
import com.platform.course.infrastructure.persistence.repository.SpringDataResourceRepository;
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

/** Resource 域查询实现(CQRS 读侧) */
@Service
@Transactional(readOnly = true)
public class ResourceQueryServiceImpl implements ResourceQueryService {

    private final SpringDataResourceRepository resourceRepository;

    public ResourceQueryServiceImpl(SpringDataResourceRepository resourceRepository) {
        this.resourceRepository = resourceRepository;
    }

    @Override
    public ResourceDetailDto findById(ResourceId id) {
        return resourceRepository.findById(id.getValue()).map(ResourceQueryServiceImpl::toDetail)
                .orElseThrow(() -> new IllegalArgumentException("资源不存在: " + id));
    }

    @Override
    public ResourceDetailDto findByCode(CourseId courseId, String code) {
        return resourceRepository.findByCourseIdAndCode(courseId.getValue(), code)
                .map(ResourceQueryServiceImpl::toDetail)
                .orElseThrow(() -> new IllegalArgumentException("资源不存在: " + code));
    }

    @Override
    public PageResult<ResourceListDto> findPage(ResourcePageQuery query) {
        Page<ResourceEntity> page = resourceRepository.findByCourseId(query.courseId().getValue(),
                PageRequest.of(Math.max(query.page(), 0), Math.max(query.size(), 1)));
        List<ResourceListDto> items = page.getContent().stream()
                .map(ResourceQueryServiceImpl::toList).collect(Collectors.toList());
        return new PageResult<>(items, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
    }

    @Override
    public List<ResourceSimpleDto> findByCourseId(CourseId courseId) {
        return resourceRepository.findByCourseId(courseId.getValue()).stream()
                .map(r -> new ResourceSimpleDto(r.getId().toString(), r.getTitle(), r.getType()))
                .collect(Collectors.toList());
    }

    @Override
    public List<ResourceSimpleDto> findByChapterId(ChapterId chapterId) {
        return resourceRepository.findByChapterId(chapterId.getValue()).stream()
                .map(r -> new ResourceSimpleDto(r.getId().toString(), r.getTitle(), r.getType()))
                .collect(Collectors.toList());
    }

    @Override
    public List<ResourceSimpleDto> findByKpId(KnowledgePointId kpId) {
        return resourceRepository.findByKpIdsContaining(kpId.getValue()).stream()
                .map(r -> new ResourceSimpleDto(r.getId().toString(), r.getTitle(), r.getType()))
                .collect(Collectors.toList());
    }

    @Override
    public List<ResourceSimpleDto> findByType(ResourceType type) {
        return resourceRepository.findByType(type).stream()
                .map(r -> new ResourceSimpleDto(r.getId().toString(), r.getTitle(), r.getType()))
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

    private static ResourceDetailDto toDetail(ResourceEntity r) {
        Set<KnowledgePointId> kpIds = r.getKpIds() == null ? Set.of()
                : Arrays.stream(r.getKpIds()).map(KnowledgePointId::of).collect(Collectors.toCollection(HashSet::new));
        Set<String> tags = r.getTags() == null ? Set.of() : new HashSet<>(Arrays.asList(r.getTags()));
        return new ResourceDetailDto(r.getId().toString(), r.getCourseId().toString(), r.getTenantId().toString(),
                r.getChapterId() == null ? null : ChapterId.of(r.getChapterId()),
                kpIds, r.getType(), r.getTitle(), r.getDescription(), r.getContentUrl(), r.getCoverUrl(),
                parseJson(r.getMetadataJson()), r.getDifficulty(), tags,
                r.getStatus() == null ? null : r.getStatus().name(), r.getVersion(),
                r.getCreatorId(), r.getCreatedAt(), r.getUpdatedAt(),
                r.getDeletedAt(), r.getDeletedBy(), r.getDeleteReason());
    }

    private static ResourceListDto toList(ResourceEntity r) {
        Set<String> tags = r.getTags() == null ? Set.of() : new HashSet<>(Arrays.asList(r.getTags()));
        return new ResourceListDto(r.getId().toString(), r.getCourseId().toString(), r.getTitle(),
                r.getDescription(), r.getType(), r.getStatus() == null ? null : r.getStatus().name(),
                r.getDifficulty(), tags, r.getVersion(), r.getCreatedAt());
    }
}
