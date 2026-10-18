package com.platform.iam.application.service.exceptions;

import com.platform.iam.domain.model.valueobjects.RoleId;
import com.platform.iam.domain.model.valueobjects.UserId;

/**
 * IAM 领域异常集合(包内可见, 由应用服务抛出、REST 层映射)
 */
/**
 * 异常类
 */

public class RoleNotFoundException extends RuntimeException {
    public RoleNotFoundException(RoleId id) {
        super("角色不存在: " + id);
    }
}
