package com.platform.tenant.infrastructure.persistence.repository;

import com.platform.tenant.domain.model.CreditTransferRule;
import com.platform.tenant.domain.repository.CreditTransferRuleRepository;
import com.platform.tenant.infrastructure.persistence.entity.CreditTransferRuleEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 学分互认规则仓储实现
 * 适配 Spring Data JPA 到领域仓储接口
 */
@Repository
public class CreditTransferRuleRepositoryImpl implements CreditTransferRuleRepository {

    private final SpringDataCreditTransferRuleRepository jpaRepository;

    public CreditTransferRuleRepositoryImpl(SpringDataCreditTransferRuleRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public CreditTransferRule save(CreditTransferRule rule) {
        CreditTransferRuleEntity saved = jpaRepository.save(toEntity(rule));
        return toDomain(saved);
    }

    @Override
    public Optional<CreditTransferRule> findById(String id) {
        return jpaRepository.findById(UUID.fromString(id))
            .map(this::toDomain);
    }

    @Override
    public List<CreditTransferRule> findBySourceTenantId(String sourceTenantId) {
        return jpaRepository.findBySourceTenantId(sourceTenantId).stream()
            .map(this::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<CreditTransferRule> findByTargetTenantId(String targetTenantId) {
        return jpaRepository.findByTargetTenantId(targetTenantId).stream()
            .map(this::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public Optional<CreditTransferRule> findActiveBySource(String sourceTenantId, String sourceCourseCode) {
        return jpaRepository.findActiveBySource(sourceTenantId, sourceCourseCode)
            .map(this::toDomain);
    }

    private CreditTransferRuleEntity toEntity(CreditTransferRule rule) {
        return CreditTransferRuleEntity.builder()
            .id(UUID.fromString(rule.getId()))
            .sourceTenantId(UUID.fromString(rule.getSourceTenantId()))
            .targetTenantId(UUID.fromString(rule.getTargetTenantId()))
            .sourceCourseCode(rule.getSourceCourseCode())
            .targetCourseCode(rule.getTargetCourseCode())
            .creditRatio(rule.getCreditRatio())
            .minScore(rule.getMinScore())
            .status(CreditTransferRuleEntity.CreditTransferRuleStatus.valueOf(rule.getStatus()))
            .build();
    }

    private CreditTransferRule toDomain(CreditTransferRuleEntity e) {
        return CreditTransferRule.reconstruct(
            e.getId().toString(),
            e.getSourceTenantId().toString(),
            e.getTargetTenantId().toString(),
            e.getSourceCourseCode(),
            e.getTargetCourseCode(),
            e.getCreditRatio(),
            e.getMinScore(),
            e.getStatus().name(),
            e.getVersion()
        );
    }
}
