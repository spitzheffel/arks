package com.chanlun.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 交易对同步周期配置请求 DTO
 * 
 * @author Chanlun Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SymbolIntervalsRequest {

    @NotNull(message = "同步周期列表不能为空")
    private List<String> intervals;
}
