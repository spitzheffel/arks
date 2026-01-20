-- =====================================================
-- Flyway 迁移脚本
-- 版本: V6
-- 描述: 创建数据缺口表 (data_gap)
-- 时区: 所有 TIMESTAMPTZ 字段存储 UTC 时间
-- =====================================================

-- 设置时区为 UTC
SET TIME ZONE 'UTC';

-- 创建数据缺口表
CREATE TABLE IF NOT EXISTS data_gap (
    id BIGSERIAL PRIMARY KEY,
    symbol_id BIGINT NOT NULL,
    interval VARCHAR(10) NOT NULL,
    gap_start TIMESTAMPTZ NOT NULL,
    gap_end TIMESTAMPTZ NOT NULL,
    missing_count INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_data_gap_symbol FOREIGN KEY (symbol_id) 
        REFERENCES symbol(id) ON DELETE CASCADE,
    CONSTRAINT chk_data_gap_status CHECK (status IN ('PENDING', 'FILLING', 'FILLED', 'FAILED'))
);

-- 创建索引
CREATE INDEX idx_data_gap_symbol_id ON data_gap(symbol_id);
CREATE INDEX idx_data_gap_interval ON data_gap(interval);
CREATE INDEX idx_data_gap_status ON data_gap(status);
CREATE INDEX idx_data_gap_symbol_interval_status ON data_gap(symbol_id, interval, status);

-- 添加表注释
COMMENT ON TABLE data_gap IS '数据缺口表，记录K线数据中检测到的缺口信息';
COMMENT ON COLUMN data_gap.id IS '主键';
COMMENT ON COLUMN data_gap.symbol_id IS '交易对ID (外键)';
COMMENT ON COLUMN data_gap.interval IS '时间周期';
COMMENT ON COLUMN data_gap.gap_start IS '缺口开始时间 (UTC)';
COMMENT ON COLUMN data_gap.gap_end IS '缺口结束时间 (UTC)';
COMMENT ON COLUMN data_gap.missing_count IS '缺失K线数量';
COMMENT ON COLUMN data_gap.status IS '状态 (PENDING/FILLING/FILLED/FAILED)';
COMMENT ON COLUMN data_gap.retry_count IS '重试次数';
COMMENT ON COLUMN data_gap.error_message IS '错误信息';
COMMENT ON COLUMN data_gap.created_at IS '创建时间 (UTC)';
COMMENT ON COLUMN data_gap.updated_at IS '更新时间 (UTC)';
