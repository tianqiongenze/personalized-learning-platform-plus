package com.platform.teaching.infrastructure.persistence.repository;

import com.platform.teaching.domain.model.valueobjects.*;
import com.platform.teaching.domain.model.LiveSession;
import com.platform.teaching.domain.model.valueobjects.LiveSessionId;
import com.platform.teaching.domain.repository.LiveSessionRepository;
import com.platform.teaching.infrastructure.persistence.entity.LiveSessionEntity;
import com.platform.teaching.infrastructure.persistence.mapper.LiveSessionEntityMapper;
import com.platform.teaching.infrastructure.persistence.repository.spring.SpringDataLiveSessionRepository;
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
 * 直播会话仓储实现
 */
@Repository
@RequiredArgsConstructor
public class LiveSessionRepositoryImpl implements LiveSessionRepository {

    private final SpringDataLiveSessionRepository springDataRepository;
    private final LiveSessionEntityMapper mapper;

    @Override
    public LiveSession save(LiveSession liveSession) {
        LiveSessionEntity entity = mapper.toEntity(liveSession);
        LiveSessionEntity saved = springDataRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<LiveSession> findById(LiveSessionId id) {
        return springDataRepository.findById(id.getValue())
                .map(mapper::toDomain);
    }

    @Override
    public Optional<LiveSession> findById(UUID id) {
        return springDataRepository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public List<LiveSession> findByTenantId(UUID tenantId) {
        return springDataRepository.findByTenantId(tenantId).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Page<LiveSession> findByTenantId(UUID tenantId, Pageable pageable) {
        return springDataRepository.findByTenantId(tenantId, pageable)
                .map(mapper::toDomain);
    }

    @Override
    public List<LiveSession> findByCourseId(UUID courseId) {
        return springDataRepository.findByCourseId(courseId).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Page<LiveSession> findByCourseId(UUID courseId, Pageable pageable) {
        return springDataRepository.findByCourseId(courseId, pageable)
                .map(mapper::toDomain);
    }

    @Override
    public List<LiveSession> findByLearningPathId(UUID learningPathId) {
        return springDataRepository.findByLearningPathId(learningPathId).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<LiveSession> findByInstructorId(UUID instructorId) {
        return springDataRepository.findByInstructorId(instructorId).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<LiveSession> findByTenantIdAndStatus(UUID tenantId, LiveSessionStatus status) {
        return springDataRepository.findByTenantIdAndStatus(tenantId, status.name()).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Page<LiveSession> findByTenantIdAndStatus(UUID tenantId, LiveSessionStatus status, Pageable pageable) {
        return springDataRepository.findByTenantIdAndStatus(tenantId, status.name(), pageable)
                .map(mapper::toDomain);
    }

    @Override
    public List<LiveSession> findUpcomingSessions(UUID tenantId, LocalDateTime from, LocalDateTime to) {
        return springDataRepository.findUpcomingSessions(tenantId, from, to).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<LiveSession> findLiveSessions(UUID tenantId) {
        return springDataRepository.findLiveSessions(tenantId).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public boolean existsById(LiveSessionId id) {
        return springDataRepository.existsById(id.getValue());
    }

    @Override
    public void deleteById(LiveSessionId id) {
        springDataRepository.deleteById(id.getValue());
    }

    @Override
    public long countByTenantId(UUID tenantId) {
        return springDataRepository.countByTenantId(tenantId);
    }

    @Override
    public long countByTenantIdAndStatus(UUID tenantId, LiveSessionStatus status) {
        return springDataRepository.countByTenantIdAndStatus(tenantId, status.name());
    }

    @Override
    public Page<LiveSession> findByStepId(UUID stepId, Pageable pageable) {
        return springDataRepository.findAll(pageable).map(mapper::toDomain);
    }

    @Override
    public Page<LiveSession> findByLearningPathId(UUID learningPathId, Pageable pageable) {
        return springDataRepository.findAll(pageable).map(mapper::toDomain);
    }

    @Override
    public Page<LiveSession> findByInstructorId(UUID instructorId, Pageable pageable) {
        return springDataRepository.findAll(pageable).map(mapper::toDomain);
    }
}