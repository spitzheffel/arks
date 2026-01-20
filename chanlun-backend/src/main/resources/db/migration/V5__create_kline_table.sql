-- =====================================================
-- Flyway 迁移脚本
-- 版本: V5
-- 描述: 创建K线数据表 (kline)
-- 时区: 所有 TIMESTAMPTZ 字段存储 UTC 时间
-- =====================================================

-- 设置时区为 UTC
SET TIME ZONE 'UTC';

-- 创建K线数据表
CREATE TABLE IF NOT EXISTS kline (
    id BIGSERIAL PRIMARY KEY,
    symbol_id BIGINT NOT NULL,
    interval VARCHAR(10) NOT NULL,
    open_time TIMESTAMPTZ NOT NULL,
    open DECIMAL(24,8) NOT NULL,
    high DECIMAL(24,8) NOT NULL,
    low DECIMAL(24,8) NOT NULL,
    close DECIMAL(24,8) NOT NULL,
    volume DECIMAL(24,8) NOT NULL DEFAULT 0,
    quote_volume DECIMAL(24,8) NOT NULL DEFAULT 0,
    trades INTEGER NOT NULL DEFAULT 0,
    close_time TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_kline_symbol FOREIGN KEY (symbol_id) 
        REFERENCES symbol(id) ON DELETE CASCADE
);

-- 创建索引
CREATE INDEX idx_kline_symbol_id ON kline(symbol_id);
CREATE INDEX idx_kline_interval ON kline(interval);
CREATE INDEX idx_kline_open_time ON kline(open_time);

-- 创建唯一约束：同一交易对、同一周期、同一开盘时间的K线唯一
CREATE UNIQUE INDEX uk_kline_symbol_interval_time ON kline(symbol_id, interval, open_time);

-- 添加表注释
COMMENT ON TABLE kline IS 'K线数据表，存储各交易对各周期的OHLCV数据';
COMMENT ON COLUMN kline.id IS '主键';
COMMENT ON COLUMN kline.symbol_id IS '交易对ID (外键)';
COMMENT ON COLUMN kline.interval IS '时间周期 (1m/3m/5m/15m/30m/1h/2h/4h/6h/8h/12h/1d/3d/1w/1M)';
COMMENT ON COLUMN kline.open_time IS '开盘时间 (UTC)';
COMMENT ON COLUMN kline.open IS '开盘价';
COMMENT ON COLUMN kline.high IS '最高价';
COMMENT ON COLUMN kline.low IS '最低价';
COMMENT ON COLUMN kline.close IS '收盘价';
COMMENT ON COLUMN kline.volume IS '成交量';
COMMENT ON COLUMN kline.quote_volume IS '成交额';
COMMENT ON COLUMN kline.trades IS '成交笔数';
COMMENT ON COLUMN kline.close_time IS '收盘时间 (UTC)';
COMMENT ON COLUMN kline.created_at IS '创建时间 (UTC)';
