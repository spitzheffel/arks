package com.chanlun.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * K线查询请求参数
 * 
 * @author Chanlun Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KlineQueryRequest {

    /**
     * 交易对ID (必填)
     */
    @NotNull(message = "交易对ID不能为空")
    private Long symbolId;

    /**
     * 时间周期 (必填)
     * 支持: 1m, 3m, 5m, 15m, 30m, 1h, 2h, 4h, 6h, 8h, 12h, 1d, 3d, 1w, 1M
     */
    @NotBlank(message = "时间周期不能为空")
    private String interval;

    /**
     * 开始时间 (可选, ISO 8601 格式)
     */
    private Instant startTime;

    /**
     * 结束时间 (可选, ISO 8601 格式)
     */
    private Instant endTime;

    /**
     * 返回数量限制 (默认500，最大1000)
     */
    @Min(value = 1, message = "返回数量最小为1")
    @Max(value = 1000, message = "返回数量最大为1000")
    private Integer limit;
}
