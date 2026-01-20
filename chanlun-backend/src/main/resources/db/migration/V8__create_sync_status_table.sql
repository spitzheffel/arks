-- =====================================================
-- Flyway 迁移脚本
-- 版本: V8
-- 描述: 创建同步状态表 (sync_status)
-- 时区: 所有 TIMESTAMPTZ 字段存储 UTC 时间
-- =====================================================

-- 设置时区为 UTC
SET TIME ZONE 'UTC';

-- 创建同步状态表
CREATE TABLE IF NOT EXISTS sync_status (
    id BIGSERIAL PRIMARY KEY,
    symbol_id BIGINT NOT NULL,
    interval VARCHAR(10) NOT NULL,
    last_sync_time TIMESTAMPTZ,
    last_kline_time TIMESTAMPTZ,
    total_klines BIGINT NOT NULL DEFAULT 0,
    auto_gap_fill_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_sync_status_symbol FOREIGN KEY (symbol_id) 
        REFERENCES symbol(id) ON DELETE CASCADE
);

-- 创建索引
CREATE INDEX idx_sync_status_symbol_id ON sync_status(symbol_id);
CREATE INDEX idx_sync_status_interval ON sync_status(interval);

-- 创建唯一约束：同一交易对同一周期只有一条状态记录
CREATE UNIQUE INDEX uk_sync_status_symbol_interval ON sync_status(symbol_id, interval);

-- 添加表注释
COMMENT ON TABLE sync_status IS '同步状态表，记录每个交易对每个周期的同步状态';
COMMENT ON COLUMN sync_status.id IS '主键';
COMMENT ON COLUMN sync_status.symbol_id IS '交易对ID (外键)';
COMMENT ON COLUMN sync_status.interval IS '时间周期';
COMMENT ON COLUMN sync_status.last_sync_time IS '最后同步时间 (UTC)';
COMMENT ON COLUMN sync_status.last_kline_time IS '最后一根K线的开盘时间 (UTC)，无数据时为 NULL';
COMMENT ON COLUMN sync_status.total_klines IS '总K线数量';
COMMENT ON COLUMN sync_status.auto_gap_fill_enabled IS '该周期自动回补开关 (默认 true)';
COMMENT ON COLUMN sync_status.created_at IS '创建时间 (UTC)';
COMMENT ON COLUMN sync_status.updated_at IS '更新时间 (UTC)';
