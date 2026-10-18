package com.platform.tenant.application.service;

/**
 * 学分互认规则不存在或未匹配异常
 */
public class CreditTransferRuleNotFoundException extends RuntimeException {
    public CreditTransferRuleNotFoundException(String message) {
        super(message);
    }
}
