package com.platform.course.infrastructure.persistence.repository;

import com.platform.course.domain.model.Course;
import com.platform.course.domain.model.valueobjects.*;
import com.platform.course.domain.repository.CourseRepository;
import com.platform.course.infrastructure.persistence.entity.CourseEntity;
import com.platform.course.infrastructure.persistence.mapper.CourseEntityMapper;
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
 * 课程仓储实现
 */
@Repository
public class CourseRepositoryImpl implements CourseRepository {

    private final SpringDataCourseRepository jpaRepository;
    private final CourseEntityMapper mapper;

    public CourseRepositoryImpl(SpringDataCourseRepository jpaRepository, CourseEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Course save(Course course) {
        CourseEntity entity = mapper.toEntity(course);
        CourseEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Course> findById(CourseId id) {
        return jpaRepository.findById(id.getValue())
            .map(mapper::toDomain);
    }

    @Override
    public Optional<Course> findByCode(String tenantId, String code) {
        return jpaRepository.findByTenantIdAndCode(UUID.fromString(tenantId), code)
            .map(mapper::toDomain);
    }

    @Override
    public boolean existsByCode(String tenantId, String code) {
        return jpaRepository.existsByTenantIdAndCode(UUID.fromString(tenantId), code);
    }

    @Override
    public List<Course> findByTenantId(String tenantId, int page, int size, String status, String keyword) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        
        CourseStatus statusEnum = status != null ? CourseStatus.valueOf(status.toUpperCase()) : null;
        Page<CourseEntity> result = jpaRepository.findWithFilters(
            UUID.fromString(tenantId), statusEnum, keyword, null, null, pageable);
        
        return result.getContent().stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public long countByTenantId(String tenantId, String status, String keyword) {
        CourseStatus statusEnum = status != null ? CourseStatus.valueOf(status.toUpperCase()) : null;
        return jpaRepository.countWithFilters(UUID.fromString(tenantId), statusEnum, keyword, null, null);
    }

    @Override
    public List<Course> findByCreatorId(String creatorId) {
        return jpaRepository.findByCreatorId(creatorId).stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<Course> findByIds(List<CourseId> ids) {
        List<UUID> uuids = ids.stream().map(CourseId::getValue).collect(Collectors.toList());
        return jpaRepository.findAllById(uuids).stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public void deleteById(CourseId id) {
        jpaRepository.deleteById(id.getValue());
    }

    @Override
    public boolean existsByCodeAndIdNot(String code, CourseId id) {
        return jpaRepository.findAll().stream()
            .anyMatch(e -> code.equals(e.getCode()) && !id.getValue().equals(e.getCourseId()));
    }
}