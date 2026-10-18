package com.platform.tenant.application.service;

import com.platform.tenant.application.command.ApplyCreditTransferCommand;
import com.platform.tenant.application.command.CreateCreditTransferRuleCommand;
import com.platform.tenant.domain.model.CreditTransferRule;
import com.platform.tenant.domain.repository.CreditTransferRecord;
import com.platform.tenant.domain.repository.CreditTransferRuleRepository;
import com.platform.tenant.domain.repository.CreditTransferRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 学分互认应用服务
 * 编排规则匹配、成绩折算与记录落库
 */
@Service
@Transactional
public class CreditTransferService {

    private final CreditTransferRuleRepository ruleRepository;
    private final CreditTransferRecordRepository recordRepository;

    public CreditTransferService(CreditTransferRuleRepository ruleRepository,
                                 CreditTransferRecordRepository recordRepository) {
        this.ruleRepository = ruleRepository;
        this.recordRepository = recordRepository;
    }

    // ========== 命令处理 ==========

    public CreditTransferRule createRule(CreateCreditTransferRuleCommand cmd) {
        CreditTransferRule rule = CreditTransferRule.create(
            cmd.sourceTenantId(), cmd.targetTenantId(),
            cmd.sourceCourseCode(), cmd.targetCourseCode(),
            cmd.creditRatio(), cmd.minScore()
        );
        return ruleRepository.save(rule);
    }

    public List<CreditTransferRule> listRules(String tenantId) {
        List<CreditTransferRule> rules = ruleRepository.findBySourceTenantId(tenantId);
        if (rules.isEmpty()) {
            rules = ruleRepository.findByTargetTenantId(tenantId);
        }
        return rules;
    }

    /**
     * 申请学分互认。
     * 规则不匹配 -> CreditTransferRuleNotFoundException；
     * 分数不足 -> IllegalStateException（score < min_score）。
     * 匹配则按 credit_ratio 折算写入 credit_transfer_records 并返回 granted_credits。
     */
    public BigDecimal apply(ApplyCreditTransferCommand cmd) {
        CreditTransferRule rule = ruleRepository
            .findActiveBySource(cmd.sourceTenantId(), cmd.sourceCourseCode())
            .orElseThrow(() -> new CreditTransferRuleNotFoundException(
                "未找到可用的学分互认规则: tenant=" + cmd.sourceTenantId()
                    + ", course=" + cmd.sourceCourseCode()));

        int score = cmd.score();
        if (!rule.evaluate(score)) {
            throw new IllegalStateException(
                "成绩未达到互认最低分数: score=" + score + ", minScore=" + rule.getMinScore());
        }

        BigDecimal granted = rule.grantedCredits(score);
        recordRepository.save(CreditTransferRecord.of(
            cmd.studentId(), rule.getId(), score, granted));
        return granted;
    }
}
