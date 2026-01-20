-- =====================================================
-- Flyway 迁移脚本
-- 版本: V4
-- 描述: 创建交易对表 (symbol)
-- 时区: 所有 TIMESTAMPTZ 字段存储 UTC 时间
-- =====================================================

-- 设置时区为 UTC
SET TIME ZONE 'UTC';

-- 创建交易对表
CREATE TABLE IF NOT EXISTS symbol (
    id BIGSERIAL PRIMARY KEY,
    market_id BIGINT NOT NULL,
    symbol VARCHAR(30) NOT NULL,
    base_asset VARCHAR(20) NOT NULL,
    quote_asset VARCHAR(20) NOT NULL,
    price_precision INTEGER NOT NULL DEFAULT 8,
    quantity_precision INTEGER NOT NULL DEFAULT 8,
    realtime_sync_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    history_sync_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    sync_intervals VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'TRADING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_symbol_market FOREIGN KEY (market_id) 
        REFERENCES market(id) ON DELETE CASCADE
);

-- 创建索引
CREATE INDEX idx_symbol_market_id ON symbol(market_id);
CREATE INDEX idx_symbol_status ON symbol(status);
CREATE INDEX idx_symbol_realtime_sync ON symbol(realtime_sync_enabled);
CREATE INDEX idx_symbol_history_sync ON symbol(history_sync_enabled);

-- 创建唯一约束：同一市场下交易对代码唯一
CREATE UNIQUE INDEX uk_symbol_market_symbol ON symbol(market_id, symbol);

-- 添加表注释
COMMENT ON TABLE symbol IS '交易对表，存储交易对基本信息和同步配置';
COMMENT ON COLUMN symbol.id IS '主键';
COMMENT ON COLUMN symbol.market_id IS '市场ID (外键)';
COMMENT ON COLUMN symbol.symbol IS '交易对代码 (如 BTCUSDT)';
COMMENT ON COLUMN symbol.base_asset IS '基础货币 (如 BTC)';
COMMENT ON COLUMN symbol.quote_asset IS '报价货币 (如 USDT)';
COMMENT ON COLUMN symbol.price_precision IS '价格精度';
COMMENT ON COLUMN symbol.quantity_precision IS '数量精度';
COMMENT ON COLUMN symbol.realtime_sync_enabled IS '实时同步开关 (默认关闭)';
COMMENT ON COLUMN symbol.history_sync_enabled IS '历史同步开关 (默认关闭)';
COMMENT ON COLUMN symbol.sync_intervals IS '同步周期 (逗号分隔: 1m,5m,1h)';
COMMENT ON COLUMN symbol.status IS '交易对状态 (TRADING/HALT)';
COMMENT ON COLUMN symbol.created_at IS '创建时间 (UTC)';
COMMENT ON COLUMN symbol.updated_at IS '更新时间 (UTC)';
