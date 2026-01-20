package com.chanlun.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 缺口检测结果 DTO
 * 
 * @author Chanlun Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GapDetectResult {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 消息
     */
    private String message;

    /**
     * 检测的交易对数量
     */
    private int symbolCount;

    /**
     * 检测的周期数量
     */
    private int intervalCount;

    /**
     * 检测到的新缺口数量
     */
    private int newGapCount;

    /**
     * 总缺口数量（包括已存在的）
     */
    private int totalGapCount;

    /**
     * 检测到的缺口列表
     */
    private List<DataGapDTO> gaps;

    /**
     * 创建成功结果
     */
    public static GapDetectResult success(String message, int symbolCount, int intervalCount, 
                                          int newGapCount, int totalGapCount, List<DataGapDTO> gaps) {
        return GapDetectResult.builder()
                .success(true)
                .message(message)
                .symbolCount(symbolCount)
                .intervalCount(intervalCount)
                .newGapCount(newGapCount)
                .totalGapCount(totalGapCount)
                .gaps(gaps)
                .build();
    }

    /**
     * 创建失败结果
     */
    public static GapDetectResult failure(String message) {
        return GapDetectResult.builder()
                .success(false)
                .message(message)
                .symbolCount(0)
                .intervalCount(0)
                .newGapCount(0)
                .totalGapCount(0)
                .build();
    }
}
