package com.platform.teaching.infrastructure.persistence.repository.spring;

import com.platform.teaching.infrastructure.persistence.entity.ExamEntity;
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
 * 考试 Spring Data JPA 仓储
 */
@Repository
public interface SpringDataExamRepository extends JpaRepository<ExamEntity, UUID> {

    List<ExamEntity> findByTenantId(UUID tenantId);

    Page<ExamEntity> findByTenantId(UUID tenantId, Pageable pageable);

    List<ExamEntity> findByTenantIdAndStatus(UUID tenantId, String status);

    Page<ExamEntity> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);

    List<ExamEntity> findByCourseId(UUID courseId);

    Page<ExamEntity> findByCourseId(UUID courseId, Pageable pageable);

    List<ExamEntity> findByLearningPathId(UUID learningPathId);

    List<ExamEntity> findByStepId(UUID stepId);

    @Query(value="SELECT e FROM exams e WHERE e.tenantId = :tenantId AND e.status IN ('PUBLISHED', 'IN_PROGRESS', 'SCHEDULED') AND (e.startAt IS NULL OR e.startAt <= :now) AND (e.endAt IS NULL OR e.endAt >= :now)", nativeQuery=true)
    List<ExamEntity> findAvailableExams(@Param("tenantId") UUID tenantId, @Param("now") LocalDateTime now);

    List<ExamEntity> findByCreatedBy(UUID createdBy);

    long countByTenantId(UUID tenantId);

    long countByTenantIdAndStatus(UUID tenantId, String status);
}