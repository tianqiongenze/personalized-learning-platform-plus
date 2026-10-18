package com.platform.teaching.domain.repository;

import com.platform.teaching.domain.model.Assignment;
import com.platform.teaching.domain.model.valueobjects.*;
import com.platform.teaching.domain.model.valueobjects.AssignmentId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 作业仓储接口
 */
public interface AssignmentRepository {
    Assignment save(Assignment assignment);

    Optional<Assignment> findById(AssignmentId id);

    Optional<Assignment> findById(UUID id);

    List<Assignment> findByTenantId(UUID tenantId);

    Page<Assignment> findByTenantId(UUID tenantId, Pageable pageable);

    List<Assignment> findByCourseId(UUID courseId);

    Page<Assignment> findByCourseId(UUID courseId, Pageable pageable);

    List<Assignment> findByLearningPathId(UUID learningPathId);

    Page<Assignment> findByLearningPathId(UUID learningPathId, Pageable pageable);

    Page<Assignment> findByStepId(UUID stepId, Pageable pageable);

    List<Assignment> findByStepId(UUID stepId);

    List<Assignment> findByTenantIdAndStatus(UUID tenantId, AssignmentStatus status);

    Page<Assignment> findByTenantIdAndStatus(UUID tenantId, AssignmentStatus status, Pageable pageable);

    List<Assignment> findByCreatedBy(UUID createdBy);

    boolean existsById(AssignmentId id);

    void deleteById(AssignmentId id);

    long countByTenantId(UUID tenantId);

    long countByTenantIdAndStatus(UUID tenantId, AssignmentStatus status);

    List<Assignment> findOpenForSubmission(UUID tenantId, java.time.LocalDateTime now);
}