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
 * 角色分页查询参数
 */

public interface UserQueryService {
    UserDetailDto findById(UserId id);
    UserDetailDto findByUsername(TenantId tenantId, String username);
    UserDetailDto findByEmail(TenantId tenantId, String email);
    PageResult<UserListDto> findPage(UserPageQuery query);
    List<UserSimpleDto> findByRoleId(RoleId roleId);
    UserStatisticsDto userStatistics(TenantId tenantId);
}
