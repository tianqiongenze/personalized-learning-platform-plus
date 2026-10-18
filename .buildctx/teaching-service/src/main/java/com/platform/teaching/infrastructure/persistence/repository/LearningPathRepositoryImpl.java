package com.platform.teaching.infrastructure.persistence.repository;

import com.platform.teaching.domain.model.valueobjects.*;
import com.platform.teaching.domain.model.LearningPath;
import com.platform.teaching.domain.model.LearningPathStatus;
import com.platform.teaching.domain.model.valueobjects.LearningPathId;
import com.platform.teaching.domain.repository.LearningPathRepository;
import com.platform.teaching.infrastructure.persistence.entity.LearningPathEntity;
import com.platform.teaching.infrastructure.persistence.mapper.LearningPathEntityMapper;
import com.platform.teaching.infrastructure.persistence.repository.spring.SpringDataLearningPathRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 学习路径仓储实现
 */
@Repository
@RequiredArgsConstructor
public class LearningPathRepositoryImpl implements LearningPathRepository {

    private final SpringDataLearningPathRepository springDataRepository;
    private final LearningPathEntityMapper mapper;

    @Override
    public LearningPath save(LearningPath learningPath) {
        LearningPathEntity entity = mapper.toEntity(learningPath);
        LearningPathEntity saved = springDataRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<LearningPath> findById(LearningPathId id) {
        return springDataRepository.findById(id.getValue())
                .map(mapper::toDomain);
    }

    @Override
    public Optional<LearningPath> findById(UUID id) {
        return springDataRepository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public List<LearningPath> findByTenantId(UUID tenantId) {
        return springDataRepository.findByTenantId(tenantId).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Page<LearningPath> findByTenantId(UUID tenantId, Pageable pageable) {
        return springDataRepository.findByTenantId(tenantId, pageable)
                .map(mapper::toDomain);
    }

    @Override
    public List<LearningPath> findByCourseId(UUID courseId) {
        return springDataRepository.findByCourseId(courseId).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Page<LearningPath> findByCourseId(UUID courseId, Pageable pageable) {
        return springDataRepository.findByCourseId(courseId, pageable)
                .map(mapper::toDomain);
    }

    @Override
    public List<LearningPath> findByTenantIdAndStatus(UUID tenantId, LearningPathStatus status) {
        return springDataRepository.findByTenantIdAndStatus(tenantId, status.name()).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Page<LearningPath> findByTenantIdAndStatus(UUID tenantId, LearningPathStatus status, Pageable pageable) {
        return springDataRepository.findByTenantIdAndStatus(tenantId, status.name(), pageable)
                .map(mapper::toDomain);
    }

    @Override
    public List<LearningPath> findByCreatedBy(UUID createdBy) {
        return springDataRepository.findByCreatedBy(createdBy).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public boolean existsById(LearningPathId id) {
        return springDataRepository.existsById(id.getValue());
    }

    @Override
    public void deleteById(LearningPathId id) {
        springDataRepository.deleteById(id.getValue());
    }

    @Override
    public long countByTenantId(UUID tenantId) {
        return springDataRepository.countByTenantId(tenantId);
    }
}