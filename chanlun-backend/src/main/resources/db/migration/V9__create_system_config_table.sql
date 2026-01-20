-- =====================================================
-- Flyway 迁移脚本
-- 版本: V9
-- 描述: 创建系统配置表 (system_config) 并插入预置配置
-- 时区: 所有 TIMESTAMPTZ 字段存储 UTC 时间
-- =====================================================

-- 设置时区为 UTC
SET TIME ZONE 'UTC';

-- 创建系统配置表
CREATE TABLE IF NOT EXISTS system_config (
    id BIGSERIAL PRIMARY KEY,
    config_key VARCHAR(100) NOT NULL,
    config_value TEXT,
    description VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 创建唯一约束
CREATE UNIQUE INDEX uk_system_config_key ON system_config(config_key);

-- 添加表注释
COMMENT ON TABLE system_config IS '系统配置表，存储系统级配置项';
COMMENT ON COLUMN system_config.id IS '主键';
COMMENT ON COLUMN system_config.config_key IS '配置键';
COMMENT ON COLUMN system_config.config_value IS '配置值';
COMMENT ON COLUMN system_config.description IS '配置说明';
COMMENT ON COLUMN system_config.created_at IS '创建时间 (UTC)';
COMMENT ON COLUMN system_config.updated_at IS '更新时间 (UTC)';

-- 插入预置配置
INSERT INTO system_config (config_key, config_value, description) VALUES
    ('sync.symbol.cron', '0 0 2 * * ?', '交易对同步Cron表达式 (默认: 每天凌晨2点 UTC)'),
    ('sync.realtime.enabled', 'true', '实时同步总开关'),
    ('sync.history.cron', '0 30 3 * * ?', '历史数据增量同步Cron表达式 (默认: 每天凌晨3:30 UTC)'),
    ('sync.history.auto', 'true', '历史数据自动增量同步开关'),
    ('sync.gap_detect.cron', '0 0 * * * ?', '缺口检测Cron表达式 (默认: 每小时整点)'),
    ('sync.gap_fill.auto', 'false', '自动缺口回补开关'),
    ('sync.gap_fill.max_retry', '3', '回补最大重试次数'),
    ('sync.gap_fill.batch_size', '10', '单次回补批量大小'),
    ('sync.gap_fill.interval_ms', '1000', '回补任务间隔毫秒数 (防止API限流)')
ON CONFLICT (config_key) DO NOTHING;
