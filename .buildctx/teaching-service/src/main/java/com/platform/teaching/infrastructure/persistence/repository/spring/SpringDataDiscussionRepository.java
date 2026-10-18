package com.platform.teaching.infrastructure.persistence.repository.spring;

import com.platform.teaching.infrastructure.persistence.entity.DiscussionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 讨论 Spring Data JPA 仓储
 */
@Repository
public interface SpringDataDiscussionRepository extends JpaRepository<DiscussionEntity, UUID> {

    List<DiscussionEntity> findByTenantId(UUID tenantId);

    Page<DiscussionEntity> findByTenantId(UUID tenantId, Pageable pageable);

    List<DiscussionEntity> findByTenantIdAndStatus(UUID tenantId, String status);

    Page<DiscussionEntity> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);

    List<DiscussionEntity> findByCourseId(UUID courseId);

    List<DiscussionEntity> findByLearningPathId(UUID learningPathId);

    List<DiscussionEntity> findByStepId(UUID stepId);

    List<DiscussionEntity> findByAuthorId(UUID authorId);

    List<DiscussionEntity> findByTenantIdAndIsPinnedTrue(UUID tenantId);

    long countByTenantId(UUID tenantId);

    long countByTenantIdAndStatus(UUID tenantId, String status);
}