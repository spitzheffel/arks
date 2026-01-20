package com.chanlun.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 自动缺口回补开关请求 DTO
 * 
 * @author Chanlun Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutoGapFillRequest {

    /**
     * 是否启用自动缺口回补
     */
    @NotNull(message = "enabled 不能为空")
    private Boolean enabled;
}
