package com.chanlun.exchange;

import com.chanlun.config.ProxyConfig;
import com.chanlun.entity.DataSource;
import com.chanlun.enums.ProxyType;
import com.chanlun.exchange.model.BinanceWsKlineEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 币安 WebSocket 客户端
 * 
 * 支持 K 线数据实时订阅，包括：
 * - 代理配置支持
 * - 自动断线重连
 * - 心跳检测
 * 
 * WebSocket 流地址:
 * - 现货: wss://stream.binance.com:9443/ws/<streamName>
 * - U本位合约: wss://fstream.binance.com/ws/<streamName>
 * - 币本位合约: wss://dstream.binance.com/ws/<streamName>
 * 
 * K线流名称格式: <symbol>@kline_<interval>
 * 例如: btcusdt@kline_1m
 * 
 * @author Chanlun Team
 */
@Slf4j
public class BinanceWebSocketClient {

    /**
     * 默认现货 WebSocket URL
     */
    public static final String DEFAULT_SPOT_WS_URL = "wss://stream.binance.com:9443/ws";

    /**
     * 默认 U 本位合约 WebSocket URL
     */
    public static final String DEFAULT_FUTURES_USDT_WS_URL = "wss://fstream.binance.com/ws";

    /**
     * 默认币本位合约 WebSocket URL
     */
    public static final String DEFAULT_FUTURES_COIN_WS_URL = "wss://dstream.binance.com/ws";

    /**
     * 默认连接超时时间（秒）
     */
    private static final int DEFAULT_CONNECT_TIMEOUT = 10;

    /**
     * 默认读取超时时间（秒）
     */
    private static final int DEFAULT_READ_TIMEOUT = 30;

    /**
     * 默认 Ping 间隔（分钟）
     */
    private static final int DEFAULT_PING_INTERVAL = 3;

    /**
     * 最大重连次数
     */
    private static final int MAX_RECONNECT_ATTEMPTS = 10;

    /**
     * 重连基础延迟（毫秒）
     */
    private static final long RECONNECT_BASE_DELAY_MS = 1000;

    /**
     * 重连最大延迟（毫秒）
     */
    private static final long RECONNECT_MAX_DELAY_MS = 60000;

    @Getter
    private final String wsUrl;

    @Getter
    private final String symbol;

    @Getter
    private final String interval;

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Consumer<BinanceWsKlineEvent> klineHandler;
    private final Consumer<Throwable> errorHandler;
    private final Runnable disconnectHandler;
    private final boolean mockEnabled;

    private WebSocket webSocket;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);

    @Getter
    private Instant lastMessageTime;

    @Getter
    private Instant connectedTime;

    @Getter
    private Instant disconnectedTime;

    /**
     * 构造函数
     * 
     * @param wsUrl WebSocket URL
     * @param symbol 交易对代码（小写）
     * @param interval 时间周期
     * @param proxyConfig 代理配置
     * @param klineHandler K线数据处理器
     * @param errorHandler 错误处理器
     * @param disconnectHandler 断开连接处理器
     * @param mockEnabled 是否启用 Mock 模式
     */
    public BinanceWebSocketClient(String wsUrl, String symbol, String interval,
                                   ProxyConfig proxyConfig,
                                   Consumer<BinanceWsKlineEvent> klineHandler,
                                   Consumer<Throwable> errorHandler,
                                   Runnable disconnectHandler,
                                   boolean mockEnabled) {
        this.wsUrl = wsUrl != null ? wsUrl : DEFAULT_SPOT_WS_URL;
        this.symbol = symbol.toLowerCase();
        this.interval = interval;
        this.klineHandler = klineHandler;
        this.errorHandler = errorHandler;
        this.disconnectHandler = disconnectHandler;
        this.mockEnabled = mockEnabled;
        this.objectMapper = new ObjectMapper();
        this.httpClient = createHttpClient(proxyConfig);
    }

    /**
     * 创建 OkHttpClient
     */
    private OkHttpClient createHttpClient(ProxyConfig proxyConfig) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(DEFAULT_CONNECT_TIMEOUT))
                .readTimeout(Duration.ofSeconds(DEFAULT_READ_TIMEOUT))
                .pingInterval(Duration.ofMinutes(DEFAULT_PING_INTERVAL))
                .retryOnConnectionFailure(true);

        if (proxyConfig != null && proxyConfig.isEnabled() && proxyConfig.isValid()) {
            configureProxy(builder, proxyConfig);
        }

        return builder.build();
    }

    /**
     * 配置代理
     */
    private void configureProxy(OkHttpClient.Builder builder, ProxyConfig proxyConfig) {
        Proxy.Type javaProxyType = proxyConfig.getType() == ProxyType.SOCKS5
                ? Proxy.Type.SOCKS : Proxy.Type.HTTP;

        Proxy proxy = new Proxy(javaProxyType,
                new InetSocketAddress(proxyConfig.getHost(), proxyConfig.getPort()));
        builder.proxy(proxy);

        if (proxyConfig.requiresAuthentication()) {
            Authenticator proxyAuthenticator = (route, response) -> {
                if (response.request().header("Proxy-Authorization") != null) {
                    return null;
                }
                String credential = Credentials.basic(
                        proxyConfig.getUsername(),
                        proxyConfig.getPassword() != null ? proxyConfig.getPassword() : ""
                );
                return response.request().newBuilder()
                        .header("Proxy-Authorization", credential)
                        .build();
            };
            builder.proxyAuthenticator(proxyAuthenticator);
        }

        log.debug("WebSocket configured with proxy: type={}, host={}, port={}",
                proxyConfig.getType(), proxyConfig.getHost(), proxyConfig.getPort());
    }

    /**
     * 获取订阅流名称
     */
    public String getStreamName() {
        return symbol + "@kline_" + interval;
    }

    /**
     * 获取完整的 WebSocket URL
     */
    public String getFullWsUrl() {
        return wsUrl + "/" + getStreamName();
    }

    /**
     * 连接 WebSocket
     */
    public void connect() {
        if (closed.get()) {
            log.warn("WebSocket client is closed, cannot connect: {}", getStreamName());
            return;
        }

        if (connected.get()) {
            log.debug("WebSocket already connected: {}", getStreamName());
            return;
        }

        if (mockEnabled) {
            log.info("Mock mode: simulating WebSocket connection for {}", getStreamName());
            connected.set(true);
            connectedTime = Instant.now();
            return;
        }

        String url = getFullWsUrl();
        log.info("Connecting to WebSocket: {}", url);

        Request request = new Request.Builder()
                .url(url)
                .build();

        webSocket = httpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                handleOpen(response);
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                handleMessage(text);
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                handleClosing(code, reason);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                handleClosed(code, reason);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                handleFailure(t, response);
            }
        });
    }

    /**
     * 处理连接打开
     */
    private void handleOpen(Response response) {
        connected.set(true);
        reconnecting.set(false);
        reconnectAttempts.set(0);
        connectedTime = Instant.now();
        disconnectedTime = null;

        log.info("WebSocket connected: {}, response code: {}", 
                getStreamName(), response != null ? response.code() : "N/A");
    }

    /**
     * 处理收到消息
     */
    private void handleMessage(String text) {
        lastMessageTime = Instant.now();

        try {
            BinanceWsKlineEvent event = objectMapper.readValue(text, BinanceWsKlineEvent.class);

            if (event.isKlineEvent() && klineHandler != null) {
                klineHandler.accept(event);
            }
        } catch (Exception e) {
            log.error("Failed to parse WebSocket message: {}", e.getMessage());
            if (errorHandler != null) {
                errorHandler.accept(e);
            }
        }
    }

    /**
     * 处理连接正在关闭
     */
    private void handleClosing(int code, String reason) {
        log.info("WebSocket closing: {}, code={}, reason={}", getStreamName(), code, reason);
    }

    /**
     * 处理连接已关闭
     */
    private void handleClosed(int code, String reason) {
        connected.set(false);
        disconnectedTime = Instant.now();

        log.info("WebSocket closed: {}, code={}, reason={}", getStreamName(), code, reason);

        if (disconnectHandler != null) {
            disconnectHandler.run();
        }

        // 如果不是主动关闭，尝试重连
        if (!closed.get()) {
            scheduleReconnect();
        }
    }

    /**
     * 处理连接失败
     */
    private void handleFailure(Throwable t, Response response) {
        connected.set(false);
        disconnectedTime = Instant.now();

        log.error("WebSocket failure: {}, error={}, response={}",
                getStreamName(), t.getMessage(), response != null ? response.code() : "N/A");

        if (errorHandler != null) {
            errorHandler.accept(t);
        }

        // 如果不是主动关闭，尝试重连
        if (!closed.get()) {
            scheduleReconnect();
        }
    }

    /**
     * 调度重连
     */
    private void scheduleReconnect() {
        if (closed.get() || reconnecting.get()) {
            return;
        }

        int attempts = reconnectAttempts.incrementAndGet();
        if (attempts > MAX_RECONNECT_ATTEMPTS) {
            log.error("Max reconnect attempts reached for {}, giving up", getStreamName());
            return;
        }

        reconnecting.set(true);

        // 指数退避延迟
        long delay = Math.min(
                RECONNECT_BASE_DELAY_MS * (1L << (attempts - 1)),
                RECONNECT_MAX_DELAY_MS
        );

        log.info("Scheduling reconnect for {} in {}ms (attempt {})", 
                getStreamName(), delay, attempts);

        // 使用新线程进行重连
        new Thread(() -> {
            try {
                Thread.sleep(delay);
                if (!closed.get()) {
                    reconnecting.set(false);
                    connect();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.debug("Reconnect interrupted for {}", getStreamName());
            }
        }, "ws-reconnect-" + getStreamName()).start();
    }

    /**
     * 断开连接
     */
    public void disconnect() {
        if (webSocket != null) {
            log.info("Disconnecting WebSocket: {}", getStreamName());
            webSocket.close(1000, "Client disconnect");
        }
        connected.set(false);
        disconnectedTime = Instant.now();
    }

    /**
     * 关闭客户端（不再重连）
     */
    public void close() {
        closed.set(true);
        disconnect();
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
        log.info("WebSocket client closed: {}", getStreamName());
    }

    /**
     * 检查是否已连接
     */
    public boolean isConnected() {
        return connected.get();
    }

    /**
     * 检查是否已关闭
     */
    public boolean isClosed() {
        return closed.get();
    }

    /**
     * 检查是否正在重连
     */
    public boolean isReconnecting() {
        return reconnecting.get();
    }

    /**
     * 获取重连次数
     */
    public int getReconnectAttempts() {
        return reconnectAttempts.get();
    }

    /**
     * 重置重连计数
     */
    public void resetReconnectAttempts() {
        reconnectAttempts.set(0);
    }

    /**
     * 获取订阅键（用于管理器）
     */
    public String getSubscriptionKey() {
        return symbol + "_" + interval;
    }

    @Override
    public String toString() {
        return "BinanceWebSocketClient{" +
                "stream=" + getStreamName() +
                ", connected=" + connected.get() +
                ", closed=" + closed.get() +
                '}';
    }
}
