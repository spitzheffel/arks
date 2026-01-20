-- =====================================================
-- Flyway 迁移脚本
-- 版本: V3
-- 描述: 创建市场表 (market)
-- 时区: 所有 TIMESTAMPTZ 字段存储 UTC 时间
-- =====================================================

-- 设置时区为 UTC
SET TIME ZONE 'UTC';

-- 创建市场表
CREATE TABLE IF NOT EXISTS market (
    id BIGSERIAL PRIMARY KEY,
    data_source_id BIGINT NOT NULL,
    name VARCHAR(50) NOT NULL,
    market_type VARCHAR(20) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_market_data_source FOREIGN KEY (data_source_id) 
        REFERENCES data_source(id) ON DELETE CASCADE
);

-- 创建索引
CREATE INDEX idx_market_data_source_id ON market(data_source_id);
CREATE INDEX idx_market_market_type ON market(market_type);
CREATE INDEX idx_market_enabled ON market(enabled);

-- 创建唯一约束：同一数据源下市场类型唯一
CREATE UNIQUE INDEX uk_market_data_source_type ON market(data_source_id, market_type);

-- 添加表注释
COMMENT ON TABLE market IS '市场表，存储不同市场类型信息（现货、U本位合约、币本位合约）';
COMMENT ON COLUMN market.id IS '主键';
COMMENT ON COLUMN market.data_source_id IS '数据源ID (外键)';
COMMENT ON COLUMN market.name IS '市场名称';
COMMENT ON COLUMN market.market_type IS '市场类型 (SPOT/USDT_M/COIN_M)';
COMMENT ON COLUMN market.enabled IS '是否启用';
COMMENT ON COLUMN market.created_at IS '创建时间 (UTC)';
COMMENT ON COLUMN market.updated_at IS '更新时间 (UTC)';
