package com.platform.teaching.infrastructure.persistence.mapper;

import com.platform.teaching.domain.model.LearningPath;
import com.platform.teaching.domain.model.LearningPathStep;
import com.platform.teaching.domain.model.valueobjects.LearningPathId;
import com.platform.teaching.domain.model.valueobjects.StepId;
import com.platform.teaching.infrastructure.persistence.entity.LearningPathEntity;
import com.platform.teaching.infrastructure.persistence.entity.LearningPathStepEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 学习路径实体映射器
 */
@Component
public class LearningPathEntityMapper {

    public LearningPathEntity toEntity(LearningPath domain) {
        if (domain == null) return null;

        LearningPathEntity entity = new LearningPathEntity();
        entity.setId(domain.getId());
        entity.setTenantId(domain.getTenantId());
        entity.setCourseId(domain.getCourseId());
        entity.setTitle(domain.getTitle());
        entity.setDescription(domain.getDescription());
        entity.setStatus(domain.getStatus().name());
        entity.setVersion(domain.getVersion());
        entity.setCreatedBy(domain.getCreatedBy());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());
        entity.setPublishedAt(domain.getPublishedAt());

        if (domain.getSteps() != null) {
            List<LearningPathStepEntity> stepEntities = domain.getSteps().stream()
                    .map(this::toStepEntity)
                    .collect(Collectors.toList());
            entity.setSteps(stepEntities);
            stepEntities.forEach(step -> step.setLearningPath(entity));
        }

        return entity;
    }

    /**
     * D-06 修复配套: 把领域聚合状态拷贝到【受管】持久化实体上(save 前由仓储取出)。
     * 不拷贝 id 与 version —— id 已一致, version 由 @Version 乐观锁机制自行递增,
     * 领域侧的手写 version++(addStep/publish 等)仅作为领域版本语义, 不再灌入持久化层,
     * 避免 detached merge 时应用版本与库内版本错位触发 StaleObjectStateException。
     */
    public void copyToEntity(LearningPath domain, LearningPathEntity entity) {
        if (domain == null || entity == null) return;

        entity.setTenantId(domain.getTenantId());
        entity.setCourseId(domain.getCourseId());
        entity.setTitle(domain.getTitle());
        entity.setDescription(domain.getDescription());
        entity.setStatus(domain.getStatus().name());
        entity.setCreatedBy(domain.getCreatedBy());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());
        entity.setPublishedAt(domain.getPublishedAt());

        // 步骤集合: 受管实体上做差量同步(新增/移除/重排序), 保留已有步骤的受管状态
        if (domain.getSteps() != null) {
            var existing = new java.util.HashMap<UUID, LearningPathStepEntity>();
            entity.getSteps().forEach(s -> existing.put(s.getId(), s));
            List<LearningPathStepEntity> synced = new java.util.ArrayList<>();
            for (LearningPathStep step : domain.getSteps()) {
                LearningPathStepEntity stepEntity = existing.get(step.getId());
                if (stepEntity == null) {
                    stepEntity = toStepEntity(step);
                } else {
                    copyStepToEntity(step, stepEntity);
                    existing.remove(step.getId());
                }
                stepEntity.setLearningPath(entity);
                synced.add(stepEntity);
            }
            entity.getSteps().clear();
            entity.getSteps().addAll(synced);
        }
    }

    public LearningPath toDomain(LearningPathEntity entity) {
        if (entity == null) return null;

        LearningPath domain = new LearningPath();
        domain.setId(entity.getId());
        domain.setTenantId(entity.getTenantId());
        domain.setCourseId(entity.getCourseId());
        domain.setTitle(entity.getTitle());
        domain.setDescription(entity.getDescription());
        domain.setStatus(com.platform.teaching.domain.model.LearningPathStatus.valueOf(entity.getStatus()));
        domain.setVersion(entity.getVersion());
        domain.setCreatedBy(entity.getCreatedBy());
        domain.setCreatedAt(entity.getCreatedAt());
        domain.setUpdatedAt(entity.getUpdatedAt());
        domain.setPublishedAt(entity.getPublishedAt());

        if (entity.getSteps() != null) {
            List<LearningPathStep> steps = entity.getSteps().stream()
                    .map(this::toStepDomain)
                    .collect(Collectors.toList());
            domain.setSteps(steps);
        }

        return domain;
    }

    private LearningPathStepEntity toStepEntity(LearningPathStep step) {
        LearningPathStepEntity entity = new LearningPathStepEntity();
        entity.setId(step.getId());
        entity.setSequence(step.getSequence());
        entity.setStepType(step.getStepType().name());
        entity.setTitle(step.getTitle());
        entity.setDescription(step.getDescription());
        entity.setContentId(step.getContentId());
        entity.setContentType(step.getContentType());
        entity.setEstimatedMinutes(step.getEstimatedMinutes());
        entity.setIsRequired(step.getIsRequired());
        entity.setPrerequisiteStepId(step.getPrerequisiteStepId());
        entity.setStatus(step.getStatus().name());
        entity.setStartedAt(step.getStartedAt());
        entity.setCompletedAt(step.getCompletedAt());
        entity.setCreatedAt(step.getCreatedAt());
        entity.setUpdatedAt(step.getUpdatedAt());
        return entity;
    }

    /**
     * D-06 修复配套: 把领域步骤状态拷贝到受管的步骤实体(不拷 id, 保留受管身份)。
     */
    private void copyStepToEntity(LearningPathStep step, LearningPathStepEntity entity) {
        entity.setSequence(step.getSequence());
        entity.setStepType(step.getStepType().name());
        entity.setTitle(step.getTitle());
        entity.setDescription(step.getDescription());
        entity.setContentId(step.getContentId());
        entity.setContentType(step.getContentType());
        entity.setEstimatedMinutes(step.getEstimatedMinutes());
        entity.setIsRequired(step.getIsRequired());
        entity.setPrerequisiteStepId(step.getPrerequisiteStepId());
        entity.setStatus(step.getStatus().name());
        entity.setStartedAt(step.getStartedAt());
        entity.setCompletedAt(step.getCompletedAt());
        entity.setCreatedAt(step.getCreatedAt());
        entity.setUpdatedAt(step.getUpdatedAt());
    }

    private LearningPathStep toStepDomain(LearningPathStepEntity entity) {
        LearningPathStep step = new LearningPathStep();
        step.setId(entity.getId());
        step.setSequence(entity.getSequence());
        step.setStepType(com.platform.teaching.domain.model.StepType.valueOf(entity.getStepType()));
        step.setTitle(entity.getTitle());
        step.setDescription(entity.getDescription());
        step.setContentId(entity.getContentId());
        step.setContentType(entity.getContentType());
        step.setEstimatedMinutes(entity.getEstimatedMinutes());
        step.setIsRequired(entity.getIsRequired());
        step.setPrerequisiteStepId(entity.getPrerequisiteStepId());
        step.setStatus(com.platform.teaching.domain.model.StepStatus.valueOf(entity.getStatus()));
        step.setStartedAt(entity.getStartedAt());
        step.setCompletedAt(entity.getCompletedAt());
        step.setCreatedAt(entity.getCreatedAt());
        step.setUpdatedAt(entity.getUpdatedAt());
        return step;
    }
}