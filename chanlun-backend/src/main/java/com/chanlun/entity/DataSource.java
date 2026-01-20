package com.chanlun.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 数据源实体
 * 
 * 存储交易所 API 连接配置信息
 * API Key 和 Secret Key 使用 AES-256 加密存储
 * 
 * @author Chanlun Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("data_source")
public class DataSource {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 数据源名称
     */
    private String name;

    /**
     * 交易所类型 (BINANCE, OKX等)
     */
    private String exchangeType;

    /**
     * API Key (AES-256加密存储)
     */
    private String apiKey;

    /**
     * Secret Key (AES-256加密存储)
     */
    private String secretKey;

    /**
     * REST API 基础 URL
     */
    private String baseUrl;

    /**
     * WebSocket URL
     */
    private String wsUrl;

    /**
     * 是否启用代理
     */
    private Boolean proxyEnabled;

    /**
     * 代理类型 (HTTP/SOCKS5)
     */
    private String proxyType;

    /**
     * 代理地址
     */
    private String proxyHost;

    /**
     * 代理端口
     */
    private Integer proxyPort;

    /**
     * 代理用户名
     */
    private String proxyUsername;

    /**
     * 代理密码 (AES-256加密存储)
     */
    private String proxyPassword;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 软删除标记
     */
    @TableLogic
    private Boolean deleted;

    /**
     * 删除时间 (UTC)
     */
    private Instant deletedAt;

    /**
     * 创建时间 (UTC)
     */
    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;

    /**
     * 更新时间 (UTC)
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
}
