package com.platform.iam.application.service.exceptions;

import com.platform.iam.domain.model.valueobjects.RoleId;
import com.platform.iam.domain.model.valueobjects.UserId;

/**
 * IAM 领域异常集合(包内可见, 由应用服务抛出、REST 层映射)
 */
/**
 * 异常类
 */

public class RoleCodeAlreadyExistsException extends RuntimeException {
    public RoleCodeAlreadyExistsException(String code) {
        super("角色编码已存在: " + code);
    }
}
