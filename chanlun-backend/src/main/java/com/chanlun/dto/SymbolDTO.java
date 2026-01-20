package com.chanlun.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * 交易对 DTO
 * 
 * 用于 API 响应
 * 
 * @author Chanlun Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SymbolDTO {

    private Long id;
    private Long marketId;
    private String marketName;
    private String marketType;
    private Long dataSourceId;
    private String dataSourceName;
    private String symbol;
    private String baseAsset;
    private String quoteAsset;
    private Integer pricePrecision;
    private Integer quantityPrecision;
    private Boolean realtimeSyncEnabled;
    private Boolean historySyncEnabled;
    private List<String> syncIntervals;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
}
