package com.chanlun.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.chanlun.dto.*;
import com.chanlun.entity.DataSource;
import com.chanlun.exchange.BinanceClient;
import com.chanlun.exchange.BinanceClientFactory;
import com.chanlun.service.DataSourceService;
import com.chanlun.service.MarketService;
import com.chanlun.service.ProxyTestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 数据源管理控制器
 * 
 * 提供数据源的 CRUD REST API
 * 
 * API 路径: /api/v1/datasources
 * 
 * @author Chanlun Team
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/datasources")
@RequiredArgsConstructor
public class DataSourceController {

    private final DataSourceService dataSourceService;
    private final ProxyTestService proxyTestService;
    private final BinanceClientFactory binanceClientFactory;
    private final MarketService marketService;

    /**
     * 获取数据源列表（分页）
     * 
     * GET /api/v1/datasources
     * 
     * @param page 页码（从1开始）
     * @param size 每页数量（默认20，最大100）
     * @param exchangeType 交易所类型筛选
     * @param enabled 启用状态筛选
     */
    @GetMapping
    public ApiResponse<IPage<DataSourceDTO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String exchangeType,
            @RequestParam(required = false) Boolean enabled) {
        
        // 限制每页最大数量
        if (size > 100) {
            size = 100;
        }
        
        IPage<DataSourceDTO> result = dataSourceService.list(page, size, exchangeType, enabled);
        return ApiResponse.success(result);
    }

    /**
     * 获取所有数据源（不分页）
     * 
     * GET /api/v1/datasources/all
     */
    @GetMapping("/all")
    public ApiResponse<List<DataSourceDTO>> listAll(
            @RequestParam(required = false) String exchangeType,
            @RequestParam(required = false) Boolean enabled) {
        
        List<DataSourceDTO> result = dataSourceService.listAll(exchangeType, enabled);
        return ApiResponse.success(result);
    }

    /**
     * 获取数据源详情
     * 
     * GET /api/v1/datasources/{id}
     */
    @GetMapping("/{id}")
    public ApiResponse<DataSourceDTO> getById(@PathVariable Long id) {
        DataSourceDTO dataSource = dataSourceService.getById(id);
        return ApiResponse.success(dataSource);
    }

    /**
     * 创建数据源
     * 
     * POST /api/v1/datasources
     */
    @PostMapping
    public ApiResponse<DataSourceDTO> create(@Valid @RequestBody DataSourceCreateRequest request) {
        DataSourceDTO dataSource = dataSourceService.create(request);
        return ApiResponse.success("数据源创建成功", dataSource);
    }

    /**
     * 更新数据源
     * 
     * PUT /api/v1/datasources/{id}
     */
    @PutMapping("/{id}")
    public ApiResponse<DataSourceDTO> update(
            @PathVariable Long id,
            @Valid @RequestBody DataSourceUpdateRequest request) {
        
        DataSourceDTO dataSource = dataSourceService.update(id, request);
        return ApiResponse.success("数据源更新成功", dataSource);
    }

    /**
     * 删除数据源（软删除）
     * 
     * DELETE /api/v1/datasources/{id}
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        dataSourceService.delete(id);
        return ApiResponse.success("数据源删除成功", null);
    }

    /**
     * 启用/禁用数据源
     * 
     * PATCH /api/v1/datasources/{id}/status
     */
    @PatchMapping("/{id}/status")
    public ApiResponse<DataSourceDTO> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody StatusUpdateRequest request) {
        
        DataSourceDTO dataSource = dataSourceService.updateStatus(id, request.getEnabled());
        String message = request.getEnabled() ? "数据源已启用" : "数据源已禁用";
        return ApiResponse.success(message, dataSource);
    }

    /**
     * 测试数据源连接
     * 
     * POST /api/v1/datasources/{id}/test
     * 
     * 通过调用交易所 API (ping + serverTime) 测试连接是否正常
     */
    @PostMapping("/{id}/test")
    public ApiResponse<ConnectionTestResult> testConnection(@PathVariable Long id) {
        // 获取数据源实体
        DataSource dataSource = dataSourceService.findById(id);
        
        // 创建币安客户端
        BinanceClient client = binanceClientFactory.createClient(dataSource);
        
        try {
            // 执行连接测试
            BinanceClient.ConnectionTestResult result = client.testConnection();
            
            // 转换为 DTO
            ConnectionTestResult dto = ConnectionTestResult.builder()
                    .success(result.isSuccess())
                    .message(result.getMessage())
                    .latencyMs(result.getLatencyMs())
                    .serverTime(result.getServerTime())
                    .timeDiffMs(result.getTimeDiffMs())
                    .build();
            
            if (result.isSuccess()) {
                return ApiResponse.success("连接测试成功", dto);
            } else {
                return ApiResponse.success(result.getMessage(), dto);
            }
        } finally {
            client.close();
        }
    }

    /**
     * 测试代理连接
     * 
     * POST /api/v1/datasources/{id}/test-proxy
     * 
     * 测试数据源配置的代理是否可用
     * 通过访问外部测试 URL 验证代理连通性
     */
    @PostMapping("/{id}/test-proxy")
    public ApiResponse<ProxyTestResult> testProxy(@PathVariable Long id) {
        ProxyTestResult result = proxyTestService.testProxy(id);
        if (result.isSuccess()) {
            return ApiResponse.success("代理连接测试成功", result);
        } else {
            return ApiResponse.success(result.getMessage(), result);
        }
    }

    /**
     * 同步市场信息
     * 
     * POST /api/v1/datasources/{id}/sync-markets
     * 
     * 从币安同步市场信息，根据数据源的 baseUrl 判断市场类型
     * 并创建对应的市场记录
     */
    @PostMapping("/{id}/sync-markets")
    public ApiResponse<MarketSyncResult> syncMarkets(@PathVariable Long id) {
        MarketSyncResult result = marketService.syncMarketsFromBinance(id);
        if (result.isSuccess()) {
            return ApiResponse.success("市场同步成功", result);
        } else {
            return ApiResponse.success(result.getMessage(), result);
        }
    }
}
