package com.chanlun.controller;

import com.chanlun.dto.ApiResponse;
import com.chanlun.dto.ConfigUpdateRequest;
import com.chanlun.dto.SystemConfigDTO;
import com.chanlun.entity.SystemConfig;
import com.chanlun.service.SystemConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 系统配置管理控制器
 * 
 * 提供系统配置的查询和更新 REST API
 * 
 * API 路径: /api/v1/config
 * 
 * @author Chanlun Team
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/config")
@RequiredArgsConstructor
public class ConfigController {

    private final SystemConfigService systemConfigService;

    /**
     * 获取所有配置
     * 
     * GET /api/v1/config
     * 
     * @return 配置列表
     */
    @GetMapping
    public ApiResponse<List<SystemConfigDTO>> getAll() {
        List<SystemConfig> configs = systemConfigService.getAll();
        List<SystemConfigDTO> dtos = configs.stream()
                .map(SystemConfigDTO::fromEntity)
                .collect(Collectors.toList());
        return ApiResponse.success(dtos);
    }

    /**
     * 获取单个配置
     * 
     * GET /api/v1/config/{key}
     * 
     * @param key 配置键
     * @return 配置详情
     */
    @GetMapping("/{key}")
    public ApiResponse<SystemConfigDTO> getByKey(@PathVariable String key) {
        SystemConfig config = systemConfigService.getByKey(key);
        return ApiResponse.success(SystemConfigDTO.fromEntity(config));
    }

    /**
     * 更新配置
     * 
     * PUT /api/v1/config/{key}
     * 
     * @param key 配置键
     * @param request 更新请求
     * @return 更新结果
     */
    @PutMapping("/{key}")
    public ApiResponse<SystemConfigDTO> update(
            @PathVariable String key,
            @Valid @RequestBody ConfigUpdateRequest request) {
        
        // 先验证配置键是否存在
        systemConfigService.getByKey(key);
        
        // 更新配置值
        boolean updated = systemConfigService.updateValue(key, request.getValue());
        
        if (updated) {
            log.info("配置更新成功: key={}, value={}", key, request.getValue());
            SystemConfig config = systemConfigService.getByKey(key);
            return ApiResponse.success("配置更新成功", SystemConfigDTO.fromEntity(config));
        } else {
            return ApiResponse.error(500, "配置更新失败");
        }
    }
}
