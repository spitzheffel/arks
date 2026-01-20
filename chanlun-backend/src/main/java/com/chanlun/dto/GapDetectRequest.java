package com.chanlun.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 缺口检测请求 DTO
 * 
 * @author Chanlun Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GapDetectRequest {

    /**
     * 交易对ID（单个检测时使用）
     */
    private Long symbolId;

    /**
     * 时间周期（单个检测时使用）
     */
    private String interval;

    /**
     * 是否批量检测所有符合条件的交易对
     */
    private Boolean detectAll;
}
