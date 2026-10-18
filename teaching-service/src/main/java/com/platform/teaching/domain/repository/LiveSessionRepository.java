package com.platform.teaching.domain.repository;

import com.platform.teaching.domain.model.*;
import com.platform.teaching.domain.model.valueobjects.*;
//LiveSession;
import com.platform.teaching.domain.model.valueobjects.LiveSessionId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 直播会话仓储接口
 */
public interface LiveSessionRepository {
    LiveSession save(LiveSession liveSession);

    Optional<LiveSession> findById(LiveSessionId id);

    Optional<LiveSession> findById(UUID id);

    List<LiveSession> findByTenantId(UUID tenantId);

    Page<LiveSession> findByTenantId(UUID tenantId, Pageable pageable);

    List<LiveSession> findByCourseId(UUID courseId);

    Page<LiveSession> findByCourseId(UUID courseId, Pageable pageable);

    List<LiveSession> findByLearningPathId(UUID learningPathId);

    Page<LiveSession> findByLearningPathId(UUID learningPathId, Pageable pageable);

    Page<LiveSession> findByStepId(UUID stepId, Pageable pageable);

    List<LiveSession> findByInstructorId(UUID instructorId);

    Page<LiveSession> findByInstructorId(UUID instructorId, Pageable pageable);

    List<LiveSession> findByTenantIdAndStatus(UUID tenantId, LiveSessionStatus status);

    Page<LiveSession> findByTenantIdAndStatus(UUID tenantId, LiveSessionStatus status, Pageable pageable);

    List<LiveSession> findUpcomingSessions(UUID tenantId, java.time.LocalDateTime from, java.time.LocalDateTime to);

    List<LiveSession> findLiveSessions(UUID tenantId);

    boolean existsById(LiveSessionId id);

    void deleteById(LiveSessionId id);

    long countByTenantId(UUID tenantId);

    long countByTenantIdAndStatus(UUID tenantId, LiveSessionStatus status);
}