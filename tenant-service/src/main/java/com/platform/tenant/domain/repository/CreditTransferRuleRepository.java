package com.platform.tenant.domain.repository;

import com.platform.tenant.domain.model.CreditTransferRule;

import java.util.List;
import java.util.Optional;

/**
 * 学分互认规则仓储接口
 */
public interface CreditTransferRuleRepository {

    CreditTransferRule save(CreditTransferRule rule);

    Optional<CreditTransferRule> findById(String id);

    List<CreditTransferRule> findBySourceTenantId(String sourceTenantId);

    List<CreditTransferRule> findByTargetTenantId(String targetTenantId);

    /**
     * 按 源租户 + 源课程编码 + ACTIVE 状态 匹配规则
     */
    Optional<CreditTransferRule> findActiveBySource(String sourceTenantId, String sourceCourseCode);
}
