-- =====================================================
-- Flyway 迁移脚本
-- 版本: V7
-- 描述: 创建同步任务表 (sync_task)
-- 时区: 所有 TIMESTAMPTZ 字段存储 UTC 时间
-- =====================================================

-- 设置时区为 UTC
SET TIME ZONE 'UTC';

-- 创建同步任务表
CREATE TABLE IF NOT EXISTS sync_task (
    id BIGSERIAL PRIMARY KEY,
    symbol_id BIGINT NOT NULL,
    interval VARCHAR(10) NOT NULL,
    task_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    start_time TIMESTAMPTZ,
    end_time TIMESTAMPTZ,
    synced_count INTEGER NOT NULL DEFAULT 0,
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retries INTEGER NOT NULL DEFAULT 3,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_sync_task_symbol FOREIGN KEY (symbol_id) 
        REFERENCES symbol(id) ON DELETE CASCADE,
    CONSTRAINT chk_sync_task_type CHECK (task_type IN ('REALTIME', 'HISTORY', 'GAP_FILL')),
    CONSTRAINT chk_sync_task_status CHECK (status IN ('PENDING', 'RUNNING', 'SUCCESS', 'FAILED'))
);

-- 创建索引
CREATE INDEX idx_sync_task_symbol_id ON sync_task(symbol_id);
CREATE INDEX idx_sync_task_interval ON sync_task(interval);
CREATE INDEX idx_sync_task_type ON sync_task(task_type);
CREATE INDEX idx_sync_task_status ON sync_task(status);
CREATE INDEX idx_sync_task_created_at ON sync_task(created_at);

-- 添加表注释
COMMENT ON TABLE sync_task IS '同步任务表，记录实时/历史/缺口回补任务执行记录';
COMMENT ON COLUMN sync_task.id IS '主键';
COMMENT ON COLUMN sync_task.symbol_id IS '交易对ID (外键)';
COMMENT ON COLUMN sync_task.interval IS '时间周期';
COMMENT ON COLUMN sync_task.task_type IS '任务类型 (REALTIME/HISTORY/GAP_FILL)';
COMMENT ON COLUMN sync_task.status IS '状态 (PENDING/RUNNING/SUCCESS/FAILED)';
COMMENT ON COLUMN sync_task.start_time IS '同步起始时间 (UTC)';
COMMENT ON COLUMN sync_task.end_time IS '同步结束时间 (UTC)';
COMMENT ON COLUMN sync_task.synced_count IS '已同步数量';
COMMENT ON COLUMN sync_task.retry_count IS '重试次数';
COMMENT ON COLUMN sync_task.max_retries IS '最大重试次数 (默认3)';
COMMENT ON COLUMN sync_task.error_message IS '错误信息';
COMMENT ON COLUMN sync_task.created_at IS '创建时间 (UTC)';
COMMENT ON COLUMN sync_task.updated_at IS '更新时间 (UTC)';
