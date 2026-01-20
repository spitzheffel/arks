package com.chanlun.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 市场同步结果 DTO
 * 
 * @author Chanlun Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketSyncResult {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 消息
     */
    private String message;

    /**
     * 同步的市场数量
     */
    private int syncedCount;

    /**
     * 新创建的市场数量
     */
    private int createdCount;

    /**
     * 已存在的市场数量
     */
    private int existingCount;

    /**
     * 同步的市场列表
     */
    private List<MarketDTO> markets;
}
