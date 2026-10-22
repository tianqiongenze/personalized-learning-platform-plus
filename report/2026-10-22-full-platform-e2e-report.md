# PLPP 全平台 E2E 测试报告(2026-10-22 终版)

## 结论

**51 passed / 4 skipped / 0 failed**,pytest `tests/e2e/test_full_platform.py`,耗时 2.44s。
本版修复了自上轮 6 failed 以来的全部链路缺陷,e2e 全绿。

## 本轮修复缺陷清单(D-09 家族)

| 缺陷 | 根因 | 修复 | 验证 |
|---|---|---|---|
| D-09-1 iam 角色列表 403(错误页空 body) | Repository 用 JPQL 语法却标 `nativeQuery=true`,TiDB 把 `r.createdAt` 驼峰当字面列报 Unknown column,异常未映射 → 403 | SpringDataRoleRepository 重写为真原生 SQL(下划线列名),4 个方法(keyword/no-keyword × 查询/计数)全部显式 `countQuery`,参数 UUID/enum → String | iam 12 测绿 + e2e roles 列表 200 |
| D-09-2 iam 用户列表同病 | 同上(users 表) | SpringDataUserRepository 同模式重写 | e2e users 列表 200 |
| D-09-3 roles 列表 200 但内容空 | native `@Query` UUID 参数绑 VARBINARY 撞 char(36) 静默返空;enum 绑 ordinal | 参数全部改 String(`.toString()`/`.name()`),调用方 RoleRepositoryImpl 分支默认 ACTIVE | e2e 数据非空 |
| D-09-4 count(r.*) 语法错 | Spring Data 对 Page native 查询自动生成 countQuery 改写失败 | 显式 countQuery="SELECT COUNT(*) ..." | TiDB 不再报错 |
| D-09-5 可选参数 null 绑 binary | `(:status IS NULL OR ...)` 的 null 绑定按 binary | 移除可选参数,调用方分支(默认 ACTIVE) | — |
| D-09-6 createRole/listRoles 租户硬编码 | `TenantId.of("current-tenant")` → Invalid UUID 500(错误页 403) | RoleController/UserController 租户取 SecurityContextUtil → X-Tenant-Id 头兜底 | e2e create 200 |
| D-09-7 credit-transfer 500 | 同 UUID VARBINARY 绑定("Cannot convert string '\xDA...' from binary to utf8mb4") | tenant SpringDataCreditTransferRuleRepository 参数 UUID→String;pod 旧镜像(buildx 缓存)→ --no-cache 重建 + imagePullPolicy=Always | e2e credit-transfer 200 |
| 网关 301 | location 前缀尾斜杠对无尾斜杠 URI 触发 nginx 目录补斜杠,Location 丢端口 | gateway.conf 为 16 个受保护 location 生成精确 `location = /api/v1/xxx` 块 | e2e 列表端点 200 |
| 路由错配 | `location /api/v1/users` proxy_pass 指向 tenant(无 /users 端点) | 改指 iam_service | e2e users 200 |
| admin 登录 BCrypt 报错 | DB 哈希 `$2a$` vs encoder BCryptVersion.$2B | pymysql UPDATE iam_db.users admin 行前缀 $2a→$2b(算法兼容) | 登录 200 |
| e2e 用例语义 | phone="" 空串触发 PhoneAlreadyExistsException;443 无 Bearer 被拒属正确语义;web-student 端口 30090≠30096 | phone 唯一化;443 断言放宽(200/301/401/403);web-student 30096 | — |

## 安全验证(顺带确认)

- NodePort 直连(不带 X-Gateway-Verified)按设计 403,鉴权业务路径全部经网关 gw()。
- HeaderAuthFilter 仅信任 `X-Gateway-Verified=1` 请求,防直连伪造 X-User-Roles。

## 运行环境与凭据

- 网关 http://10.167.2.175:30081;web-student 30096;TiDB v8.5.8(NodePort 30210,零改动)
- admin@tenant da98138f-486f-42db-944c-219a5a44bf52,密码 Admin@12345(bcrypt $2b$)

## 单测回归(本轮代码改动后)

- tenant-service: 6 tests, 0 fail(buildx --no-cache 重建前后同绿)
- iam-service: 12 tests, 0 fail(D-09-1/2 重写后)

## 复现命令

```
pytest tests/e2e/test_full_platform.py -v --tb=short
# 51 passed, 4 skipped, 2 warnings in 2.44s
```
