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
 * 角色查询服务接口
 */

public record UserSimpleDto(
    String id,
    String username,
    String email
) {}

/**
 * 角色列表DTO
 */
