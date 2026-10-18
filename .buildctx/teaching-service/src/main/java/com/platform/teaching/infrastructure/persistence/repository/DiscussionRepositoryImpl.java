package com.platform.teaching.infrastructure.persistence.repository;

import com.platform.teaching.domain.model.valueobjects.*;
import com.platform.teaching.domain.model.Discussion;
import com.platform.teaching.domain.model.valueobjects.DiscussionId;
import com.platform.teaching.domain.repository.DiscussionRepository;
import com.platform.teaching.infrastructure.persistence.entity.DiscussionEntity;
import com.platform.teaching.infrastructure.persistence.mapper.DiscussionEntityMapper;
import com.platform.teaching.infrastructure.persistence.repository.spring.SpringDataDiscussionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 讨论仓储实现
 */
@Repository
@RequiredArgsConstructor
public class DiscussionRepositoryImpl implements DiscussionRepository {

    private final SpringDataDiscussionRepository springDataRepository;
    private final DiscussionEntityMapper mapper;

    @Override
    public Discussion save(Discussion discussion) {
        DiscussionEntity entity = mapper.toEntity(discussion);
        DiscussionEntity saved = springDataRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Discussion> findById(DiscussionId id) {
        return springDataRepository.findById(id.getValue())
                .map(mapper::toDomain);
    }

    @Override
    public Optional<Discussion> findById(UUID id) {
        return springDataRepository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public List<Discussion> findByTenantId(UUID tenantId) {
        return springDataRepository.findByTenantId(tenantId).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Page<Discussion> findByTenantId(UUID tenantId, Pageable pageable) {
        return springDataRepository.findByTenantId(tenantId, pageable)
                .map(mapper::toDomain);
    }

    @Override
    public List<Discussion> findByTenantIdAndStatus(UUID tenantId, DiscussionStatus status) {
        return springDataRepository.findByTenantIdAndStatus(tenantId, status.name()).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Page<Discussion> findByTenantIdAndStatus(UUID tenantId, DiscussionStatus status, Pageable pageable) {
        return springDataRepository.findByTenantIdAndStatus(tenantId, status.name(), pageable)
                .map(mapper::toDomain);
    }

    @Override
    public List<Discussion> findByCourseId(UUID courseId) {
        return springDataRepository.findByCourseId(courseId).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Discussion> findByLearningPathId(UUID learningPathId) {
        return springDataRepository.findByLearningPathId(learningPathId).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Discussion> findByStepId(UUID stepId) {
        return springDataRepository.findByStepId(stepId).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Discussion> findByAuthorId(UUID authorId) {
        return springDataRepository.findByAuthorId(authorId).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Discussion> findPinnedDiscussions(UUID tenantId) {
        return springDataRepository.findByTenantIdAndIsPinnedTrue(tenantId).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public boolean existsById(DiscussionId id) {
        return springDataRepository.existsById(id.getValue());
    }

    @Override
    public void deleteById(DiscussionId id) {
        springDataRepository.deleteById(id.getValue());
    }

    @Override
    public long countByTenantId(UUID tenantId) {
        return springDataRepository.countByTenantId(tenantId);
    }

    @Override
    public long countByTenantIdAndStatus(UUID tenantId, DiscussionStatus status) {
        return springDataRepository.countByTenantIdAndStatus(tenantId, status.name());
    }

    @Override
    public Page<Discussion> findByLearningPathId(UUID learningPathId, Pageable pageable) {
        return springDataRepository.findAll(pageable).map(mapper::toDomain);
    }

    @Override
    public Page<Discussion> findByStepId(UUID stepId, Pageable pageable) {
        return springDataRepository.findAll(pageable).map(mapper::toDomain);
    }

    @Override
    public Page<Discussion> findByCourseId(UUID courseId, Pageable pageable) {
        return springDataRepository.findAll(pageable).map(mapper::toDomain);
    }

    @Override
    public List<Discussion> findAvailableDiscussions(UUID tenantId) {
        return springDataRepository.findAll().stream().map(mapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public Page<Discussion> findByAuthorId(UUID authorId, Pageable pageable) {
        return springDataRepository.findAll(pageable).map(mapper::toDomain);
    }
}