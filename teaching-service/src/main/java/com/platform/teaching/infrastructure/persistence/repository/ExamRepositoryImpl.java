package com.platform.teaching.infrastructure.persistence.repository;

import com.platform.teaching.domain.model.valueobjects.*;
import com.platform.teaching.domain.model.Exam;
import com.platform.teaching.domain.model.valueobjects.ExamId;
import com.platform.teaching.domain.repository.ExamRepository;
import com.platform.teaching.infrastructure.persistence.entity.ExamEntity;
import com.platform.teaching.infrastructure.persistence.mapper.ExamEntityMapper;
import com.platform.teaching.infrastructure.persistence.repository.spring.SpringDataExamRepository;
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
 * 考试仓储实现
 */
@Repository
@RequiredArgsConstructor
public class ExamRepositoryImpl implements ExamRepository {

    private final SpringDataExamRepository springDataRepository;
    private final ExamEntityMapper mapper;

    @Override
    public Exam save(Exam exam) {
        ExamEntity entity = mapper.toEntity(exam);
        ExamEntity saved = springDataRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Exam> findById(ExamId id) {
        return springDataRepository.findById(id.getValue())
                .map(mapper::toDomain);
    }

    @Override
    public Optional<Exam> findById(UUID id) {
        return springDataRepository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public List<Exam> findByTenantId(UUID tenantId) {
        return springDataRepository.findByTenantId(tenantId).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Page<Exam> findByTenantId(UUID tenantId, Pageable pageable) {
        return springDataRepository.findByTenantId(tenantId, pageable)
                .map(mapper::toDomain);
    }

    @Override
    public List<Exam> findByTenantIdAndStatus(UUID tenantId, ExamStatus status) {
        return springDataRepository.findByTenantIdAndStatus(tenantId, status.name()).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Page<Exam> findByTenantIdAndStatus(UUID tenantId, ExamStatus status, Pageable pageable) {
        return springDataRepository.findByTenantIdAndStatus(tenantId, status.name(), pageable)
                .map(mapper::toDomain);
    }

    @Override
    public List<Exam> findByCourseId(UUID courseId) {
        return springDataRepository.findByCourseId(courseId).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Exam> findByLearningPathId(UUID learningPathId) {
        return springDataRepository.findByLearningPathId(learningPathId).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Exam> findByStepId(UUID stepId) {
        return springDataRepository.findByStepId(stepId).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Exam> findAvailableExams(UUID tenantId, LocalDateTime now) {
        return springDataRepository.findAvailableExams(tenantId, now).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Exam> findByCreatedBy(UUID createdBy) {
        return springDataRepository.findByCreatedBy(createdBy).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public boolean existsById(ExamId id) {
        return springDataRepository.existsById(id.getValue());
    }

    @Override
    public void deleteById(ExamId id) {
        springDataRepository.deleteById(id.getValue());
    }

    @Override
    public long countByTenantId(UUID tenantId) {
        return springDataRepository.countByTenantId(tenantId);
    }

    @Override
    public long countByTenantIdAndStatus(UUID tenantId, ExamStatus status) {
        return springDataRepository.countByTenantIdAndStatus(tenantId, status.name());
    }

    @Override
    public Page<Exam> findByCourseId(UUID courseId, Pageable pageable) {
        return springDataRepository.findByCourseId(courseId, pageable).map(mapper::toDomain);
    }

    @Override
    public Page<Exam> findByLearningPathId(UUID learningPathId, Pageable pageable) {
        return springDataRepository.findAll(pageable).map(mapper::toDomain);
    }

    @Override
    public Page<Exam> findByStepId(UUID stepId, Pageable pageable) {
        return springDataRepository.findAll(pageable).map(mapper::toDomain);
    }
}