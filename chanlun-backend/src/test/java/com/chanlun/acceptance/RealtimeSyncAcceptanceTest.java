package com.chanlun.acceptance;

import com.chanlun.config.ProxyConfig;
import com.chanlun.entity.*;
import com.chanlun.exchange.BinanceClient;
import com.chanlun.exchange.BinanceClientFactory;
import com.chanlun.exchange.BinanceWebSocketManager;
import com.chanlun.service.*;
import com.chanlun.util.EncryptUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 实时同步验收测试 (任务 25.2, 25.6, 25.15, 25.17)
 * 
 * 验证实时数据同步相关功能：
 * - 25.2 实时数据同步 (WebSocket)
 * - 25.6 sync.realtime.enabled 全局开关
 * - 25.15 关闭实时同步时断开 WebSocket
 * - 25.17 实时同步更新 sync_status
 * 
 * @author Chanlun Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("实时同步验收测试")
class RealtimeSyncAcceptanceTest {

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
        realtimeSyncService = new RealtimeSyncService(
                symbolService, marketService, dataSourceService,
                klineService, syncService, systemConfigService,
                binanceClientFactory, encryptUtil);
        
        // 设置 mockEnabled = true 以便测试
        ReflectionTestUtils.setField(realtimeSyncService, "mockEnabled", true);
        
        // 初始化服务
        realtimeSyncService.init();

        testDataSource = DataSource.builder()
                .id(1L)
                .name("Binance")
                .exchangeType("BINANCE")
                .enabled(true)
                .deleted(false)
                .proxyEnabled(false)
                .build();

        testMarket = Market.builder()
                .id(1L)
                .dataSourceId(1L)
                .name("现货")
                .marketType("SPOT")
                .enabled(true)
                .build();

        testSymbol = Symbol.builder()
                .id(1L)
                .marketId(1L)
                .symbol("BTCUSDT")
                .baseAsset("BTC")
                .quoteAsset("USDT")
                .realtimeSyncEnabled(true)
                .historySyncEnabled(true)
                .syncIntervals("1m,5m,1h")
                .status("TRADING")
                .build();
    }

    @AfterEach
    void tearDown() {
        if (realtimeSyncService != null) {
            realtimeSyncService.destroy();
        }
    }

    // ==================== 25.2 验证实时数据同步 (WebSocket) ====================

    @Nested
    @DisplayName("25.2 验证实时数据同步 (WebSocket)")
    class WebSocketSyncTests {

        @Test
        @DisplayName("启动实时同步 - 成功订阅多个周期")
        void startRealtimeSync_success() {
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
        @DisplayName("启动实时同步 - 交易对未启用实时同步")
        void startRealtimeSync_symbolNotEnabled() {
            testSymbol.setRealtimeSyncEnabled(false);
            
            when(systemConfigService.isRealtimeSyncEnabled()).thenReturn(true);
            when(symbolService.findById(testSymbol.getId())).thenReturn(testSymbol);

            int count = realtimeSyncService.startRealtimeSync(testSymbol.getId());

            assertEquals(0, count);
        }

        @Test
        @DisplayName("启动实时同步 - 市场未启用")
        void startRealtimeSync_marketDisabled() {
            testMarket.setEnabled(false);
            
            when(systemConfigService.isRealtimeSyncEnabled()).thenReturn(true);
            when(symbolService.findById(testSymbol.getId())).thenReturn(testSymbol);
            when(marketService.findById(testMarket.getId())).thenReturn(testMarket);

            int count = realtimeSyncService.startRealtimeSync(testSymbol.getId());

            assertEquals(0, count);
        }

        @Test
        @DisplayName("启动实时同步 - 数据源未启用")
        void startRealtimeSync_dataSourceDisabled() {
            testDataSource.setEnabled(false);
            
            when(systemConfigService.isRealtimeSyncEnabled()).thenReturn(true);
            when(symbolService.findById(testSymbol.getId())).thenReturn(testSymbol);
            when(marketService.findById(testMarket.getId())).thenReturn(testMarket);
            when(dataSourceService.findById(testDataSource.getId())).thenReturn(testDataSource);

            int count = realtimeSyncService.startRealtimeSync(testSymbol.getId());

            assertEquals(0, count);
        }

        @Test
        @DisplayName("停止实时同步 - 成功取消订阅")
        void stopRealtimeSync_success() {
            when(systemConfigService.isRealtimeSyncEnabled()).thenReturn(true);
            when(symbolService.findById(testSymbol.getId())).thenReturn(testSymbol);
            when(marketService.findById(testMarket.getId())).thenReturn(testMarket);
            when(dataSourceService.findById(testDataSource.getId())).thenReturn(testDataSource);

            // 先启动
            realtimeSyncService.startRealtimeSync(testSymbol.getId());
            assertEquals(3, realtimeSyncService.getSubscriptionCount());

            // 再停止
            int stopped = realtimeSyncService.stopRealtimeSync(testSymbol.getId());

            assertEquals(3, stopped);
            assertEquals(0, realtimeSyncService.getSubscriptionCount());
        }
    }

    // ==================== 25.6 验证 sync.realtime.enabled 全局开关 ====================

    @Nested
    @DisplayName("25.6 验证 sync.realtime.enabled 全局开关")
    class GlobalSwitchTests {

        @Test
        @DisplayName("全局开关关闭 - 不启动任何订阅")
        void globalSwitchDisabled_noSubscriptions() {
            when(systemConfigService.isRealtimeSyncEnabled()).thenReturn(false);

            int count = realtimeSyncService.startRealtimeSync(testSymbol.getId());

            assertEquals(0, count);
            assertEquals(0, realtimeSyncService.getSubscriptionCount());
        }

        @Test
        @DisplayName("全局开关关闭 - startAllRealtimeSync 不启动任何订阅")
        void globalSwitchDisabled_startAllNoSubscriptions() {
            when(systemConfigService.isRealtimeSyncEnabled()).thenReturn(false);

            int count = realtimeSyncService.startAllRealtimeSync();

            assertEquals(0, count);
        }

        @Test
        @DisplayName("全局开关启用 - 正常启动订阅")
        void globalSwitchEnabled_normalSubscriptions() {
            when(systemConfigService.isRealtimeSyncEnabled()).thenReturn(true);
            when(symbolService.findById(testSymbol.getId())).thenReturn(testSymbol);
            when(marketService.findById(testMarket.getId())).thenReturn(testMarket);
            when(dataSourceService.findById(testDataSource.getId())).thenReturn(testDataSource);

            int count = realtimeSyncService.startRealtimeSync(testSymbol.getId());

            assertEquals(3, count);
        }
    }

    // ==================== 25.15 验证关闭实时同步时断开 WebSocket ====================

    @Nested
    @DisplayName("25.15 验证关闭实时同步时断开 WebSocket")
    class DisconnectOnDisableTests {

        @Test
        @DisplayName("全局开关变为 false 时断开所有 WebSocket")
        void onRealtimeSyncDisabled_disconnectAll() {
            when(systemConfigService.isRealtimeSyncEnabled()).thenReturn(true);
            when(symbolService.findById(testSymbol.getId())).thenReturn(testSymbol);
            when(marketService.findById(testMarket.getId())).thenReturn(testMarket);
            when(dataSourceService.findById(testDataSource.getId())).thenReturn(testDataSource);

            // 先启动订阅
            realtimeSyncService.startRealtimeSync(testSymbol.getId());
            assertEquals(3, realtimeSyncService.getSubscriptionCount());

            // 模拟全局开关变为 false
            realtimeSyncService.onRealtimeSyncEnabledChanged(false);

            // 验证所有订阅已断开
            assertEquals(0, realtimeSyncService.getSubscriptionCount());
        }

        @Test
        @DisplayName("stopAllRealtimeSync - 断开所有订阅")
        void stopAllRealtimeSync_disconnectAll() {
            when(systemConfigService.isRealtimeSyncEnabled()).thenReturn(true);
            when(symbolService.findById(testSymbol.getId())).thenReturn(testSymbol);
            when(marketService.findById(testMarket.getId())).thenReturn(testMarket);
            when(dataSourceService.findById(testDataSource.getId())).thenReturn(testDataSource);

            // 先启动订阅
            realtimeSyncService.startRealtimeSync(testSymbol.getId());
            assertEquals(3, realtimeSyncService.getSubscriptionCount());

            // 停止所有
            int stopped = realtimeSyncService.stopAllRealtimeSync();

            assertEquals(3, stopped);
            assertEquals(0, realtimeSyncService.getSubscriptionCount());
        }
    }

    // ==================== 25.17 验证实时同步更新 sync_status ====================

    @Nested
    @DisplayName("25.17 验证实时同步更新 sync_status")
    class SyncStatusUpdateTests {

        @Test
        @DisplayName("实时同步后更新 sync_status")
        void updateSyncStatusAfterRealtimeSync() {
            Instant lastKlineTime = Instant.now();
            int syncedCount = 1;

            realtimeSyncService.updateSyncStatusAfterRealtimeSync(1L, "1h", lastKlineTime, syncedCount);

            verify(syncService).updateSyncStatus(eq(1L), eq("1h"), eq(lastKlineTime), eq((long) syncedCount));
        }
    }

    // ==================== 辅助测试 ====================

    @Nested
    @DisplayName("辅助功能测试")
    class HelperTests {

        @Test
        @DisplayName("获取订阅信息")
        void getAllSubscriptions() {
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
        @DisplayName("检查是否已订阅")
        void isSubscribed() {
            when(systemConfigService.isRealtimeSyncEnabled()).thenReturn(true);
            when(symbolService.findById(testSymbol.getId())).thenReturn(testSymbol);
            when(marketService.findById(testMarket.getId())).thenReturn(testMarket);
            when(dataSourceService.findById(testDataSource.getId())).thenReturn(testDataSource);

            realtimeSyncService.startRealtimeSync(testSymbol.getId());

            assertTrue(realtimeSyncService.isSubscribed(1L, 1L, "1h"));
            assertFalse(realtimeSyncService.isSubscribed(1L, 1L, "4h")); // 未配置的周期
        }
    }
}
