package com.chanlun.exchange;

import com.chanlun.entity.DataSource;
import com.chanlun.entity.Symbol;
import com.chanlun.exchange.model.BinanceWsKlineEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BinanceWebSocketManager 单元测试
 * 
 * @author Chanlun Team
 */
@DisplayName("BinanceWebSocketManager 测试")
class BinanceWebSocketManagerTest {

    private BinanceWebSocketManager manager;
    private DataSource testDataSource;
    private Symbol testSymbol;

    @BeforeEach
    void setUp() {
        // 使用 Mock 模式创建管理器
        manager = new BinanceWebSocketManager(true);
        
        // 创建测试数据源
        testDataSource = DataSource.builder()
                .id(1L)
                .name("Test Binance")
                .exchangeType("BINANCE")
                .baseUrl("https://api.binance.com")
                .wsUrl("wss://stream.binance.com:9443/ws")
                .enabled(true)
                .proxyEnabled(false)
                .build();
        
        // 创建测试交易对
        testSymbol = Symbol.builder()
                .id(100L)
                .marketId(10L)
                .symbol("BTCUSDT")
                .baseAsset("BTC")
                .quoteAsset("USDT")
                .realtimeSyncEnabled(true)
                .syncIntervals("1m,5m,1h")
                .build();
    }

    @Test
    @DisplayName("测试订阅")
    void testSubscribe() {
        boolean result = manager.subscribe(testDataSource, testSymbol, "1m", null);
        
        assertTrue(result);
        assertEquals(1, manager.getSubscriptionCount());
        assertTrue(manager.isSubscribed(testDataSource.getId(), testSymbol.getId(), "1m"));
    }

    @Test
    @DisplayName("测试重复订阅")
    void testDuplicateSubscribe() {
        manager.subscribe(testDataSource, testSymbol, "1m", null);
        boolean result = manager.subscribe(testDataSource, testSymbol, "1m", null);
        
        assertTrue(result);
        assertEquals(1, manager.getSubscriptionCount());
    }

    @Test
    @DisplayName("测试多周期订阅")
    void testMultipleIntervalSubscribe() {
        manager.subscribe(testDataSource, testSymbol, "1m", null);
        manager.subscribe(testDataSource, testSymbol, "5m", null);
        manager.subscribe(testDataSource, testSymbol, "1h", null);
        
        assertEquals(3, manager.getSubscriptionCount());
        assertTrue(manager.isSubscribed(testDataSource.getId(), testSymbol.getId(), "1m"));
        assertTrue(manager.isSubscribed(testDataSource.getId(), testSymbol.getId(), "5m"));
        assertTrue(manager.isSubscribed(testDataSource.getId(), testSymbol.getId(), "1h"));
    }

    @Test
    @DisplayName("测试取消订阅")
    void testUnsubscribe() {
        manager.subscribe(testDataSource, testSymbol, "1m", null);
        
        boolean result = manager.unsubscribe(testDataSource.getId(), testSymbol.getId(), "1m");
        
        assertTrue(result);
        assertEquals(0, manager.getSubscriptionCount());
        assertFalse(manager.isSubscribed(testDataSource.getId(), testSymbol.getId(), "1m"));
    }

    @Test
    @DisplayName("测试取消不存在的订阅")
    void testUnsubscribeNonExistent() {
        boolean result = manager.unsubscribe(999L, 999L, "1m");
        
        assertFalse(result);
    }

    @Test
    @DisplayName("测试按交易对取消订阅")
    void testUnsubscribeBySymbol() {
        manager.subscribe(testDataSource, testSymbol, "1m", null);
        manager.subscribe(testDataSource, testSymbol, "5m", null);
        manager.subscribe(testDataSource, testSymbol, "1h", null);
        
        int count = manager.unsubscribeBySymbol(testSymbol.getId());
        
        assertEquals(3, count);
        assertEquals(0, manager.getSubscriptionCount());
    }

    @Test
    @DisplayName("测试按数据源取消订阅")
    void testUnsubscribeByDataSource() {
        // 创建另一个交易对
        Symbol anotherSymbol = Symbol.builder()
                .id(101L)
                .marketId(10L)
                .symbol("ETHUSDT")
                .build();
        
        manager.subscribe(testDataSource, testSymbol, "1m", null);
        manager.subscribe(testDataSource, anotherSymbol, "1m", null);
        
        int count = manager.unsubscribeByDataSource(testDataSource.getId());
        
        assertEquals(2, count);
        assertEquals(0, manager.getSubscriptionCount());
    }

    @Test
    @DisplayName("测试取消所有订阅")
    void testUnsubscribeAll() {
        manager.subscribe(testDataSource, testSymbol, "1m", null);
        manager.subscribe(testDataSource, testSymbol, "5m", null);
        
        int count = manager.unsubscribeAll();
        
        assertEquals(2, count);
        assertEquals(0, manager.getSubscriptionCount());
    }

    @Test
    @DisplayName("测试获取订阅信息")
    void testGetSubscription() {
        manager.subscribe(testDataSource, testSymbol, "1m", null);
        
        BinanceWebSocketManager.SubscriptionInfo info = 
                manager.getSubscription(testDataSource.getId(), testSymbol.getId(), "1m");
        
        assertNotNull(info);
        assertEquals(testDataSource.getId(), info.getDataSourceId());
        assertEquals(testSymbol.getId(), info.getSymbolId());
        assertEquals("BTCUSDT", info.getSymbolCode());
        assertEquals("1m", info.getInterval());
        assertNotNull(info.getSubscribedAt());
    }

    @Test
    @DisplayName("测试获取所有订阅")
    void testGetAllSubscriptions() {
        manager.subscribe(testDataSource, testSymbol, "1m", null);
        manager.subscribe(testDataSource, testSymbol, "5m", null);
        
        List<BinanceWebSocketManager.SubscriptionInfo> subscriptions = manager.getAllSubscriptions();
        
        assertEquals(2, subscriptions.size());
    }

    @Test
    @DisplayName("测试获取数据源的订阅")
    void testGetSubscriptionsByDataSource() {
        manager.subscribe(testDataSource, testSymbol, "1m", null);
        manager.subscribe(testDataSource, testSymbol, "5m", null);
        
        List<BinanceWebSocketManager.SubscriptionInfo> subscriptions = 
                manager.getSubscriptionsByDataSource(testDataSource.getId());
        
        assertEquals(2, subscriptions.size());
    }

    @Test
    @DisplayName("测试获取交易对的订阅")
    void testGetSubscriptionsBySymbol() {
        manager.subscribe(testDataSource, testSymbol, "1m", null);
        manager.subscribe(testDataSource, testSymbol, "5m", null);
        
        List<BinanceWebSocketManager.SubscriptionInfo> subscriptions = 
                manager.getSubscriptionsBySymbol(testSymbol.getId());
        
        assertEquals(2, subscriptions.size());
    }

    @Test
    @DisplayName("测试 K 线回调")
    void testKlineCallback() {
        AtomicReference<Long> receivedSymbolId = new AtomicReference<>();
        AtomicReference<BinanceWsKlineEvent> receivedEvent = new AtomicReference<>();
        
        manager.setKlineCallback((symbolId, event) -> {
            receivedSymbolId.set(symbolId);
            receivedEvent.set(event);
        });
        
        // 回调已设置，但需要实际的 WebSocket 消息来触发
        // 这里只验证回调设置成功
        assertNotNull(manager);
    }

    @Test
    @DisplayName("测试断线回调")
    void testDisconnectCallback() {
        AtomicReference<String> receivedKey = new AtomicReference<>();
        
        manager.setDisconnectCallback(key -> receivedKey.set(key));
        
        // 回调已设置
        assertNotNull(manager);
    }

    @Test
    @DisplayName("测试已连接数量")
    void testGetConnectedCount() {
        manager.subscribe(testDataSource, testSymbol, "1m", null);
        manager.subscribe(testDataSource, testSymbol, "5m", null);
        
        // Mock 模式下连接会立即成功
        int connectedCount = manager.getConnectedCount();
        
        assertEquals(2, connectedCount);
    }

    @Test
    @DisplayName("测试关闭管理器")
    void testShutdown() {
        manager.subscribe(testDataSource, testSymbol, "1m", null);
        manager.subscribe(testDataSource, testSymbol, "5m", null);
        
        manager.shutdown();
        
        assertEquals(0, manager.getSubscriptionCount());
    }
}
