package com.platform.teaching.infrastructure.persistence.repository.spring;

import com.platform.teaching.infrastructure.persistence.entity.LiveSessionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 直播会话 Spring Data JPA 仓储
 */
@Repository
public interface SpringDataLiveSessionRepository extends JpaRepository<LiveSessionEntity, UUID> {

    List<LiveSessionEntity> findByTenantId(UUID tenantId);

    Page<LiveSessionEntity> findByTenantId(UUID tenantId, Pageable pageable);

    List<LiveSessionEntity> findByCourseId(UUID courseId);

    Page<LiveSessionEntity> findByCourseId(UUID courseId, Pageable pageable);

    List<LiveSessionEntity> findByLearningPathId(UUID learningPathId);

    List<LiveSessionEntity> findByInstructorId(UUID instructorId);

    List<LiveSessionEntity> findByTenantIdAndStatus(UUID tenantId, String status);

    Page<LiveSessionEntity> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);

    @Query(value="SELECT l FROM live_sessions l WHERE l.tenantId = :tenantId AND l.status IN ('SCHEDULED', 'PREPARING') AND l.scheduledStartAt BETWEEN :from AND :to ORDER BY l.scheduledStartAt ASC", nativeQuery=true)
    List<LiveSessionEntity> findUpcomingSessions(@Param("tenantId") UUID tenantId, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query(value="SELECT l FROM live_sessions l WHERE l.tenantId = :tenantId AND l.status = 'LIVE'", nativeQuery=true)
    List<LiveSessionEntity> findLiveSessions(@Param("tenantId") UUID tenantId);

    long countByTenantId(UUID tenantId);

    long countByTenantIdAndStatus(UUID tenantId, String status);
}