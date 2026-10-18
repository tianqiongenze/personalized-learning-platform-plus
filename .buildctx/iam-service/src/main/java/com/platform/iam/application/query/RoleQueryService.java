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
 * 用户列表DTO
 */

public interface RoleQueryService {
    RoleDetailDto findById(RoleId id);
    RoleDetailDto findByCode(TenantId tenantId, String code);
    PageResult<RoleListDto> findPage(RolePageQuery query);
    List<RoleSimpleDto> findByUserId(UserId userId);
    List<RoleSimpleDto> findSystemRoles();
    RoleStatisticsDto statistics(TenantId tenantId);
}
