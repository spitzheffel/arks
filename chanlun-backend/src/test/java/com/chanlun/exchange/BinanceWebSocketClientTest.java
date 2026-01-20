package com.chanlun.exchange;

import com.chanlun.exchange.model.BinanceWsKlineEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BinanceWebSocketClient 单元测试
 * 
 * @author Chanlun Team
 */
@DisplayName("BinanceWebSocketClient 测试")
class BinanceWebSocketClientTest {

    private BinanceWebSocketClient client;

    @BeforeEach
    void setUp() {
        // 使用 Mock 模式创建客户端
        client = new BinanceWebSocketClient(
                BinanceWebSocketClient.DEFAULT_SPOT_WS_URL,
                "BTCUSDT",
                "1m",
                null,
                event -> {},
                error -> {},
                () -> {},
                true  // Mock 模式
        );
    }

    @Test
    @DisplayName("测试获取流名称")
    void testGetStreamName() {
        assertEquals("btcusdt@kline_1m", client.getStreamName());
    }

    @Test
    @DisplayName("测试获取完整 WebSocket URL")
    void testGetFullWsUrl() {
        String expected = BinanceWebSocketClient.DEFAULT_SPOT_WS_URL + "/btcusdt@kline_1m";
        assertEquals(expected, client.getFullWsUrl());
    }

    @Test
    @DisplayName("测试获取订阅键")
    void testGetSubscriptionKey() {
        assertEquals("btcusdt_1m", client.getSubscriptionKey());
    }

    @Test
    @DisplayName("测试 Mock 模式连接")
    void testMockConnect() {
        assertFalse(client.isConnected());
        
        client.connect();
        
        assertTrue(client.isConnected());
        assertNotNull(client.getConnectedTime());
    }

    @Test
    @DisplayName("测试断开连接")
    void testDisconnect() {
        client.connect();
        assertTrue(client.isConnected());
        
        client.disconnect();
        
        assertFalse(client.isConnected());
        assertNotNull(client.getDisconnectedTime());
    }

    @Test
    @DisplayName("测试关闭客户端")
    void testClose() {
        client.connect();
        
        client.close();
        
        assertTrue(client.isClosed());
        assertFalse(client.isConnected());
    }

    @Test
    @DisplayName("测试关闭后无法连接")
    void testCannotConnectAfterClose() {
        client.close();
        
        client.connect();
        
        // Mock 模式下关闭后不应该连接成功
        assertFalse(client.isConnected());
    }

    @Test
    @DisplayName("测试重连计数")
    void testReconnectAttempts() {
        assertEquals(0, client.getReconnectAttempts());
        
        client.resetReconnectAttempts();
        
        assertEquals(0, client.getReconnectAttempts());
    }

    @Test
    @DisplayName("测试不同市场的 WebSocket URL")
    void testDifferentMarketWsUrls() {
        // 现货
        BinanceWebSocketClient spotClient = new BinanceWebSocketClient(
                BinanceWebSocketClient.DEFAULT_SPOT_WS_URL,
                "BTCUSDT", "1h", null, null, null, null, true);
        assertTrue(spotClient.getWsUrl().contains("stream.binance.com"));
        
        // U本位合约
        BinanceWebSocketClient futuresUsdtClient = new BinanceWebSocketClient(
                BinanceWebSocketClient.DEFAULT_FUTURES_USDT_WS_URL,
                "BTCUSDT", "1h", null, null, null, null, true);
        assertTrue(futuresUsdtClient.getWsUrl().contains("fstream.binance.com"));
        
        // 币本位合约
        BinanceWebSocketClient futuresCoinClient = new BinanceWebSocketClient(
                BinanceWebSocketClient.DEFAULT_FUTURES_COIN_WS_URL,
                "BTCUSD_PERP", "1h", null, null, null, null, true);
        assertTrue(futuresCoinClient.getWsUrl().contains("dstream.binance.com"));
    }

    @Test
    @DisplayName("测试 toString 方法")
    void testToString() {
        String str = client.toString();
        
        assertTrue(str.contains("btcusdt@kline_1m"));
        assertTrue(str.contains("connected="));
        assertTrue(str.contains("closed="));
    }
}
