package com.chanlun.controller;

import com.chanlun.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查控制器
 * 
 * @author Chanlun Team
 */
@RestController
@RequestMapping("/api/v1")
public class HealthController {

    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> data = new HashMap<>();
        data.put("status", "UP");
        data.put("timestamp", Instant.now().toString());
        return ApiResponse.success(data);
    }
}
