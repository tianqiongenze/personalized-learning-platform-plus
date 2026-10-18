-- V1__init_course_schema.sql
-- 课程资源服务初始化数据库脚本

-- 创建扩展
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 创建课程表
CREATE TABLE courses (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(2000),
    subject VARCHAR(50),
    grade_level VARCHAR(20),
    semester VARCHAR(20),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    cover_url VARCHAR(500),
    settings JSONB NOT NULL DEFAULT '{}',
    creator_id VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at TIMESTAMPTZ,
    archived_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    deleted_by VARCHAR(100),
    delete_reason VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0
);

-- 创建索引
CREATE INDEX idx_courses_tenant_code ON courses(tenant_id, code);
CREATE INDEX idx_courses_status ON courses(status);
CREATE INDEX idx_courses_creator ON courses(creator_id);
CREATE INDEX idx_courses_created_at ON courses(created_at DESC);
CREATE INDEX idx_courses_deleted_at ON courses(deleted_at) WHERE deleted_at IS NOT NULL;

-- 创建更新时间触发器函数
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

-- 创建触发器
CREATE TRIGGER update_courses_updated_at
    BEFORE UPDATE ON courses
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- 创建章节表
CREATE TABLE chapters (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    course_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(1000),
    parent_id UUID,
    "order" INT NOT NULL DEFAULT 0,
    difficulty INT NOT NULL DEFAULT 3,
    estimated_hours INT NOT NULL DEFAULT 0,
    knowledge_point_ids UUID[],
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    deleted_by VARCHAR(100),
    delete_reason VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0
);

-- 创建索引
CREATE INDEX idx_chapters_course_code ON chapters(course_id, code);
CREATE INDEX idx_chapters_parent ON chapters(parent_id);
CREATE INDEX idx_chapters_status ON chapters(status);
CREATE INDEX idx_chapters_order ON chapters(course_id, "order");

-- 创建触发器
CREATE TRIGGER update_chapters_updated_at
    BEFORE UPDATE ON chapters
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- 创建知识点表
CREATE TABLE knowledge_points (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    course_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(1000),
    chapter_id UUID,
    difficulty INT NOT NULL DEFAULT 3,
    prerequisites UUID[],
    related_points UUID[],
    metadata JSONB NOT NULL DEFAULT '{}',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    deleted_by VARCHAR(100),
    delete_reason VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0
);

-- 创建索引
CREATE INDEX idx_kp_course_code ON knowledge_points(course_id, code);
CREATE INDEX idx_kp_chapter ON knowledge_points(chapter_id);
CREATE INDEX IF NOT EXISTS idx_kp_status ON knowledge_points(status);
CREATE INDEX idx_kp_created_at ON knowledge_points(created_at DESC);

-- 创建触发器
CREATE TRIGGER update_knowledge_points_updated_at
    BEFORE UPDATE ON knowledge_points
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- 创建资源表
CREATE TABLE resources (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    course_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    chapter_id UUID,
    code VARCHAR(50) NOT NULL,
    type VARCHAR(20) NOT NULL,
    title VARCHAR(300) NOT NULL,
    description VARCHAR(2000),
    content_url VARCHAR(500),
    cover_url VARCHAR(500),
    metadata JSONB NOT NULL DEFAULT '{}',
    difficulty INT NOT NULL DEFAULT 3,
    tags TEXT[],
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    version INT NOT NULL DEFAULT 1,
    creator_id VARCHAR(100) NOT NULL,
    kp_ids UUID[],
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    deleted_by VARCHAR(100),
    delete_reason VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0
);

-- 创建索引
CREATE INDEX idx_resources_course_code ON resources(course_id, code);
CREATE INDEX idx_resources_chapter ON resources(chapter_id);
CREATE INDEX idx_resources_type ON resources(type);
CREATE INDEX idx_resources_status ON resources(status);
CREATE INDEX idx_resources_created_at ON resources(created_at DESC);

-- 创建触发器
CREATE TRIGGER update_resources_updated_at
    BEFORE UPDATE ON resources
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- 插入默认系统课程（用于系统级操作）
INSERT INTO courses (id, tenant_id, code, name, description, subject, grade_level, semester, status, settings, creator_id)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000001',
    'SYSTEM',
    '系统课程',
    '平台系统级课程，用于跨租户管理操作',
    'SYSTEM',
    'ALL',
    'ALL',
    'PUBLISHED',
    '{}',
    'system'
) ON CONFLICT (tenant_id, code) DO NOTHING;