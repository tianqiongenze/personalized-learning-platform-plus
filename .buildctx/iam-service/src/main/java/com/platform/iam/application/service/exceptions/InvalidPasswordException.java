package com.platform.iam.application.service.exceptions;

import com.platform.iam.domain.model.valueobjects.RoleId;
import com.platform.iam.domain.model.valueobjects.UserId;

/**
 * IAM 领域异常集合(包内可见, 由应用服务抛出、REST 层映射)
 */
/**
 * 异常类
 */

public class InvalidPasswordException extends RuntimeException {
    public InvalidPasswordException(String message) {
        super(message);
    }
}
