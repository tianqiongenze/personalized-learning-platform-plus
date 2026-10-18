package com.platform.teaching.infrastructure.persistence.repository.spring;

import com.platform.teaching.infrastructure.persistence.entity.LearningPathStepEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 学习路径步骤 Spring Data JPA 仓储
 */
@Repository
public interface SpringDataLearningPathStepRepository extends JpaRepository<LearningPathStepEntity, UUID> {

    List<LearningPathStepEntity> findByLearningPathId(UUID learningPathId);

    List<LearningPathStepEntity> findByLearningPathIdOrderBySequenceAsc(UUID learningPathId);

    Optional<LearningPathStepEntity> findByLearningPathIdAndSequence(UUID learningPathId, Integer sequence);

    List<LearningPathStepEntity> findByContentId(UUID contentId);

    void deleteByLearningPathId(UUID learningPathId);

    long countByLearningPathId(UUID learningPathId);
}