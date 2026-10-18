-- V1__init_tenant_schema.sql
-- 租户管理服务初始化数据库脚本

-- 创建扩展
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 创建租户表
CREATE TABLE tenants (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(1000),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    quota JSONB NOT NULL DEFAULT '{}',
    branding JSONB NOT NULL DEFAULT '{}',
    config JSONB NOT NULL DEFAULT '{}',
    admin_user_id VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    deleted_by VARCHAR(100),
    delete_reason VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0
);

-- 创建索引
CREATE INDEX idx_tenants_code ON tenants(code);
CREATE INDEX idx_tenants_status ON tenants(status);
CREATE INDEX idx_tenants_admin_user ON tenants(admin_user_id);
CREATE INDEX idx_tenants_created_at ON tenants(created_at DESC);
CREATE INDEX idx_tenants_deleted_at ON tenants(deleted_at) WHERE deleted_at IS NOT NULL;

-- 创建更新时间触发器函数
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

-- 创建触发器
CREATE TRIGGER update_tenants_updated_at
    BEFORE UPDATE ON tenants
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- 插入默认系统租户（用于系统级操作）
INSERT INTO tenants (id, code, name, description, status, quota, branding, config, admin_user_id)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'system',
    '系统租户',
    '平台系统级租户，用于跨租户管理操作',
    'ACTIVE',
    '{"maxUsers": 100000, "maxCourses": 50000, "maxStorageBytes": 10995116277760, "maxApiCallsPerMinute": 1000000, "maxConcurrentSessions": 50000, "maxKnowledgePoints": 5000000, "maxResourcesPerCourse": 20000}',
    '{"logoUrl": null, "primaryColor": "#1890ff", "secondaryColor": "#52c41a", "faviconUrl": null, "customDomain": null, "emailTemplate": null, "loginPageTemplate": null, "customCssVariables": {}}',
    '{"features": {"knowledge_tracing": true, "recommendation": true, "multimodal_analysis": true, "llm_assistant": true, "ab_testing": true, "analytics_dashboard": true, "content_management": true, "collaboration": true}, "integrations": {"sso": {"enabled": "true", "provider": "cas"}, "lti": {"enabled": "true", "version": "1.3"}, "xapi": {"enabled": "true"}}, "limits": {"max_file_upload_mb": 1000, "max_concurrent_kt_requests": 10000, "max_recommendation_batch_size": 5000}, "metadata": {}}',
    'system'
) ON CONFLICT (code) DO NOTHING;

-- 创建租户配额使用统计视图
CREATE OR REPLACE VIEW tenant_quota_usage AS
SELECT 
    t.id,
    t.code,
    t.name,
    t.status,
    t.quota->>'maxUsers' AS max_users,
    t.quota->>'maxCourses' AS max_courses,
    t.quota->>'maxStorageBytes' AS max_storage_bytes,
    COALESCE(u.user_count, 0) AS current_users,
    COALESCE(c.course_count, 0) AS current_courses,
    COALESCE(s.storage_bytes, 0) AS current_storage_bytes
FROM tenants t
LEFT JOIN (
    SELECT tenant_id, COUNT(*) AS user_count
    FROM users
    WHERE deleted_at IS NULL
    GROUP BY tenant_id
) u ON t.id = u.tenant_id
LEFT JOIN (
    SELECT tenant_id, COUNT(*) AS course_count
    FROM courses
    WHERE deleted_at IS NULL
    GROUP BY tenant_id
) c ON t.id = c.tenant_id
LEFT JOIN (
    SELECT tenant_id, SUM(size_bytes) AS storage_bytes
    FROM resources
    WHERE deleted_at IS NULL
    GROUP BY tenant_id
) s ON t.id = s.tenant_id
WHERE t.deleted_at IS NULL;