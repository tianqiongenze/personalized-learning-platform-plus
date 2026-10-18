-- IAM 服务初始 schema (PostgreSQL / CockroachDB 双兼容)
-- CRDB 注意: 使用 UUID 主键避免单调递增热点

CREATE TABLE IF NOT EXISTS users (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID NOT NULL,
    username              VARCHAR(50) NOT NULL,
    email                 VARCHAR(255) NOT NULL,
    phone                 VARCHAR(20),
    password_hash         VARCHAR(255) NOT NULL,
    status                VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    role_ids              UUID[],
    last_login_at         TIMESTAMPTZ,
    failed_login_attempts INT NOT NULL DEFAULT 0,
    locked_at             TIMESTAMPTZ,
    locked_reason         VARCHAR(500),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at            TIMESTAMPTZ,
    deleted_by            VARCHAR(100),
    delete_reason         VARCHAR(500),
    version               BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_users_tenant_username ON users (tenant_id, username);
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_tenant_email ON users (tenant_id, email);
CREATE INDEX IF NOT EXISTS idx_users_status ON users (status);
CREATE INDEX IF NOT EXISTS idx_users_created_at ON users (created_at);

CREATE TABLE IF NOT EXISTS roles (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL,
    code        VARCHAR(50) NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    permissions JSONB,
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    is_system   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ,
    deleted_by  VARCHAR(100),
    delete_reason VARCHAR(500),
    version     BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_roles_tenant_code ON roles (tenant_id, code);

-- 内置系统角色(幂等)
INSERT INTO roles (tenant_id, code, name, description, permissions, status, is_system)
SELECT '00000000-0000-0000-0000-000000000000', 'ADMIN', '系统管理员', '平台最高管理员', '["*"]', 'ACTIVE', TRUE
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE code = 'ADMIN');
INSERT INTO roles (tenant_id, code, name, description, permissions, status, is_system)
SELECT '00000000-0000-0000-0000-000000000000', 'TEACHER', '教师', '教师角色', '["course:write","teaching:write"]', 'ACTIVE', TRUE
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE code = 'TEACHER');
INSERT INTO roles (tenant_id, code, name, description, permissions, status, is_system)
SELECT '00000000-0000-0000-0000-000000000000', 'STUDENT', '学生', '学生角色', '["course:read","learning:write"]', 'ACTIVE', TRUE
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE code = 'STUDENT');
