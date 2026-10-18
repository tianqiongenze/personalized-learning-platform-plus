-- V1__init_teaching_schema.sql
-- 教学互动服务数据库初始化脚本

-- 启用 UUID 扩展
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ==========================================
-- 学习路径表
-- ==========================================
CREATE TABLE learning_paths (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    course_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    published_at TIMESTAMP
);

CREATE INDEX idx_learning_paths_tenant_id ON learning_paths(tenant_id);
CREATE INDEX idx_learning_paths_course_id ON learning_paths(course_id);
CREATE INDEX idx_learning_paths_status ON learning_paths(status);
CREATE INDEX idx_learning_paths_created_by ON learning_paths(created_by);

-- ==========================================
-- 学习路径步骤表
-- ==========================================
CREATE TABLE learning_path_steps (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    learning_path_id UUID NOT NULL REFERENCES learning_paths(id) ON DELETE CASCADE,
    sequence INTEGER NOT NULL,
    step_type VARCHAR(20) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    content_id UUID,
    content_type VARCHAR(50),
    estimated_minutes INTEGER,
    is_required BOOLEAN NOT NULL DEFAULT TRUE,
    prerequisite_step_id UUID,
    status VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED',
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX idx_learning_path_steps_learning_path_id ON learning_path_steps(learning_path_id);
CREATE INDEX idx_learning_path_steps_sequence ON learning_path_steps(learning_path_id, sequence);
CREATE INDEX idx_learning_path_steps_content_id ON learning_path_steps(content_id);
CREATE INDEX idx_learning_path_steps_prerequisite ON learning_path_steps(prerequisite_step_id);

-- ==========================================
-- 作业表
-- ==========================================
CREATE TABLE assignments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    course_id UUID NOT NULL,
    learning_path_id UUID,
    step_id UUID,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    instructions TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    max_score INTEGER NOT NULL DEFAULT 100,
    passing_score INTEGER NOT NULL DEFAULT 60,
    allow_late_submission BOOLEAN NOT NULL DEFAULT FALSE,
    late_penalty_percent INTEGER DEFAULT 10,
    max_attempts INTEGER NOT NULL DEFAULT 3,
    start_at TIMESTAMP,
    due_at TIMESTAMP,
    created_by UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    published_at TIMESTAMP
);

CREATE INDEX idx_assignments_tenant_id ON assignments(tenant_id);
CREATE INDEX idx_assignments_course_id ON assignments(course_id);
CREATE INDEX idx_assignments_learning_path_id ON assignments(learning_path_id);
CREATE INDEX idx_assignments_step_id ON assignments(step_id);
CREATE INDEX idx_assignments_status ON assignments(status);
CREATE INDEX idx_assignments_created_by ON assignments(created_by);
CREATE INDEX idx_assignments_due_at ON assignments(due_at);

-- ==========================================
-- 考试表
-- ==========================================
CREATE TABLE exams (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    course_id UUID NOT NULL,
    learning_path_id UUID,
    step_id UUID,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    instructions TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    duration_minutes INTEGER NOT NULL DEFAULT 60,
    max_score INTEGER NOT NULL DEFAULT 100,
    passing_score INTEGER NOT NULL DEFAULT 60,
    max_attempts INTEGER NOT NULL DEFAULT 1,
    shuffle_questions BOOLEAN NOT NULL DEFAULT TRUE,
    shuffle_options BOOLEAN NOT NULL DEFAULT TRUE,
    show_results_immediately BOOLEAN NOT NULL DEFAULT TRUE,
    start_at TIMESTAMP,
    end_at TIMESTAMP,
    created_by UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    published_at TIMESTAMP
);

CREATE INDEX idx_exams_tenant_id ON exams(tenant_id);
CREATE INDEX idx_exams_course_id ON exams(course_id);
CREATE INDEX idx_exams_learning_path_id ON exams(learning_path_id);
CREATE INDEX idx_exams_step_id ON exams(step_id);
CREATE INDEX idx_exams_status ON exams(status);
CREATE INDEX idx_exams_created_by ON exams(created_by);
CREATE INDEX idx_exams_start_at ON exams(start_at);
CREATE INDEX idx_exams_end_at ON exams(end_at);

-- ==========================================
-- 讨论表
-- ==========================================
CREATE TABLE discussions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    course_id UUID NOT NULL,
    learning_path_id UUID,
    step_id UUID,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    author_id UUID NOT NULL,
    author_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    is_pinned BOOLEAN NOT NULL DEFAULT FALSE,
    is_locked BOOLEAN NOT NULL DEFAULT FALSE,
    view_count BIGINT NOT NULL DEFAULT 0,
    reply_count BIGINT NOT NULL DEFAULT 0,
    last_reply_at TIMESTAMP,
    last_reply_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX idx_discussions_tenant_id ON discussions(tenant_id);
CREATE INDEX idx_discussions_course_id ON discussions(course_id);
CREATE INDEX idx_discussions_learning_path_id ON discussions(learning_path_id);
CREATE INDEX idx_discussions_step_id ON discussions(step_id);
CREATE INDEX idx_discussions_author_id ON discussions(author_id);
CREATE INDEX idx_discussions_status ON discussions(status);
CREATE INDEX idx_discussions_pinned ON discussions(tenant_id, is_pinned) WHERE is_pinned = TRUE;
CREATE INDEX idx_discussions_created_at ON discussions(created_at DESC);

-- ==========================================
-- 直播会话表
-- ==========================================
CREATE TABLE live_sessions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    course_id UUID NOT NULL,
    learning_path_id UUID,
    step_id UUID,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    instructor_id UUID NOT NULL,
    instructor_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    stream_key VARCHAR(100),
    playback_url VARCHAR(500),
    max_participants INTEGER NOT NULL DEFAULT 100,
    current_participants INTEGER NOT NULL DEFAULT 0,
    scheduled_start_at TIMESTAMP NOT NULL,
    scheduled_end_at TIMESTAMP NOT NULL,
    actual_start_at TIMESTAMP,
    actual_end_at TIMESTAMP,
    recording_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    chat_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX idx_live_sessions_tenant_id ON live_sessions(tenant_id);
CREATE INDEX idx_live_sessions_course_id ON live_sessions(course_id);
CREATE INDEX idx_live_sessions_learning_path_id ON live_sessions(learning_path_id);
CREATE INDEX idx_live_sessions_instructor_id ON live_sessions(instructor_id);
CREATE INDEX idx_live_sessions_status ON live_sessions(status);
CREATE INDEX idx_live_sessions_scheduled_start ON live_sessions(scheduled_start_at);
CREATE INDEX idx_live_sessions_created_by ON live_sessions(created_by);

-- ==========================================
-- 更新时间触发器函数
-- ==========================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- 为所有表添加更新时间触发器
CREATE TRIGGER update_learning_paths_updated_at
    BEFORE UPDATE ON learning_paths
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_learning_path_steps_updated_at
    BEFORE UPDATE ON learning_path_steps
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_assignments_updated_at
    BEFORE UPDATE ON assignments
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_exams_updated_at
    BEFORE UPDATE ON exams
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_discussions_updated_at
    BEFORE UPDATE ON discussions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_live_sessions_updated_at
    BEFORE UPDATE ON live_sessions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ==========================================
-- 权限授予 (根据实际情况调整)
-- ==========================================
-- GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO teaching_user;
-- GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO teaching_user;