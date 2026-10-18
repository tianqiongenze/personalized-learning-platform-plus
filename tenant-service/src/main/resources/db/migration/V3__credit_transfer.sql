-- V3__credit_transfer.sql
-- 学分互认规则引擎（P2-C）：跨租户课程/成绩学分映射

-- 学分互认规则表
CREATE TABLE credit_transfer_rules (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    source_tenant_id UUID NOT NULL,
    target_tenant_id UUID NOT NULL,
    source_course_code VARCHAR(100) NOT NULL,
    target_course_code VARCHAR(100) NOT NULL,
    credit_ratio DECIMAL(5,2) NOT NULL DEFAULT 1.0,
    min_score INT NOT NULL DEFAULT 60,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 学分互认记录表
CREATE TABLE credit_transfer_records (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    student_id UUID NOT NULL,
    rule_id UUID NOT NULL,
    source_score INT NOT NULL,
    granted_credits DECIMAL(5,2) NOT NULL,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 索引
CREATE INDEX idx_ctr_source ON credit_transfer_rules(source_tenant_id, source_course_code);
CREATE INDEX idx_ctr_target ON credit_transfer_rules(target_tenant_id);
CREATE INDEX idx_ctr_status ON credit_transfer_rules(status);
CREATE INDEX idx_ctr_records_student ON credit_transfer_records(student_id);
CREATE INDEX idx_ctr_records_rule ON credit_transfer_records(rule_id);

-- 更新时间触发器
CREATE TRIGGER update_credit_transfer_rules_updated_at
    BEFORE UPDATE ON credit_transfer_rules
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
