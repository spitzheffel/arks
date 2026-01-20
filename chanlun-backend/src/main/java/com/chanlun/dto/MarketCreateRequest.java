package com.chanlun.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 市场创建请求 DTO
 * 
 * @author Chanlun Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketCreateRequest {

    @NotNull(message = "数据源ID不能为空")
    private Long dataSourceId;

    @NotBlank(message = "市场名称不能为空")
    private String name;

    @NotBlank(message = "市场类型不能为空")
    private String marketType;
}
