-- =====================================================
-- Flyway 初始化迁移脚本
-- 版本: V1
-- 描述: 创建基础 schema 和验证 Flyway 配置
-- 时区: 所有 TIMESTAMPTZ 字段存储 UTC 时间
-- =====================================================

-- 设置时区为 UTC
SET TIME ZONE 'UTC';

-- 创建扩展 (如果需要)
-- CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 验证 Flyway 配置成功的占位表
-- 此表仅用于验证 Flyway 迁移机制正常工作
-- 后续迁移脚本将创建实际业务表

CREATE TABLE IF NOT EXISTS flyway_validation (
    id SERIAL PRIMARY KEY,
    validation_key VARCHAR(50) NOT NULL UNIQUE,
    validation_value VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 插入验证记录
INSERT INTO flyway_validation (validation_key, validation_value)
VALUES ('flyway_init', 'Flyway migration initialized successfully')
ON CONFLICT (validation_key) DO NOTHING;

COMMENT ON TABLE flyway_validation IS 'Flyway 配置验证表，用于确认迁移机制正常工作';
