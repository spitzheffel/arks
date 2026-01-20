package com.chanlun.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 市场 DTO
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
public class MarketDTO {

    private Long id;
    private Long dataSourceId;
    private String dataSourceName;
    private String name;
    private String marketType;
    private Boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;
}
