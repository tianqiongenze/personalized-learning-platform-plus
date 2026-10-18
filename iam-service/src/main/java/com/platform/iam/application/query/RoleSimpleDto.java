package com.platform.iam.application.query;

import com.platform.iam.domain.model.User;
import com.platform.iam.domain.model.Role;
import com.platform.iam.domain.model.valueobjects.*;
import java.util.List;
import java.util.Set;

/**
 * 用户查询服务接口
 */

/**
 * 用户分页查询参数
 */

public record RoleSimpleDto(
    String id,
    String code,
    String name
) {}

/**
 * 权限DTO
 */
