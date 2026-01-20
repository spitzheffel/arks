-- =====================================================
-- Flyway 迁移脚本
-- 版本: V2
-- 描述: 创建数据源表 (data_source)
-- 时区: 所有 TIMESTAMPTZ 字段存储 UTC 时间
-- =====================================================

-- 设置时区为 UTC
SET TIME ZONE 'UTC';

-- 创建数据源表
CREATE TABLE IF NOT EXISTS data_source (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    exchange_type VARCHAR(20) NOT NULL,
    api_key VARCHAR(255),
    secret_key VARCHAR(500),
    base_url VARCHAR(255),
    ws_url VARCHAR(255),
    proxy_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    proxy_type VARCHAR(10),
    proxy_host VARCHAR(100),
    proxy_port INTEGER,
    proxy_username VARCHAR(100),
    proxy_password VARCHAR(255),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 创建索引
CREATE INDEX idx_data_source_exchange_type ON data_source(exchange_type);
CREATE INDEX idx_data_source_enabled ON data_source(enabled);
CREATE INDEX idx_data_source_deleted ON data_source(deleted);

-- 添加表注释
COMMENT ON TABLE data_source IS '数据源配置表，存储交易所API连接信息';
COMMENT ON COLUMN data_source.id IS '主键';
COMMENT ON COLUMN data_source.name IS '数据源名称';
COMMENT ON COLUMN data_source.exchange_type IS '交易所类型 (BINANCE, OKX等)';
COMMENT ON COLUMN data_source.api_key IS 'API Key (AES-256加密存储)';
COMMENT ON COLUMN data_source.secret_key IS 'Secret Key (AES-256加密存储)';
COMMENT ON COLUMN data_source.base_url IS 'REST API基础URL';
COMMENT ON COLUMN data_source.ws_url IS 'WebSocket URL';
COMMENT ON COLUMN data_source.proxy_enabled IS '是否启用代理';
COMMENT ON COLUMN data_source.proxy_type IS '代理类型 (HTTP/SOCKS5)';
COMMENT ON COLUMN data_source.proxy_host IS '代理地址';
COMMENT ON COLUMN data_source.proxy_port IS '代理端口';
COMMENT ON COLUMN data_source.proxy_username IS '代理用户名';
COMMENT ON COLUMN data_source.proxy_password IS '代理密码 (AES-256加密存储)';
COMMENT ON COLUMN data_source.enabled IS '是否启用';
COMMENT ON COLUMN data_source.deleted IS '软删除标记';
COMMENT ON COLUMN data_source.deleted_at IS '删除时间 (UTC)';
COMMENT ON COLUMN data_source.created_at IS '创建时间 (UTC)';
COMMENT ON COLUMN data_source.updated_at IS '更新时间 (UTC)';
