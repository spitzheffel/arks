package com.chanlun.enums;

import lombok.Getter;

/**
 * 交易所类型枚举
 * 
 * @author Chanlun Team
 */
@Getter
public enum ExchangeType {

    BINANCE("BINANCE", "币安"),
    OKX("OKX", "欧易");

    private final String code;
    private final String description;

    ExchangeType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据 code 获取枚举
     */
    public static ExchangeType fromCode(String code) {
        for (ExchangeType type : values()) {
            if (type.getCode().equalsIgnoreCase(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown exchange type: " + code);
    }
}
