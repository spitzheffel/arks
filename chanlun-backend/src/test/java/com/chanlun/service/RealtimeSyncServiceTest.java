package com.chanlun.service;

import com.chanlun.entity.*;
import com.chanlun.exchange.BinanceClientFactory;
import com.chanlun.exchange.BinanceWebSocketManager;
import com.chanlun.util.EncryptUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RealtimeSyncService 单元测试
 * 
 * @author Chanlun Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RealtimeSyncService 测试")
class RealtimeSyncServiceTest {

    @Mock
    private SymbolService symbolService;

    @Mock
    private MarketService marketService;

    @Mock
    private DataSourceService dataSourceService;

    @Mock
    private KlineService klineService;

    @Mock
    private SyncService syncService;

    @Mock
    private SystemConfigService systemConfigService;

    @Mock
    private BinanceClientFactory binanceClientFactory;

    @Mock
    private EncryptUtil encryptUtil;

    private RealtimeSyncService realtimeSyncService;

    private DataSource testDataSource;
    private Market testMarket;
    private Symbol testSymbol;

    @BeforeEach
    void setUp() {
        // 手动创建服务实例
        realtimeSyncService = new RealtimeSyncService(
                symbolService, marketService, dataSourceService,
                klineService, syncService, systemConfigService,
                binanceClientFactory, encryptUtil
        );
        
        // 设置 mockEnabled
        ReflectionTestUtils.setField(realtimeSyncService, "mockEnabled", true);
        
        // 初始化
        realtimeSyncService.init();

        // 创建测试数据
        testDataSource = DataSource.builder()
                .id(1L)
                .name("Test Binance")
                .exchangeType("BINANCE")
                .baseUrl("https://api.binance.com")
                .wsUrl("wss://stream.binance.com:9443/ws")
                .enabled(true)
                .proxyEnabled(false)
                .build();

        testMarket = Market.builder()
                .id(10L)
                .dataSourceId(1L)
                .name("现货")
                .marketType("SPOT")
                .enabled(true)
                .build();

        testSymbol = Symbol.builder()
                .id(100L)
                .marketId(10L)
                .symbol("BTCUSDT")
                .baseAsset("BTC")
                .quoteAsset("USDT")
                .realtimeSyncEnabled(true)
                .historySyncEnabled(false)
                .syncIntervals("1m,5m,1h")
                .status("TRADING")
                .build();
    }

    @Test
    @DisplayName("测试启动实时同步 - 全局开关关闭")
    void testStartRealtimeSync_GlobalDisabled() {
        when(systemConfigService.isRealtimeSyncEnabled()).thenReturn(false);

        int count = realtimeSyncService.startRealtimeSync(testSymbol.getId());

        assertEquals(0, count);
        verify(symbolService, never()).findById(anyLong());
    }

    @Test
    @DisplayName("测试启动实时同步 - 交易对未启用")
    void testStartRealtimeSync_SymbolNotEnabled() {
        testSymbol.setRealtimeSyncEnabled(false);
        
        when(systemConfigService.isRealtimeSyncEnabled()).thenReturn(true);
        when(symbolService.findById(testSymbol.getId())).thenReturn(testSymbol);

        int count = realtimeSyncService.startRealtimeSync(testSymbol.getId());

        assertEquals(0, count);
    }

    @Test
    @DisplayName("测试启动实时同步 - 市场未启用")
    void testStartRealtimeSync_MarketDisabled() {
        testMarket.setEnabled(false);
        
        when(systemConfigService.isRealtimeSyncEnabled()).thenReturn(true);
        when(symbolService.findById(testSymbol.getId())).thenReturn(testSymbol);
        when(marketService.findById(testMarket.getId())).thenReturn(testMarket);

        int count = realtimeSyncService.startRealtimeSync(testSymbol.getId());

        assertEquals(0, count);
    }

    @Test
    @DisplayName("测试启动实时同步 - 数据源未启用")
    void testStartRealtimeSync_DataSourceDisabled() {
        testDataSource.setEnabled(false);
        
        when(systemConfigService.isRealtimeSyncEnabled()).thenReturn(true);
        when(symbolService.findById(testSymbol.getId())).thenReturn(testSymbol);
        when(marketService.findById(testMarket.getId())).thenReturn(testMarket);
        when(dataSourceService.findById(testDataSource.getId())).thenReturn(testDataSource);

        int count = realtimeSyncService.startRealtimeSync(testSymbol.getId());

        assertEquals(0, count);
    }

    @Test
    @DisplayName("测试启动实时同步 - 无同步周期")
    void testStartRealtimeSync_NoIntervals() {
        testSymbol.setSyncIntervals(null);
        
        when(systemConfigService.isRealtimeSyncEnabled()).thenReturn(true);
        when(symbolService.findById(testSymbol.getId())).thenReturn(testSymbol);
        when(marketService.findById(testMarket.getId())).thenReturn(testMarket);
        when(dataSourceService.findById(testDataSource.getId())).thenReturn(testDataSource);

        int count = realtimeSyncService.startRealtimeSync(testSymbol.getId());

        assertEquals(0, count);
    }

    @Test
    @DisplayName("测试启动实时同步 - 成功")
    void testStartRealtimeSync_Success() {
        when(systemConfigService.isRealtimeSyncEnabled()).thenReturn(true);
        when(symbolService.findById(testSymbol.getId())).thenReturn(testSymbol);
        when(marketService.findById(testMarket.getId())).thenReturn(testMarket);
        when(dataSourceService.findById(testDataSource.getId())).thenReturn(testDataSource);

        int count = realtimeSyncService.startRealtimeSync(testSymbol.getId());

        // 应该订阅 3 个周期 (1m, 5m, 1h)
        assertEquals(3, count);
        assertEquals(3, realtimeSyncService.getSubscriptionCount());
    }

    @Test
    @DisplayName("测试停止实时同步")
    void testStopRealtimeSync() {
        // 先启动
        when(systemConfigService.isRealtimeSyncEnabled()).thenReturn(true);
        when(symbolService.findById(testSymbol.getId())).thenReturn(testSymbol);
        when(marketService.findById(testMarket.getId())).thenReturn(testMarket);
        when(dataSourceService.findById(testDataSource.getId())).thenReturn(testDataSource);
        
        realtimeSyncService.startRealtimeSync(testSymbol.getId());
        assertEquals(3, realtimeSyncService.getSubscriptionCount());

        // 停止
        int count = realtimeSyncService.stopRealtimeSync(testSymbol.getId());

        assertEquals(3, count);
        assertEquals(0, realtimeSyncService.getSubscriptionCount());
    }

    @Test
    @DisplayName("测试停止所有实时同步")
    void testStopAllRealtimeSync() {
        // 先启动
        when(systemConfigService.isRealtimeSyncEnabled()).thenReturn(true);
        when(symbolService.findById(testSymbol.getId())).thenReturn(testSymbol);
        when(marketService.findById(testMarket.getId())).thenReturn(testMarket);
        when(dataSourceService.findById(testDataSource.getId())).thenReturn(testDataSource);
        
        realtimeSyncService.startRealtimeSync(testSymbol.getId());

        // 停止所有
        int count = realtimeSyncService.stopAllRealtimeSync();

        assertEquals(3, count);
        assertEquals(0, realtimeSyncService.getSubscriptionCount());
    }

    @Test
    @DisplayName("测试启动所有实时同步 - 全局开关关闭")
    void testStartAllRealtimeSync_GlobalDisabled() {
        when(systemConfigService.isRealtimeSyncEnabled()).thenReturn(false);

        int count = realtimeSyncService.startAllRealtimeSync();

        assertEquals(0, count);
        verify(symbolService, never()).getRealtimeSyncEnabledSymbols();
    }

    @Test
    @DisplayName("测试启动所有实时同步 - 成功")
    void testStartAllRealtimeSync_Success() {
        when(systemConfigService.isRealtimeSyncEnabled()).thenReturn(true);
        when(symbolService.getRealtimeSyncEnabledSymbols()).thenReturn(List.of(testSymbol));
        when(symbolService.findById(testSymbol.getId())).thenReturn(testSymbol);
        when(marketService.findById(testMarket.getId())).thenReturn(testMarket);
        when(dataSourceService.findById(testDataSource.getId())).thenReturn(testDataSource);

        int count = realtimeSyncService.startAllRealtimeSync();

        assertEquals(3, count);
    }

    @Test
    @DisplayName("测试全局开关变化 - 关闭")
    void testOnRealtimeSyncEnabledChanged_Disabled() {
        // 先启动一些订阅
        when(systemConfigService.isRealtimeSyncEnabled()).thenReturn(true);
        when(symbolService.findById(testSymbol.getId())).thenReturn(testSymbol);
        when(marketService.findById(testMarket.getId())).thenReturn(testMarket);
        when(dataSourceService.findById(testDataSource.getId())).thenReturn(testDataSource);
        
        realtimeSyncService.startRealtimeSync(testSymbol.getId());
        assertEquals(3, realtimeSyncService.getSubscriptionCount());

        // 触发开关变化
        realtimeSyncService.onRealtimeSyncEnabledChanged(false);

        assertEquals(0, realtimeSyncService.getSubscriptionCount());
    }

    @Test
    @DisplayName("测试检查是否已订阅")
    void testIsSubscribed() {
        when(systemConfigService.isRealtimeSyncEnabled()).thenReturn(true);
        when(symbolService.findById(testSymbol.getId())).thenReturn(testSymbol);
        when(marketService.findById(testMarket.getId())).thenReturn(testMarket);
        when(dataSourceService.findById(testDataSource.getId())).thenReturn(testDataSource);
        
        realtimeSyncService.startRealtimeSync(testSymbol.getId());

        assertTrue(realtimeSyncService.isSubscribed(testDataSource.getId(), testSymbol.getId(), "1m"));
        assertTrue(realtimeSyncService.isSubscribed(testDataSource.getId(), testSymbol.getId(), "5m"));
        assertTrue(realtimeSyncService.isSubscribed(testDataSource.getId(), testSymbol.getId(), "1h"));
        assertFalse(realtimeSyncService.isSubscribed(testDataSource.getId(), testSymbol.getId(), "4h"));
    }

    @Test
    @DisplayName("测试获取订阅信息")
    void testGetAllSubscriptions() {
        when(systemConfigService.isRealtimeSyncEnabled()).thenReturn(true);
        when(symbolService.findById(testSymbol.getId())).thenReturn(testSymbol);
        when(marketService.findById(testMarket.getId())).thenReturn(testMarket);
        when(dataSourceService.findById(testDataSource.getId())).thenReturn(testDataSource);
        
        realtimeSyncService.startRealtimeSync(testSymbol.getId());

        List<BinanceWebSocketManager.SubscriptionInfo> subscriptions = 
                realtimeSyncService.getAllSubscriptions();

        assertEquals(3, subscriptions.size());
    }

    @Test
    @DisplayName("测试获取已连接数量")
    void testGetConnectedCount() {
        when(systemConfigService.isRealtimeSyncEnabled()).thenReturn(true);
        when(symbolService.findById(testSymbol.getId())).thenReturn(testSymbol);
        when(marketService.findById(testMarket.getId())).thenReturn(testMarket);
        when(dataSourceService.findById(testDataSource.getId())).thenReturn(testDataSource);
        
        realtimeSyncService.startRealtimeSync(testSymbol.getId());

        // Mock 模式下连接会立即成功
        int connectedCount = realtimeSyncService.getConnectedCount();

        assertEquals(3, connectedCount);
    }
}
