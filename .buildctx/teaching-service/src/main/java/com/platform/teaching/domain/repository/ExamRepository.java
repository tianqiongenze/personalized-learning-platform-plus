package com.platform.teaching.domain.repository;

import com.platform.teaching.domain.model.Exam;
import com.platform.teaching.domain.model.valueobjects.ExamStatus;
import com.platform.teaching.domain.model.valueobjects.ExamId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 考试仓储接口
 */
public interface ExamRepository {
    Exam save(Exam exam);

    Optional<Exam> findById(ExamId id);

    Optional<Exam> findById(UUID id);

    List<Exam> findByTenantId(UUID tenantId);

    Page<Exam> findByTenantId(UUID tenantId, Pageable pageable);

    List<Exam> findByCourseId(UUID courseId);

    Page<Exam> findByCourseId(UUID courseId, Pageable pageable);

    Page<Exam> findByLearningPathId(UUID learningPathId, Pageable pageable);

    List<Exam> findByLearningPathId(UUID learningPathId);

    List<Exam> findByCreatedBy(UUID createdBy);

    List<Exam> findByTenantIdAndStatus(UUID tenantId, ExamStatus status);

    Page<Exam> findByTenantIdAndStatus(UUID tenantId, ExamStatus status, Pageable pageable);

    long countByTenantId(UUID tenantId);

    long countByTenantIdAndStatus(UUID tenantId, ExamStatus status);

    boolean existsById(ExamId id);

    void deleteById(ExamId id);

    List<Exam> findAvailableExams(UUID tenantId, java.time.LocalDateTime now);

    List<Exam> findByStepId(UUID stepId);

    Page<Exam> findByStepId(UUID stepId, Pageable pageable);
}
