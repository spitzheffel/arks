package com.chanlun.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * K线删除请求参数
 * 
 * 用于手动删除历史数据接口
 * 
 * @author Chanlun Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KlineDeleteRequest {

    /**
     * 时间周期 (必填)
     * 支持: 1m, 3m, 5m, 15m, 30m, 1h, 2h, 4h, 6h, 8h, 12h, 1d, 3d, 1w, 1M
     */
    @NotBlank(message = "时间周期不能为空")
    private String interval;

    /**
     * 开始时间 (必填, ISO 8601 格式)
     */
    @NotNull(message = "开始时间不能为空")
    private Instant startTime;

    /**
     * 结束时间 (必填, ISO 8601 格式)
     */
    @NotNull(message = "结束时间不能为空")
    private Instant endTime;
}
