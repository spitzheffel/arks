package com.chanlun.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chanlun.dto.AutoGapFillRequest;
import com.chanlun.dto.HistorySyncRequest;
import com.chanlun.entity.SyncStatus;
import com.chanlun.entity.SyncTask;
import com.chanlun.exception.ResourceNotFoundException;
import com.chanlun.service.HistorySyncService;
import com.chanlun.service.SyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * SyncController 测试
 * 
 * @author Chanlun Team
 */
@WebMvcTest(SyncController.class)
@DisplayName("SyncController 测试")
class SyncControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SyncService syncService;

    @MockBean
    private HistorySyncService historySyncService;

    private ObjectMapper objectMapper;
    private Instant baseTime;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        baseTime = Instant.parse("2025-01-01T00:00:00Z");
    }

    // ==================== 同步任务 API 测试 ====================

    @Test
    @DisplayName("获取同步任务列表 - 成功")
    void getTaskList_success() throws Exception {
        SyncTask task = SyncTask.builder()
                .id(1L)
                .symbolId(100L)
                .interval("1h")
                .taskType(SyncTask.TaskType.HISTORY)
                .status(SyncTask.Status.SUCCESS)
                .syncedCount(100)
                .createdAt(baseTime)
                .build();
        
        IPage<SyncTask> page = new Page<>(1, 20);
        page.setRecords(List.of(task));
        page.setTotal(1);
        
        when(syncService.getTaskList(isNull(), isNull(), isNull(), eq(1), eq(20))).thenReturn(page);

        mockMvc.perform(get("/api/v1/sync/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.records[0].id").value(1))
                .andExpect(jsonPath("$.data.records[0].symbolId").value(100))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    @DisplayName("获取同步任务列表 - 带筛选条件")
    void getTaskList_withFilters() throws Exception {
        IPage<SyncTask> page = new Page<>(1, 20);
        page.setRecords(List.of());
        page.setTotal(0);
        
        when(syncService.getTaskList(eq(100L), eq("HISTORY"), eq("SUCCESS"), eq(1), eq(20))).thenReturn(page);

        mockMvc.perform(get("/api/v1/sync/tasks")
                        .param("symbolId", "100")
                        .param("taskType", "HISTORY")
                        .param("status", "SUCCESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("获取同步任务详情 - 成功")
    void getTask_success() throws Exception {
        SyncTask task = SyncTask.builder()
                .id(1L)
                .symbolId(100L)
                .interval("1h")
                .taskType(SyncTask.TaskType.HISTORY)
                .status(SyncTask.Status.SUCCESS)
                .build();
        
        when(syncService.getTaskById(1L)).thenReturn(task);

        mockMvc.perform(get("/api/v1/sync/tasks/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.symbolId").value(100));
    }

    @Test
    @DisplayName("获取同步任务详情 - 不存在")
    void getTask_notFound() throws Exception {
        when(syncService.getTaskById(999L)).thenReturn(null);

        mockMvc.perform(get("/api/v1/sync/tasks/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("同步任务不存在"));
    }

    @Test
    @DisplayName("触发历史同步 - 成功")
    void triggerHistorySync_success() throws Exception {
        HistorySyncRequest request = HistorySyncRequest.builder()
                .interval("1h")
                .startTime(baseTime)
                .endTime(baseTime.plus(24, ChronoUnit.HOURS))
                .build();
        
        SyncTask task = SyncTask.builder()
                .id(1L)
                .symbolId(100L)
                .interval("1h")
                .taskType(SyncTask.TaskType.HISTORY)
                .status(SyncTask.Status.SUCCESS)
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .syncedCount(100)
                .build();
        
        // Mock historySyncService.syncHistory 返回同步数量
        when(historySyncService.syncHistory(eq(100L), eq("1h"), any(), any())).thenReturn(100);
        
        // Mock 获取最新任务
        IPage<SyncTask> taskPage = new Page<>(1, 1);
        taskPage.setRecords(List.of(task));
        when(syncService.getTaskList(eq(100L), eq(SyncTask.TaskType.HISTORY), isNull(), eq(1), eq(1))).thenReturn(taskPage);

        mockMvc.perform(post("/api/v1/sync/symbols/100/history")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.syncedCount").value(100));
    }

    @Test
    @DisplayName("触发历史同步 - 缺少必填字段")
    void triggerHistorySync_missingFields() throws Exception {
        HistorySyncRequest request = HistorySyncRequest.builder()
                .interval("1h")
                // 缺少 startTime 和 endTime
                .build();

        mockMvc.perform(post("/api/v1/sync/symbols/100/history")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ==================== 同步状态 API 测试 ====================

    @Test
    @DisplayName("获取同步状态列表 - 成功")
    void getSyncStatusList_success() throws Exception {
        SyncStatus status = SyncStatus.builder()
                .id(1L)
                .symbolId(100L)
                .interval("1h")
                .totalKlines(1000L)
                .autoGapFillEnabled(true)
                .createdAt(baseTime)
                .build();
        
        IPage<SyncStatus> page = new Page<>(1, 20);
        page.setRecords(List.of(status));
        page.setTotal(1);
        
        when(syncService.getSyncStatusList(isNull(), eq(1), eq(20))).thenReturn(page);

        mockMvc.perform(get("/api/v1/sync/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.records[0].id").value(1))
                .andExpect(jsonPath("$.data.records[0].totalKlines").value(1000));
    }

    @Test
    @DisplayName("获取同步状态详情 - 成功")
    void getSyncStatus_success() throws Exception {
        SyncStatus status = SyncStatus.builder()
                .id(1L)
                .symbolId(100L)
                .interval("1h")
                .totalKlines(1000L)
                .autoGapFillEnabled(true)
                .build();
        
        when(syncService.getSyncStatusById(1L)).thenReturn(status);

        mockMvc.perform(get("/api/v1/sync/status/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.autoGapFillEnabled").value(true));
    }

    @Test
    @DisplayName("获取同步状态详情 - 不存在")
    void getSyncStatus_notFound() throws Exception {
        when(syncService.getSyncStatusById(999L)).thenReturn(null);

        mockMvc.perform(get("/api/v1/sync/status/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("同步状态不存在"));
    }

    @Test
    @DisplayName("获取交易对的所有同步状态 - 成功")
    void getSyncStatusBySymbol_success() throws Exception {
        List<SyncStatus> statuses = List.of(
                SyncStatus.builder().id(1L).symbolId(100L).interval("1h").totalKlines(1000L).build(),
                SyncStatus.builder().id(2L).symbolId(100L).interval("4h").totalKlines(500L).build()
        );
        
        when(syncService.getSyncStatusBySymbolId(100L)).thenReturn(statuses);

        mockMvc.perform(get("/api/v1/sync/symbols/100/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @DisplayName("更新自动回补开关 - 成功")
    void updateAutoGapFill_success() throws Exception {
        AutoGapFillRequest request = AutoGapFillRequest.builder()
                .enabled(false)
                .build();
        
        SyncStatus status = SyncStatus.builder()
                .id(1L)
                .symbolId(100L)
                .interval("1h")
                .autoGapFillEnabled(false)
                .build();
        
        when(syncService.updateAutoGapFillEnabledById(1L, false)).thenReturn(true);
        when(syncService.getSyncStatusById(1L)).thenReturn(status);

        mockMvc.perform(patch("/api/v1/sync/status/1/auto-gap-fill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.autoGapFillEnabled").value(false));
    }

    @Test
    @DisplayName("更新自动回补开关 - 缺少必填字段")
    void updateAutoGapFill_missingEnabled() throws Exception {
        mockMvc.perform(patch("/api/v1/sync/status/1/auto-gap-fill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
