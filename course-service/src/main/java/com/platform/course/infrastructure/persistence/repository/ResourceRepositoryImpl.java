package com.platform.course.infrastructure.persistence.repository;

import com.platform.course.domain.model.Resource;
import com.platform.course.domain.model.valueobjects.*;
import com.platform.course.domain.repository.ResourceRepository;
import com.platform.course.infrastructure.persistence.entity.ResourceEntity;
import com.platform.course.infrastructure.persistence.mapper.ResourceEntityMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 资源仓储实现
 */
@Repository
public class ResourceRepositoryImpl implements ResourceRepository {

    private final SpringDataResourceRepository jpaRepository;
    private final ResourceEntityMapper mapper;

    public ResourceRepositoryImpl(SpringDataResourceRepository jpaRepository, ResourceEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Resource save(Resource resource) {
        ResourceEntity entity = mapper.toEntity(resource);
        ResourceEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Resource> findById(ResourceId id) {
        return jpaRepository.findById(id.getValue())
            .map(mapper::toDomain);
    }

    @Override
    public Optional<Resource> findByCode(CourseId courseId, String code) {
        return jpaRepository.findByCourseIdAndCode(courseId.getValue(), code)
            .map(mapper::toDomain);
    }

    @Override
    public boolean existsByCode(CourseId courseId, String code) {
        return jpaRepository.existsByCourseIdAndCode(courseId.getValue(), code);
    }

    @Override
    public List<Resource> findByCourseId(CourseId courseId, int page, int size, String status, String keyword, String type) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        
        ResourceStatus statusEnum = status != null ? ResourceStatus.valueOf(status.toUpperCase()) : null;
        ResourceType typeEnum = type != null ? ResourceType.valueOf(type.toUpperCase()) : null;
        
        Page<ResourceEntity> result = jpaRepository.findWithFilters(
            courseId.getValue(), statusEnum, keyword, typeEnum, null, null, pageable);
        
        return result.getContent().stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public long countByCourseId(CourseId courseId, String status, String keyword, String type) {
        ResourceStatus statusEnum = status != null ? ResourceStatus.valueOf(status.toUpperCase()) : null;
        ResourceType typeEnum = type != null ? ResourceType.valueOf(type.toUpperCase()) : null;
        return jpaRepository.countWithFilters(courseId.getValue(), statusEnum, keyword, typeEnum, null, null);
    }

    @Override
    public List<Resource> findByChapterId(ChapterId chapterId) {
        return jpaRepository.findByChapterId(chapterId.getValue()).stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<Resource> findByKpId(KnowledgePointId kpId) {
        return jpaRepository.findByKpIdsContaining(kpId.getValue()).stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<Resource> findByType(ResourceType type) {
        return jpaRepository.findByType(type).stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<Resource> findByIds(List<ResourceId> ids) {
        List<UUID> uuids = ids.stream().map(ResourceId::getValue).collect(Collectors.toList());
        return jpaRepository.findAllById(uuids).stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public void deleteById(ResourceId id) {
        jpaRepository.deleteById(id.getValue());
    }

    @Override
    public boolean existsByCodeAndIdNot(String code, ResourceId id) {
        return jpaRepository.existsByCodeAndIdNot(code, id.getValue());
    }

    @Override
    public boolean existsByCodeAndCourseId(String code, CourseId courseId) {
        return jpaRepository.existsByCourseIdAndCode(courseId.getValue(), code);
    }
}