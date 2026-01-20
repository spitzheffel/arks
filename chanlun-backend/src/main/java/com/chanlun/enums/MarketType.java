package com.chanlun.enums;

import lombok.Getter;

/**
 * 市场类型枚举
 * 
 * @author Chanlun Team
 */
@Getter
public enum MarketType {

    SPOT("SPOT", "现货"),
    USDT_M("USDT_M", "U本位合约"),
    COIN_M("COIN_M", "币本位合约");

    private final String code;
    private final String description;

    MarketType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据 code 获取枚举
     */
    public static MarketType fromCode(String code) {
        for (MarketType type : values()) {
            if (type.getCode().equalsIgnoreCase(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown market type: " + code);
    }
}
