package com.chanlun.dto;

import com.chanlun.entity.SystemConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 系统配置 DTO
 * 
 * @author Chanlun Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemConfigDTO {

    /**
     * 主键
     */
    private Long id;

    /**
     * 配置键
     */
    private String configKey;

    /**
     * 配置值
     */
    private String configValue;

    /**
     * 配置说明
     */
    private String description;

    /**
     * 创建时间 (UTC)
     */
    private Instant createdAt;

    /**
     * 更新时间 (UTC)
     */
    private Instant updatedAt;

    /**
     * 从实体转换为 DTO
     */
    public static SystemConfigDTO fromEntity(SystemConfig entity) {
        if (entity == null) {
            return null;
        }
        return SystemConfigDTO.builder()
                .id(entity.getId())
                .configKey(entity.getConfigKey())
                .configValue(entity.getConfigValue())
                .description(entity.getDescription())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
