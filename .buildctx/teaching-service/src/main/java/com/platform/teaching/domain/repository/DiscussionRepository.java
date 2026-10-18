package com.platform.teaching.domain.repository;

import com.platform.teaching.domain.model.*;
import com.platform.teaching.domain.model.valueobjects.*;
// import com.platform.teaching.domain.model.Discussion;
import com.platform.teaching.domain.model.valueobjects.DiscussionId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 讨论仓储接口
 */
public interface DiscussionRepository {
    Discussion save(Discussion discussion);

    Optional<Discussion> findById(DiscussionId id);

    Optional<Discussion> findById(UUID id);

    List<Discussion> findByTenantId(UUID tenantId);

    Page<Discussion> findByTenantId(UUID tenantId, Pageable pageable);

    List<Discussion> findByCourseId(UUID courseId);

    Page<Discussion> findByCourseId(UUID courseId, Pageable pageable);

    List<Discussion> findByLearningPathId(UUID learningPathId);

    Page<Discussion> findByLearningPathId(UUID learningPathId, Pageable pageable);

    Page<Discussion> findByStepId(UUID stepId, Pageable pageable);

    List<Discussion> findByStepId(UUID stepId);

    List<Discussion> findByAuthorId(UUID authorId);

    List<Discussion> findByTenantIdAndStatus(UUID tenantId, DiscussionStatus status);

    Page<Discussion> findByTenantIdAndStatus(UUID tenantId, DiscussionStatus status, Pageable pageable);

    boolean existsById(DiscussionId id);

    void deleteById(DiscussionId id);

    long countByTenantId(UUID tenantId);

    long countByTenantIdAndStatus(UUID tenantId, DiscussionStatus status);

    List<Discussion> findAvailableDiscussions(UUID tenantId);

    List<Discussion> findPinnedDiscussions(UUID tenantId);

    Page<Discussion> findByAuthorId(UUID authorId, Pageable pageable);
}