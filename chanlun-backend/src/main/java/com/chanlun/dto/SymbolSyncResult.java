package com.chanlun.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 交易对同步结果 DTO
 * 
 * @author Chanlun Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SymbolSyncResult {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 消息
     */
    private String message;

    /**
     * 同步的交易对总数
     */
    private int syncedCount;

    /**
     * 新创建的交易对数量
     */
    private int createdCount;

    /**
     * 已存在的交易对数量
     */
    private int existingCount;

    /**
     * 更新的交易对数量
     */
    private int updatedCount;

    /**
     * 同步的交易对列表
     */
    private List<SymbolDTO> symbols;
}
