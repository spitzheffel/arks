package com.chanlun.exchange.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 币安服务器时间响应
 * 
 * @author Chanlun Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BinanceServerTime {

    /**
     * 服务器时间（毫秒时间戳）
     */
    private long serverTime;
}
