package com.chanlun.enums;

import lombok.Getter;

/**
 * 代理类型枚举
 * 
 * @author Chanlun Team
 */
@Getter
public enum ProxyType {

    HTTP("HTTP", "HTTP 代理"),
    SOCKS5("SOCKS5", "SOCKS5 代理");

    private final String code;
    private final String description;

    ProxyType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据 code 获取枚举
     */
    public static ProxyType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (ProxyType type : values()) {
            if (type.getCode().equalsIgnoreCase(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown proxy type: " + code);
    }
}
