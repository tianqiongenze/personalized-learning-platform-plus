package com.platform.teaching.domain.repository;

import com.platform.teaching.domain.model.LearningPathStep;
import com.platform.teaching.domain.model.valueobjects.StepId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 学习路径步骤仓储接口
 */
public interface LearningPathStepRepository {
    LearningPathStep save(LearningPathStep step);

    Optional<LearningPathStep> findById(StepId id);

    Optional<LearningPathStep> findById(UUID id);

    List<LearningPathStep> findByLearningPathId(UUID learningPathId);

    List<LearningPathStep> findByLearningPathIdOrderBySequenceAsc(UUID learningPathId);

    Optional<LearningPathStep> findByLearningPathIdAndSequence(UUID learningPathId, Integer sequence);

    List<LearningPathStep> findByContentId(UUID contentId);

    boolean existsById(StepId id);

    void deleteById(StepId id);

    void deleteByLearningPathId(UUID learningPathId);

    long countByLearningPathId(UUID learningPathId);
}