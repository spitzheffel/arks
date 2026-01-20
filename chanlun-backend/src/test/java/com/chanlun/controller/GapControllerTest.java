package com.chanlun.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chanlun.dto.DataGapDTO;
import com.chanlun.dto.GapDetectRequest;
import com.chanlun.dto.GapDetectResult;
import com.chanlun.exception.BusinessException;
import com.chanlun.exception.GlobalExceptionHandler;
import com.chanlun.exception.ResourceNotFoundException;
import com.chanlun.service.DataGapService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GapController 测试")
class GapControllerTest {

    @Mock
    private DataGapService dataGapService;

    @InjectMocks
    private GapController gapController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(gapController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
    }

    private DataGapDTO createGapDTO(Long id, Long symbolId, String interval) {
        return DataGapDTO.builder()
                .id(id)
                .symbolId(symbolId)
                .symbol("BTCUSDT")
                .interval(interval)
                .gapStart(Instant.parse("2025-01-01T00:00:00Z"))
                .gapEnd(Instant.parse("2025-01-01T02:00:00Z"))
                .missingCount(2)
                .status("PENDING")
                .retryCount(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("GET /api/v1/gaps - 获取缺口列表")
    class GetGapListTest {

        @Test
        @DisplayName("无参数查询返回所有缺口")
        void listAll_returnsGaps() throws Exception {
            DataGapDTO gap = createGapDTO(1L, 1L, "1h");
            IPage<DataGapDTO> page = new Page<>(1, 20);
            page.setRecords(List.of(gap));
            page.setTotal(1);

            when(dataGapService.list(1, 20, null, null, null)).thenReturn(page);

            mockMvc.perform(get("/api/v1/gaps"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.records").isArray())
                    .andExpect(jsonPath("$.data.records[0].id").value(1))
                    .andExpect(jsonPath("$.data.total").value(1));
        }

        @Test
        @DisplayName("按交易对ID筛选")
        void listBySymbolId_returnsFiltered() throws Exception {
            DataGapDTO gap = createGapDTO(1L, 1L, "1h");
            IPage<DataGapDTO> page = new Page<>(1, 20);
            page.setRecords(List.of(gap));
            page.setTotal(1);

            when(dataGapService.list(1, 20, 1L, null, null)).thenReturn(page);

            mockMvc.perform(get("/api/v1/gaps").param("symbolId", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.records[0].symbolId").value(1));
        }

        @Test
        @DisplayName("按周期筛选")
        void listByInterval_returnsFiltered() throws Exception {
            DataGapDTO gap = createGapDTO(1L, 1L, "1h");
            IPage<DataGapDTO> page = new Page<>(1, 20);
            page.setRecords(List.of(gap));
            page.setTotal(1);

            when(dataGapService.list(1, 20, null, "1h", null)).thenReturn(page);

            mockMvc.perform(get("/api/v1/gaps").param("interval", "1h"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.records[0].interval").value("1h"));
        }

        @Test
        @DisplayName("按状态筛选")
        void listByStatus_returnsFiltered() throws Exception {
            DataGapDTO gap = createGapDTO(1L, 1L, "1h");
            IPage<DataGapDTO> page = new Page<>(1, 20);
            page.setRecords(List.of(gap));
            page.setTotal(1);

            when(dataGapService.list(1, 20, null, null, "PENDING")).thenReturn(page);

            mockMvc.perform(get("/api/v1/gaps").param("status", "PENDING"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.records[0].status").value("PENDING"));
        }

        @Test
        @DisplayName("分页参数正确传递")
        void listWithPagination_returnsCorrectPage() throws Exception {
            IPage<DataGapDTO> page = new Page<>(2, 10);
            page.setRecords(Collections.emptyList());
            page.setTotal(15);

            when(dataGapService.list(2, 10, null, null, null)).thenReturn(page);

            mockMvc.perform(get("/api/v1/gaps")
                            .param("page", "2")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.page").value(2))
                    .andExpect(jsonPath("$.data.size").value(10));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/gaps/{id} - 获取缺口详情")
    class GetGapTest {

        @Test
        @DisplayName("存在的缺口返回详情")
        void getExisting_returnsGap() throws Exception {
            DataGapDTO gap = createGapDTO(1L, 1L, "1h");
            when(dataGapService.getById(1L)).thenReturn(gap);

            mockMvc.perform(get("/api/v1/gaps/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.symbol").value("BTCUSDT"));
        }

        @Test
        @DisplayName("不存在的缺口返回404")
        void getNotExisting_returns404() throws Exception {
            when(dataGapService.getById(999L)).thenThrow(new ResourceNotFoundException("缺口不存在: 999"));

            mockMvc.perform(get("/api/v1/gaps/999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(404));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/gaps/detect - 检测数据缺口")
    class DetectGapsTest {

        @Test
        @DisplayName("单交易对检测成功")
        void detectSingle_success() throws Exception {
            GapDetectRequest request = GapDetectRequest.builder()
                    .symbolId(1L)
                    .interval("1h")
                    .build();

            GapDetectResult result = GapDetectResult.success(
                    "缺口检测完成", 1, 1, 2, 2, Collections.emptyList());

            when(dataGapService.detectGaps(1L, "1h")).thenReturn(result);

            mockMvc.perform(post("/api/v1/gaps/detect")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.success").value(true))
                    .andExpect(jsonPath("$.data.newGapCount").value(2));
        }

        @Test
        @DisplayName("批量检测成功")
        void detectAll_success() throws Exception {
            GapDetectRequest request = GapDetectRequest.builder()
                    .detectAll(true)
                    .build();

            GapDetectResult result = GapDetectResult.success(
                    "批量缺口检测完成", 10, 30, 5, 15, Collections.emptyList());

            when(dataGapService.detectAllGaps()).thenReturn(result);

            mockMvc.perform(post("/api/v1/gaps/detect")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.success").value(true))
                    .andExpect(jsonPath("$.data.symbolCount").value(10))
                    .andExpect(jsonPath("$.data.intervalCount").value(30));
        }

        @Test
        @DisplayName("缺少symbolId返回错误")
        void detectWithoutSymbolId_returnsBadRequest() throws Exception {
            GapDetectRequest request = GapDetectRequest.builder()
                    .interval("1h")
                    .build();

            mockMvc.perform(post("/api/v1/gaps/detect")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("缺少interval返回错误")
        void detectWithoutInterval_returnsBadRequest() throws Exception {
            GapDetectRequest request = GapDetectRequest.builder()
                    .symbolId(1L)
                    .build();

            mockMvc.perform(post("/api/v1/gaps/detect")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("交易对不符合条件返回失败结果")
        void detectIneligibleSymbol_returnsFailure() throws Exception {
            GapDetectRequest request = GapDetectRequest.builder()
                    .symbolId(1L)
                    .interval("1h")
                    .build();

            GapDetectResult result = GapDetectResult.failure("交易对不符合缺口检测条件");

            when(dataGapService.detectGaps(1L, "1h")).thenReturn(result);

            mockMvc.perform(post("/api/v1/gaps/detect")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.success").value(false))
                    .andExpect(jsonPath("$.data.message").value("交易对不符合缺口检测条件"));
        }

        @Test
        @DisplayName("无效周期返回错误")
        void detectInvalidInterval_returnsBadRequest() throws Exception {
            GapDetectRequest request = GapDetectRequest.builder()
                    .symbolId(1L)
                    .interval("1s")
                    .build();

            when(dataGapService.detectGaps(1L, "1s"))
                    .thenThrow(new BusinessException("不支持的时间周期: 1s"));

            mockMvc.perform(post("/api/v1/gaps/detect")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }
    }
}
