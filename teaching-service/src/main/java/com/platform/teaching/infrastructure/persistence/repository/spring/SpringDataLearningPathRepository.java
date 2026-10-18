package com.platform.teaching.infrastructure.persistence.repository.spring;

import com.platform.teaching.infrastructure.persistence.entity.LearningPathEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 学习路径 Spring Data JPA 仓储
 */
@Repository
public interface SpringDataLearningPathRepository extends JpaRepository<LearningPathEntity, UUID> {

    List<LearningPathEntity> findByTenantId(UUID tenantId);

    Page<LearningPathEntity> findByTenantId(UUID tenantId, Pageable pageable);

    List<LearningPathEntity> findByCourseId(UUID courseId);

    Page<LearningPathEntity> findByCourseId(UUID courseId, Pageable pageable);

    List<LearningPathEntity> findByTenantIdAndStatus(UUID tenantId, String status);

    Page<LearningPathEntity> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);

    List<LearningPathEntity> findByCreatedBy(UUID createdBy);

    long countByTenantId(UUID tenantId);

    @Query(value="SELECT l FROM learning_paths l WHERE l.tenantId = :tenantId AND l.status = :status", nativeQuery=true)
    List<LearningPathEntity> findByTenantIdAndStatusNative(@Param("tenantId") UUID tenantId, @Param("status") String status);
}