package com.chanlun.controller;

import com.chanlun.dto.ConfigUpdateRequest;
import com.chanlun.entity.SystemConfig;
import com.chanlun.exception.ResourceNotFoundException;
import com.chanlun.service.SystemConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 系统配置控制器测试
 * 
 * @author Chanlun Team
 */
@WebMvcTest(ConfigController.class)
@DisplayName("ConfigController 测试")
class ConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SystemConfigService systemConfigService;

    private SystemConfig createTestConfig(String key, String value, String description) {
        return SystemConfig.builder()
                .id(1L)
                .configKey(key)
                .configValue(value)
                .description(description)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/config - 获取所有配置成功")
    void getAll_success() throws Exception {
        List<SystemConfig> configs = Arrays.asList(
                createTestConfig("sync.history.auto", "true", "历史数据自动同步开关"),
                createTestConfig("sync.realtime.enabled", "true", "实时同步总开关")
        );

        when(systemConfigService.getAll()).thenReturn(configs);

        mockMvc.perform(get("/api/v1/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].configKey").value("sync.history.auto"))
                .andExpect(jsonPath("$.data[0].configValue").value("true"))
                .andExpect(jsonPath("$.data[1].configKey").value("sync.realtime.enabled"));
    }

    @Test
    @DisplayName("GET /api/v1/config/{key} - 获取单个配置成功")
    void getByKey_success() throws Exception {
        SystemConfig config = createTestConfig("sync.history.auto", "true", "历史数据自动同步开关");

        when(systemConfigService.getByKey("sync.history.auto")).thenReturn(config);

        mockMvc.perform(get("/api/v1/config/sync.history.auto"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.configKey").value("sync.history.auto"))
                .andExpect(jsonPath("$.data.configValue").value("true"))
                .andExpect(jsonPath("$.data.description").value("历史数据自动同步开关"));
    }

    @Test
    @DisplayName("GET /api/v1/config/{key} - 配置不存在返回404")
    void getByKey_notFound() throws Exception {
        when(systemConfigService.getByKey("not.exists"))
                .thenThrow(new ResourceNotFoundException("配置不存在: not.exists"));

        mockMvc.perform(get("/api/v1/config/not.exists"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("PUT /api/v1/config/{key} - 更新配置成功")
    void update_success() throws Exception {
        ConfigUpdateRequest request = ConfigUpdateRequest.builder()
                .value("false")
                .build();

        SystemConfig existingConfig = createTestConfig("sync.history.auto", "true", "历史数据自动同步开关");
        SystemConfig updatedConfig = createTestConfig("sync.history.auto", "false", "历史数据自动同步开关");

        when(systemConfigService.getByKey("sync.history.auto"))
                .thenReturn(existingConfig)
                .thenReturn(updatedConfig);
        when(systemConfigService.updateValue("sync.history.auto", "false")).thenReturn(true);

        mockMvc.perform(put("/api/v1/config/sync.history.auto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("配置更新成功"))
                .andExpect(jsonPath("$.data.configKey").value("sync.history.auto"))
                .andExpect(jsonPath("$.data.configValue").value("false"));
    }

    @Test
    @DisplayName("PUT /api/v1/config/{key} - 配置不存在返回404")
    void update_notFound() throws Exception {
        ConfigUpdateRequest request = ConfigUpdateRequest.builder()
                .value("false")
                .build();

        when(systemConfigService.getByKey("not.exists"))
                .thenThrow(new ResourceNotFoundException("配置不存在: not.exists"));

        mockMvc.perform(put("/api/v1/config/not.exists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("PUT /api/v1/config/{key} - 缺少配置值返回400")
    void update_missingValue() throws Exception {
        ConfigUpdateRequest request = ConfigUpdateRequest.builder()
                .value("")
                .build();

        mockMvc.perform(put("/api/v1/config/sync.history.auto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("PUT /api/v1/config/{key} - 更新失败返回500")
    void update_failed() throws Exception {
        ConfigUpdateRequest request = ConfigUpdateRequest.builder()
                .value("false")
                .build();

        SystemConfig existingConfig = createTestConfig("sync.history.auto", "true", "历史数据自动同步开关");

        when(systemConfigService.getByKey("sync.history.auto")).thenReturn(existingConfig);
        when(systemConfigService.updateValue("sync.history.auto", "false")).thenReturn(false);

        mockMvc.perform(put("/api/v1/config/sync.history.auto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("配置更新失败"));
    }
}
