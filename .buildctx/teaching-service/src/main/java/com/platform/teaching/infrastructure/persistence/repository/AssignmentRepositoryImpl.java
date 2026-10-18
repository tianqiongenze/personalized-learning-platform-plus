package com.platform.teaching.infrastructure.persistence.repository;

import com.platform.teaching.domain.model.valueobjects.*;
import com.platform.teaching.domain.model.Assignment;
import com.platform.teaching.domain.model.valueobjects.AssignmentId;
import com.platform.teaching.domain.repository.AssignmentRepository;
import com.platform.teaching.infrastructure.persistence.entity.AssignmentEntity;
import com.platform.teaching.infrastructure.persistence.mapper.AssignmentEntityMapper;
import com.platform.teaching.infrastructure.persistence.repository.spring.SpringDataAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 作业仓储实现
 */
@Repository
@RequiredArgsConstructor
public class AssignmentRepositoryImpl implements AssignmentRepository {

    private final SpringDataAssignmentRepository springDataRepository;
    private final AssignmentEntityMapper mapper;

    @Override
    public Assignment save(Assignment assignment) {
        AssignmentEntity entity = mapper.toEntity(assignment);
        AssignmentEntity saved = springDataRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Assignment> findById(AssignmentId id) {
        return springDataRepository.findById(id.getValue())
                .map(mapper::toDomain);
    }

    @Override
    public Optional<Assignment> findById(UUID id) {
        return springDataRepository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public List<Assignment> findByTenantId(UUID tenantId) {
        return springDataRepository.findByTenantId(tenantId).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Page<Assignment> findByTenantId(UUID tenantId, Pageable pageable) {
        return springDataRepository.findByTenantId(tenantId, pageable)
                .map(mapper::toDomain);
    }

    @Override
    public List<Assignment> findByTenantIdAndStatus(UUID tenantId, AssignmentStatus status) {
        return springDataRepository.findByTenantIdAndStatus(tenantId, status.name()).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Page<Assignment> findByTenantIdAndStatus(UUID tenantId, AssignmentStatus status, Pageable pageable) {
        return springDataRepository.findByTenantIdAndStatus(tenantId, status.name(), pageable)
                .map(mapper::toDomain);
    }

    @Override
    public List<Assignment> findByCourseId(UUID courseId) {
        return springDataRepository.findByCourseId(courseId).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Assignment> findByLearningPathId(UUID learningPathId) {
        return springDataRepository.findByLearningPathId(learningPathId).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Assignment> findByStepId(UUID stepId) {
        return springDataRepository.findByStepId(stepId).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Assignment> findOpenForSubmission(UUID tenantId, LocalDateTime now) {
        return springDataRepository.findOpenForSubmission(tenantId, now).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Assignment> findByCreatedBy(UUID createdBy) {
        return springDataRepository.findByCreatedBy(createdBy).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public boolean existsById(AssignmentId id) {
        return springDataRepository.existsById(id.getValue());
    }

    @Override
    public void deleteById(AssignmentId id) {
        springDataRepository.deleteById(id.getValue());
    }

    @Override
    public long countByTenantId(UUID tenantId) {
        return springDataRepository.countByTenantId(tenantId);
    }

    @Override
    public long countByTenantIdAndStatus(UUID tenantId, AssignmentStatus status) {
        return springDataRepository.countByTenantIdAndStatus(tenantId, status.name());
    }

    @Override
    public Page<Assignment> findByStepId(UUID stepId, Pageable pageable) {
        return springDataRepository.findAll(pageable).map(mapper::toDomain);
    }

    @Override
    public Page<Assignment> findByLearningPathId(UUID learningPathId, Pageable pageable) {
        return springDataRepository.findAll(pageable).map(mapper::toDomain);
    }

    @Override
    public Page<Assignment> findByCourseId(UUID courseId, Pageable pageable) {
        return springDataRepository.findAll(pageable).map(mapper::toDomain);
    }
}