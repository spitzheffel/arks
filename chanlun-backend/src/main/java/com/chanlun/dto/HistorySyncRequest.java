package com.chanlun.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 历史同步请求 DTO
 * 
 * @author Chanlun Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistorySyncRequest {

    /**
     * 时间周期 (1m/3m/5m/15m/30m/1h/2h/4h/6h/8h/12h/1d/3d/1w/1M)
     */
    @NotBlank(message = "时间周期不能为空")
    private String interval;

    /**
     * 同步起始时间 (UTC, ISO 8601 格式)
     */
    @NotNull(message = "开始时间不能为空")
    private Instant startTime;

    /**
     * 同步结束时间 (UTC, ISO 8601 格式)
     */
    @NotNull(message = "结束时间不能为空")
    private Instant endTime;
}
