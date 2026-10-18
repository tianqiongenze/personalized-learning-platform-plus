package com.platform.tenant.application.service;

import com.platform.tenant.application.command.*;
import com.platform.tenant.application.query.*;
import com.platform.tenant.domain.event.DomainEvent;
import com.platform.tenant.domain.model.Tenant;
import com.platform.tenant.domain.model.valueobjects.TenantId;
import com.platform.tenant.domain.repository.TenantRepository;
import com.platform.tenant.infrastructure.event.DomainEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 租户应用服务
 * 编排领域对象完成用例，处理事务边界
 */
@Transactional

class TenantNotFoundException extends RuntimeException {
    public TenantNotFoundException(TenantId id) {
        super("租户不存在: " + id);
    }
}
