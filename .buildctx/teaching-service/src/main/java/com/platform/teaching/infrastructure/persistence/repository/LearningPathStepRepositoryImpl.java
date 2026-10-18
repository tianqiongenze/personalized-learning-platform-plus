package com.platform.teaching.infrastructure.persistence.repository;

import com.platform.teaching.domain.model.LearningPathStep;
import com.platform.teaching.domain.model.valueobjects.StepId;
import com.platform.teaching.domain.repository.LearningPathStepRepository;
import com.platform.teaching.infrastructure.persistence.entity.LearningPathStepEntity;
import com.platform.teaching.infrastructure.persistence.mapper.LearningPathStepEntityMapper;
import com.platform.teaching.infrastructure.persistence.repository.spring.SpringDataLearningPathStepRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 学习路径步骤仓储实现
 */
@Repository
@RequiredArgsConstructor
public class LearningPathStepRepositoryImpl implements LearningPathStepRepository {

    private final SpringDataLearningPathStepRepository springDataRepository;
    private final LearningPathStepEntityMapper mapper;

    @Override
    public LearningPathStep save(LearningPathStep step) {
        LearningPathStepEntity entity = mapper.toStepEntity(step);
        LearningPathStepEntity saved = springDataRepository.save(entity);
        return mapper.toStepDomain(saved);
    }

    @Override
    public Optional<LearningPathStep> findById(StepId id) {
        return springDataRepository.findById(id.getValue())
                .map(mapper::toStepDomain);
    }

    @Override
    public Optional<LearningPathStep> findById(UUID id) {
        return springDataRepository.findById(id)
                .map(mapper::toStepDomain);
    }

    @Override
    public List<LearningPathStep> findByLearningPathId(UUID learningPathId) {
        return springDataRepository.findByLearningPathId(learningPathId).stream()
                .map(mapper::toStepDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<LearningPathStep> findByLearningPathIdOrderBySequenceAsc(UUID learningPathId) {
        return springDataRepository.findByLearningPathIdOrderBySequenceAsc(learningPathId).stream()
                .map(mapper::toStepDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<LearningPathStep> findByLearningPathIdAndSequence(UUID learningPathId, Integer sequence) {
        return springDataRepository.findByLearningPathIdAndSequence(learningPathId, sequence)
                .map(mapper::toStepDomain);
    }

    @Override
    public List<LearningPathStep> findByContentId(UUID contentId) {
        return springDataRepository.findByContentId(contentId).stream()
                .map(mapper::toStepDomain)
                .collect(Collectors.toList());
    }

    @Override
    public boolean existsById(StepId id) {
        return springDataRepository.existsById(id.getValue());
    }

    @Override
    public void deleteById(StepId id) {
        springDataRepository.deleteById(id.getValue());
    }

    @Override
    public void deleteByLearningPathId(UUID learningPathId) {
        springDataRepository.deleteByLearningPathId(learningPathId);
    }

    @Override
    public long countByLearningPathId(UUID learningPathId) {
        return springDataRepository.countByLearningPathId(learningPathId);
    }
}