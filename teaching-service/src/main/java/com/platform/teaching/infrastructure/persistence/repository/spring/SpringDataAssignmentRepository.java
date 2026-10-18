package com.platform.teaching.infrastructure.persistence.repository.spring;

import com.platform.teaching.infrastructure.persistence.entity.AssignmentEntity;
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
 * 作业 Spring Data JPA 仓储
 */
@Repository
public interface SpringDataAssignmentRepository extends JpaRepository<AssignmentEntity, UUID> {

    List<AssignmentEntity> findByTenantId(UUID tenantId);

    Page<AssignmentEntity> findByTenantId(UUID tenantId, Pageable pageable);

    List<AssignmentEntity> findByTenantIdAndStatus(UUID tenantId, String status);

    Page<AssignmentEntity> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);

    List<AssignmentEntity> findByCourseId(UUID courseId);

    List<AssignmentEntity> findByLearningPathId(UUID learningPathId);

    List<AssignmentEntity> findByStepId(UUID stepId);

    @Query(value="SELECT a FROM assignments a WHERE a.tenantId = :tenantId AND a.status IN ('PUBLISHED', 'ONGOING') AND (a.startAt IS NULL OR a.startAt <= :now) AND (a.dueAt IS NULL OR a.dueAt >= :now OR a.allowLateSubmission = true)", nativeQuery=true)
    List<AssignmentEntity> findOpenForSubmission(@Param("tenantId") UUID tenantId, @Param("now") LocalDateTime now);

    List<AssignmentEntity> findByCreatedBy(UUID createdBy);

    long countByTenantId(UUID tenantId);

    long countByTenantIdAndStatus(UUID tenantId, String status);
}