package com.platform.teaching.domain.repository;

import com.platform.teaching.domain.model.*;
import com.platform.teaching.domain.model.valueobjects.*;
//LearningPath;
import com.platform.teaching.domain.model.valueobjects.LearningPathId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 学习路径仓储接口
 */
public interface LearningPathRepository {
    LearningPath save(LearningPath learningPath);

    Optional<LearningPath> findById(LearningPathId id);

    Optional<LearningPath> findById(UUID id);

    List<LearningPath> findByTenantId(UUID tenantId);

    Page<LearningPath> findByTenantId(UUID tenantId, Pageable pageable);

    List<LearningPath> findByCourseId(UUID courseId);

    Page<LearningPath> findByCourseId(UUID courseId, Pageable pageable);

    List<LearningPath> findByTenantIdAndStatus(UUID tenantId, LearningPathStatus status);

    Page<LearningPath> findByTenantIdAndStatus(UUID tenantId, LearningPathStatus status, Pageable pageable);

    List<LearningPath> findByCreatedBy(UUID createdBy);

    boolean existsById(LearningPathId id);

    void deleteById(LearningPathId id);

    long countByTenantId(UUID tenantId);
}