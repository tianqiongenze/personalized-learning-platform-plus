package com.platform.teaching.infrastructure.persistence.repository;

import com.platform.teaching.domain.model.valueobjects.*;
import com.platform.teaching.domain.model.LearningPath;
import com.platform.teaching.domain.model.LearningPathStatus;
import com.platform.teaching.domain.model.valueobjects.LearningPathId;
import com.platform.teaching.domain.repository.LearningPathRepository;
import com.platform.teaching.infrastructure.persistence.entity.LearningPathEntity;
import com.platform.teaching.infrastructure.persistence.mapper.LearningPathEntityMapper;
import com.platform.teaching.infrastructure.persistence.repository.spring.SpringDataLearningPathRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 学习路径仓储实现
 */
@Repository
@RequiredArgsConstructor
public class LearningPathRepositoryImpl implements LearningPathRepository {

    private final SpringDataLearningPathRepository springDataRepository;
    private final LearningPathEntityMapper mapper;

    /**
     * D-06 修复: 原 save 直接把 mapper.toEntity 造出的 detached 实体交给 save()。
     * 实体带 @Version(version 由应用层 addStep/publish 等手动 ++),
     * Spring Data 对非新实体(persistable 判定: version 非 null 即视为已存在)走 merge,
     * merge 读取库内当前 version 再比对 detached 版本 —— 领域侧自增与 @Version 机制叠加,
     * 新建路径后立即 addStep 时 detached version(1) 与库内 version(0, create 落库时
     * Hibernate 未将手写 version++ 同步生效路径一致)不一致 → StaleObjectStateException 500。
     * 改为: 已有 id 的实体先 findById 取受管实体, 把领域状态拷到受管实例再 save
     * (受管实体走 dirty-checking 增量 UPDATE, @Version 自动递增, 不再冲突);
     * 新实体(库中不存在)保持原 save 走 INSERT。
     */
    @Override
    public LearningPath save(LearningPath learningPath) {
        LearningPathEntity entity;
        if (learningPath.getId() != null && springDataRepository.existsById(learningPath.getId())) {
            entity = springDataRepository.findById(learningPath.getId())
                    .orElseThrow(() -> new IllegalStateException(
                            "学习路径实体消失: " + learningPath.getId()));
            mapper.copyToEntity(learningPath, entity);
        } else {
            entity = mapper.toEntity(learningPath);
        }
        LearningPathEntity saved = springDataRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<LearningPath> findById(LearningPathId id) {
        return springDataRepository.findById(id.getValue())
                .map(mapper::toDomain);
    }

    @Override
    public Optional<LearningPath> findById(UUID id) {
        return springDataRepository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public List<LearningPath> findByTenantId(UUID tenantId) {
        return springDataRepository.findByTenantId(tenantId).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Page<LearningPath> findByTenantId(UUID tenantId, Pageable pageable) {
        return springDataRepository.findByTenantId(tenantId, pageable)
                .map(mapper::toDomain);
    }

    @Override
    public List<LearningPath> findByCourseId(UUID courseId) {
        return springDataRepository.findByCourseId(courseId).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Page<LearningPath> findByCourseId(UUID courseId, Pageable pageable) {
        return springDataRepository.findByCourseId(courseId, pageable)
                .map(mapper::toDomain);
    }

    @Override
    public List<LearningPath> findByTenantIdAndStatus(UUID tenantId, LearningPathStatus status) {
        return springDataRepository.findByTenantIdAndStatus(tenantId, status.name()).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Page<LearningPath> findByTenantIdAndStatus(UUID tenantId, LearningPathStatus status, Pageable pageable) {
        return springDataRepository.findByTenantIdAndStatus(tenantId, status.name(), pageable)
                .map(mapper::toDomain);
    }

    @Override
    public List<LearningPath> findByCreatedBy(UUID createdBy) {
        return springDataRepository.findByCreatedBy(createdBy).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public boolean existsById(LearningPathId id) {
        return springDataRepository.existsById(id.getValue());
    }

    @Override
    public void deleteById(LearningPathId id) {
        springDataRepository.deleteById(id.getValue());
    }

    @Override
    public long countByTenantId(UUID tenantId) {
        return springDataRepository.countByTenantId(tenantId);
    }
}