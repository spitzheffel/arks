package com.chanlun.exchange;

import com.chanlun.config.ProxyConfig;
import com.chanlun.entity.DataSource;
import com.chanlun.enums.ProxyType;
import com.chanlun.exchange.model.BinanceApiResponse;
import com.chanlun.exchange.model.BinanceExchangeInfo;
import com.chanlun.exchange.model.BinanceKline;
import com.chanlun.exchange.model.BinanceServerTime;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 币安交易所客户端基础类
 * 
 * 支持代理配置、API 限流处理、连接测试
 * 支持 Mock 模式用于本地开发和 E2E 测试
 * 
 * @author Chanlun Team
 */
@Slf4j
public class BinanceClient {

    /**
     * 默认现货 API 基础 URL
     */
    public static final String DEFAULT_SPOT_BASE_URL = "https://api.binance.com";

    /**
     * 默认 U 本位合约 API 基础 URL
     */
    public static final String DEFAULT_FUTURES_USDT_BASE_URL = "https://fapi.binance.com";

    /**
     * 默认币本位合约 API 基础 URL
     */
    public static final String DEFAULT_FUTURES_COIN_BASE_URL = "https://dapi.binance.com";

    /**
     * 默认连接超时时间（秒）
     */
    private static final int DEFAULT_CONNECT_TIMEOUT = 10;

    /**
     * 默认读取超时时间（秒）
     */
    private static final int DEFAULT_READ_TIMEOUT = 30;

    /**
     * 默认写入超时时间（秒）
     */
    private static final int DEFAULT_WRITE_TIMEOUT = 30;

    /**
     * 每分钟最大请求权重（币安限制为 6000）
     */
    private static final int MAX_WEIGHT_PER_MINUTE = 6000;

    /**
     * 权重重置间隔（毫秒）
     */
    private static final long WEIGHT_RESET_INTERVAL_MS = 60_000;

    /**
     * 限流等待最大时间（毫秒）
     */
    private static final long MAX_RATE_LIMIT_WAIT_MS = 30_000;

    @Getter
    private final String baseUrl;

    @Getter
    private final String apiKey;

    @Getter
    private final String secretKey;

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final boolean mockEnabled;

    // 限流控制
    private final AtomicInteger currentWeight = new AtomicInteger(0);
    private final AtomicLong lastResetTime = new AtomicLong(System.currentTimeMillis());
    private final Object rateLimitLock = new Object();

    /**
     * 构造函数
     * 
     * @param baseUrl API 基础 URL
     * @param apiKey API Key（可为空，用于公开接口）
     * @param secretKey Secret Key（可为空，用于公开接口）
     * @param proxyConfig 代理配置（可为空）
     * @param mockEnabled 是否启用 Mock 模式
     */
    public BinanceClient(String baseUrl, String apiKey, String secretKey, 
                         ProxyConfig proxyConfig, boolean mockEnabled) {
        this.baseUrl = baseUrl != null ? baseUrl : DEFAULT_SPOT_BASE_URL;
        this.apiKey = apiKey;
        this.secretKey = secretKey;
        this.mockEnabled = mockEnabled;
        this.objectMapper = new ObjectMapper();
        this.httpClient = createHttpClient(proxyConfig);
        
        log.info("BinanceClient initialized: baseUrl={}, mockEnabled={}, proxyEnabled={}", 
                this.baseUrl, mockEnabled, proxyConfig != null && proxyConfig.isEnabled());
    }

    /**
     * 从数据源实体创建客户端
     * 
     * @param dataSource 数据源实体
     * @param decryptedApiKey 解密后的 API Key
     * @param decryptedSecretKey 解密后的 Secret Key
     * @param decryptedProxyPassword 解密后的代理密码
     * @param mockEnabled 是否启用 Mock 模式
     * @return BinanceClient 实例
     */
    public static BinanceClient fromDataSource(DataSource dataSource, 
                                                String decryptedApiKey,
                                                String decryptedSecretKey,
                                                String decryptedProxyPassword,
                                                boolean mockEnabled) {
        ProxyConfig proxyConfig = ProxyConfig.fromDataSource(dataSource, decryptedProxyPassword);
        return new BinanceClient(
                dataSource.getBaseUrl(),
                decryptedApiKey,
                decryptedSecretKey,
                proxyConfig,
                mockEnabled
        );
    }

    /**
     * 创建 OkHttpClient
     */
    private OkHttpClient createHttpClient(ProxyConfig proxyConfig) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(DEFAULT_CONNECT_TIMEOUT))
                .readTimeout(Duration.ofSeconds(DEFAULT_READ_TIMEOUT))
                .writeTimeout(Duration.ofSeconds(DEFAULT_WRITE_TIMEOUT))
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

        log.debug("Configured proxy: type={}, host={}, port={}", 
                proxyConfig.getType(), proxyConfig.getHost(), proxyConfig.getPort());
    }

    /**
     * 测试连接 - Ping
     * 
     * @return true 如果连接成功
     */
    public boolean ping() {
        if (mockEnabled) {
            log.debug("Mock mode: ping returns true");
            return true;
        }

        try {
            // ping 接口权重为 1
            acquireWeight(1);
            
            String url = baseUrl + "/api/v3/ping";
            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                boolean success = response.isSuccessful();
                log.debug("Ping result: success={}, code={}", success, response.code());
                return success;
            }
        } catch (IOException e) {
            log.error("Ping failed: {}", e.getMessage());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Ping interrupted");
            return false;
        }
    }

    /**
     * 获取服务器时间
     * 
     * @return 服务器时间响应
     */
    public BinanceApiResponse<BinanceServerTime> getServerTime() {
        if (mockEnabled) {
            log.debug("Mock mode: returning mock server time");
            BinanceServerTime mockTime = new BinanceServerTime();
            mockTime.setServerTime(System.currentTimeMillis());
            return BinanceApiResponse.success(mockTime);
        }

        try {
            // serverTime 接口权重为 1
            acquireWeight(1);
            
            String url = baseUrl + "/api/v3/time";
            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return handleErrorResponse(response);
                }

                String body = response.body() != null ? response.body().string() : "";
                BinanceServerTime serverTime = objectMapper.readValue(body, BinanceServerTime.class);
                return BinanceApiResponse.success(serverTime);
            }
        } catch (IOException e) {
            log.error("Get server time failed: {}", e.getMessage());
            return BinanceApiResponse.error(-1, "Network error: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return BinanceApiResponse.error(-1, "Request interrupted");
        }
    }

    /**
     * 测试连接（ping + serverTime）
     * 
     * @return 测试结果
     */
    public ConnectionTestResult testConnection() {
        long startTime = System.currentTimeMillis();
        
        // 测试 ping
        boolean pingSuccess = ping();
        if (!pingSuccess) {
            return ConnectionTestResult.builder()
                    .success(false)
                    .message("Ping failed")
                    .latencyMs(System.currentTimeMillis() - startTime)
                    .build();
        }

        // 测试 serverTime
        BinanceApiResponse<BinanceServerTime> timeResponse = getServerTime();
        long latency = System.currentTimeMillis() - startTime;

        if (!timeResponse.isSuccess()) {
            return ConnectionTestResult.builder()
                    .success(false)
                    .message("Get server time failed: " + timeResponse.getMessage())
                    .latencyMs(latency)
                    .build();
        }

        // 计算时间差
        long serverTime = timeResponse.getData().getServerTime();
        long timeDiff = Math.abs(System.currentTimeMillis() - serverTime);

        return ConnectionTestResult.builder()
                .success(true)
                .message("Connection successful")
                .latencyMs(latency)
                .serverTime(Instant.ofEpochMilli(serverTime))
                .timeDiffMs(timeDiff)
                .build();
    }

    /**
     * 执行 GET 请求
     * 
     * @param path API 路径
     * @param weight 请求权重
     * @return 响应体字符串
     */
    protected String doGet(String path, int weight) throws IOException, InterruptedException {
        acquireWeight(weight);
        
        String url = baseUrl + path;
        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("X-MBX-APIKEY", apiKey != null ? apiKey : "")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Request failed: " + response.code() + " " + response.message());
            }
            return response.body() != null ? response.body().string() : "";
        }
    }

    /**
     * 获取请求权重（限流控制）
     * 
     * @param weight 请求权重
     */
    protected void acquireWeight(int weight) throws InterruptedException {
        synchronized (rateLimitLock) {
            long now = System.currentTimeMillis();
            long elapsed = now - lastResetTime.get();

            // 检查是否需要重置权重
            if (elapsed >= WEIGHT_RESET_INTERVAL_MS) {
                currentWeight.set(0);
                lastResetTime.set(now);
                log.debug("Rate limit weight reset");
            }

            // 检查是否超过限制
            int newWeight = currentWeight.get() + weight;
            if (newWeight > MAX_WEIGHT_PER_MINUTE) {
                // 计算需要等待的时间
                long waitTime = WEIGHT_RESET_INTERVAL_MS - elapsed;
                if (waitTime > MAX_RATE_LIMIT_WAIT_MS) {
                    throw new InterruptedException("Rate limit exceeded, wait time too long: " + waitTime + "ms");
                }
                
                log.warn("Rate limit reached, waiting {}ms", waitTime);
                rateLimitLock.wait(waitTime);
                
                // 重置后重新获取
                currentWeight.set(0);
                lastResetTime.set(System.currentTimeMillis());
            }

            currentWeight.addAndGet(weight);
            log.trace("Acquired weight: {}, current total: {}", weight, currentWeight.get());
        }
    }

    /**
     * 获取当前已使用的权重
     */
    public int getCurrentWeight() {
        return currentWeight.get();
    }

    /**
     * 获取剩余可用权重
     */
    public int getRemainingWeight() {
        return MAX_WEIGHT_PER_MINUTE - currentWeight.get();
    }

    /**
     * 处理错误响应
     */
    protected <T> BinanceApiResponse<T> handleErrorResponse(Response response) throws IOException {
        String body = response.body() != null ? response.body().string() : "";
        
        try {
            JsonNode node = objectMapper.readTree(body);
            int code = node.has("code") ? node.get("code").asInt() : response.code();
            String msg = node.has("msg") ? node.get("msg").asText() : response.message();
            return BinanceApiResponse.error(code, msg);
        } catch (JsonProcessingException e) {
            return BinanceApiResponse.error(response.code(), response.message());
        }
    }

    /**
     * 获取交易所信息（包含所有交易对）
     * 
     * 现货: GET /api/v3/exchangeInfo (weight: 20)
     * U本位合约: GET /fapi/v1/exchangeInfo (weight: 1)
     * 币本位合约: GET /dapi/v1/exchangeInfo (weight: 1)
     * 
     * @return 交易所信息响应
     */
    public BinanceApiResponse<BinanceExchangeInfo> getExchangeInfo() {
        if (mockEnabled) {
            log.debug("Mock mode: returning mock exchange info");
            return getMockExchangeInfo();
        }

        try {
            // 根据 baseUrl 判断使用哪个 API 路径
            String path = getExchangeInfoPath();
            int weight = getExchangeInfoWeight();
            
            acquireWeight(weight);
            
            String url = baseUrl + path;
            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return handleErrorResponse(response);
                }

                String body = response.body() != null ? response.body().string() : "";
                BinanceExchangeInfo exchangeInfo = objectMapper.readValue(body, BinanceExchangeInfo.class);
                log.info("Retrieved exchange info: {} symbols", 
                        exchangeInfo.getSymbols() != null ? exchangeInfo.getSymbols().size() : 0);
                return BinanceApiResponse.success(exchangeInfo);
            }
        } catch (IOException e) {
            log.error("Get exchange info failed: {}", e.getMessage());
            return BinanceApiResponse.error(-1, "Network error: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return BinanceApiResponse.error(-1, "Request interrupted");
        }
    }

    /**
     * 支持的 K 线周期列表（不支持 1s）
     */
    private static final Set<String> VALID_INTERVALS = Set.of(
            "1m", "3m", "5m", "15m", "30m",
            "1h", "2h", "4h", "6h", "8h", "12h",
            "1d", "3d", "1w", "1M"
    );

    /**
     * K 线 API 单次最大返回数量
     */
    public static final int KLINE_MAX_LIMIT = 1000;

    /**
     * K 线 API 默认返回数量
     */
    public static final int KLINE_DEFAULT_LIMIT = 500;

    /**
     * 获取 K 线数据
     * 
     * 现货: GET /api/v3/klines (weight: 2)
     * U本位合约: GET /fapi/v1/klines (weight: 5)
     * 币本位合约: GET /dapi/v1/klines (weight: 5)
     * 
     * @param symbol 交易对代码 (如 BTCUSDT)
     * @param interval 时间周期 (1m/3m/5m/15m/30m/1h/2h/4h/6h/8h/12h/1d/3d/1w/1M)
     * @param startTime 开始时间 (UTC 毫秒，可选)
     * @param endTime 结束时间 (UTC 毫秒，可选)
     * @param limit 返回数量 (默认 500，最大 1000)
     * @return K 线数据列表
     */
    public BinanceApiResponse<List<BinanceKline>> getKlines(String symbol, String interval,
                                                             Long startTime, Long endTime, Integer limit) {
        // 校验参数
        if (symbol == null || symbol.isBlank()) {
            return BinanceApiResponse.error(-1, "Symbol is required");
        }
        if (interval == null || interval.isBlank()) {
            return BinanceApiResponse.error(-1, "Interval is required");
        }
        if (!VALID_INTERVALS.contains(interval)) {
            return BinanceApiResponse.error(-1, "Invalid interval: " + interval);
        }

        if (mockEnabled) {
            log.debug("Mock mode: returning mock klines for {} {}", symbol, interval);
            return getMockKlines(symbol, interval, startTime, endTime, limit);
        }

        try {
            // 构建请求路径
            String path = getKlinesPath();
            int weight = getKlinesWeight();
            
            // 规范化 limit
            int actualLimit = limit != null ? Math.min(limit, KLINE_MAX_LIMIT) : KLINE_DEFAULT_LIMIT;
            
            // 构建查询参数
            StringBuilder queryBuilder = new StringBuilder();
            queryBuilder.append("?symbol=").append(symbol);
            queryBuilder.append("&interval=").append(interval);
            queryBuilder.append("&limit=").append(actualLimit);
            
            if (startTime != null) {
                queryBuilder.append("&startTime=").append(startTime);
            }
            if (endTime != null) {
                queryBuilder.append("&endTime=").append(endTime);
            }
            
            acquireWeight(weight);
            
            String url = baseUrl + path + queryBuilder;
            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return handleErrorResponse(response);
                }

                String body = response.body() != null ? response.body().string() : "";
                List<BinanceKline> klines = parseKlinesResponse(body);
                
                log.debug("Retrieved {} klines for {} {}", klines.size(), symbol, interval);
                return BinanceApiResponse.success(klines);
            }
        } catch (IOException e) {
            log.error("Get klines failed: {}", e.getMessage());
            return BinanceApiResponse.error(-1, "Network error: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return BinanceApiResponse.error(-1, "Request interrupted");
        }
    }

    /**
     * 获取 K 线数据（使用 Instant 时间）
     * 
     * @param symbol 交易对代码
     * @param interval 时间周期
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param limit 返回数量
     * @return K 线数据列表
     */
    public BinanceApiResponse<List<BinanceKline>> getKlines(String symbol, String interval,
                                                             Instant startTime, Instant endTime, Integer limit) {
        Long startMs = startTime != null ? startTime.toEpochMilli() : null;
        Long endMs = endTime != null ? endTime.toEpochMilli() : null;
        return getKlines(symbol, interval, startMs, endMs, limit);
    }

    /**
     * 获取 K 线数据（简化版，仅指定数量）
     * 
     * @param symbol 交易对代码
     * @param interval 时间周期
     * @param limit 返回数量
     * @return K 线数据列表
     */
    public BinanceApiResponse<List<BinanceKline>> getKlines(String symbol, String interval, Integer limit) {
        return getKlines(symbol, interval, (Long) null, null, limit);
    }

    /**
     * 根据 baseUrl 获取 klines API 路径
     */
    private String getKlinesPath() {
        if (baseUrl.contains("fapi.binance.com")) {
            return "/fapi/v1/klines";
        } else if (baseUrl.contains("dapi.binance.com")) {
            return "/dapi/v1/klines";
        } else {
            return "/api/v3/klines";
        }
    }

    /**
     * 根据 baseUrl 获取 klines API 权重
     */
    private int getKlinesWeight() {
        if (baseUrl.contains("fapi.binance.com") || baseUrl.contains("dapi.binance.com")) {
            return 5;
        }
        return 2; // 现货 API 权重为 2
    }

    /**
     * 解析 K 线响应数据
     */
    private List<BinanceKline> parseKlinesResponse(String body) throws IOException {
        List<Object[]> rawData = objectMapper.readValue(body, new TypeReference<List<Object[]>>() {});
        List<BinanceKline> klines = new ArrayList<>(rawData.size());
        
        for (Object[] data : rawData) {
            try {
                klines.add(BinanceKline.fromArray(data));
            } catch (Exception e) {
                log.warn("Failed to parse kline data: {}", e.getMessage());
            }
        }
        
        return klines;
    }

    /**
     * 获取 Mock K 线数据
     */
    protected BinanceApiResponse<List<BinanceKline>> getMockKlines(String symbol, String interval,
                                                                    Long startTime, Long endTime, Integer limit) {
        // 子类可以覆盖此方法提供更丰富的 mock 数据
        return BinanceApiResponse.success(createMockKlines(symbol, interval, startTime, endTime, limit));
    }

    /**
     * 创建 Mock K 线数据
     */
    protected List<BinanceKline> createMockKlines(String symbol, String interval,
                                                   Long startTime, Long endTime, Integer limit) {
        int count = limit != null ? Math.min(limit, KLINE_MAX_LIMIT) : KLINE_DEFAULT_LIMIT;
        long intervalMs = getIntervalMillis(interval);
        
        // 如果没有指定结束时间，使用当前时间
        long end = endTime != null ? endTime : System.currentTimeMillis();
        // 如果没有指定开始时间，从结束时间往前推
        long start = startTime != null ? startTime : end - (count * intervalMs);
        
        // 计算实际需要生成的 K 线数量
        int actualCount = (int) Math.min(count, (end - start) / intervalMs + 1);
        
        List<BinanceKline> klines = new ArrayList<>(actualCount);
        
        // 基础价格（根据交易对设置不同的基础价格）
        double basePrice = getBasePriceForSymbol(symbol);
        
        for (int i = 0; i < actualCount; i++) {
            long openTime = start + (i * intervalMs);
            long closeTime = openTime + intervalMs - 1;
            
            // 生成随机价格波动
            double priceChange = (Math.random() - 0.5) * basePrice * 0.02; // ±1% 波动
            double open = basePrice + priceChange;
            double high = open * (1 + Math.random() * 0.01);
            double low = open * (1 - Math.random() * 0.01);
            double close = low + Math.random() * (high - low);
            double volume = 100 + Math.random() * 1000;
            
            klines.add(BinanceKline.builder()
                    .openTime(openTime)
                    .open(java.math.BigDecimal.valueOf(open))
                    .high(java.math.BigDecimal.valueOf(high))
                    .low(java.math.BigDecimal.valueOf(low))
                    .close(java.math.BigDecimal.valueOf(close))
                    .volume(java.math.BigDecimal.valueOf(volume))
                    .closeTime(closeTime)
                    .quoteVolume(java.math.BigDecimal.valueOf(volume * close))
                    .trades((int) (50 + Math.random() * 500))
                    .takerBuyBaseVolume(java.math.BigDecimal.valueOf(volume * 0.5))
                    .takerBuyQuoteVolume(java.math.BigDecimal.valueOf(volume * close * 0.5))
                    .build());
            
            // 更新基础价格
            basePrice = close;
        }
        
        return klines;
    }

    /**
     * 获取周期对应的毫秒数
     */
    protected long getIntervalMillis(String interval) {
        return switch (interval) {
            case "1m" -> 60_000L;
            case "3m" -> 3 * 60_000L;
            case "5m" -> 5 * 60_000L;
            case "15m" -> 15 * 60_000L;
            case "30m" -> 30 * 60_000L;
            case "1h" -> 60 * 60_000L;
            case "2h" -> 2 * 60 * 60_000L;
            case "4h" -> 4 * 60 * 60_000L;
            case "6h" -> 6 * 60 * 60_000L;
            case "8h" -> 8 * 60 * 60_000L;
            case "12h" -> 12 * 60 * 60_000L;
            case "1d" -> 24 * 60 * 60_000L;
            case "3d" -> 3 * 24 * 60 * 60_000L;
            case "1w" -> 7 * 24 * 60 * 60_000L;
            case "1M" -> 30 * 24 * 60 * 60_000L; // 近似值
            default -> 60_000L;
        };
    }

    /**
     * 根据交易对获取基础价格（用于 Mock）
     */
    private double getBasePriceForSymbol(String symbol) {
        if (symbol.startsWith("BTC")) {
            return 50000.0;
        } else if (symbol.startsWith("ETH")) {
            return 3000.0;
        } else if (symbol.startsWith("BNB")) {
            return 400.0;
        } else if (symbol.startsWith("SOL")) {
            return 100.0;
        } else if (symbol.startsWith("XRP")) {
            return 0.5;
        } else if (symbol.startsWith("DOGE")) {
            return 0.1;
        } else {
            return 100.0;
        }
    }

    /**
     * 根据 baseUrl 获取 exchangeInfo API 路径
     */
    private String getExchangeInfoPath() {
        if (baseUrl.contains("fapi.binance.com")) {
            return "/fapi/v1/exchangeInfo";
        } else if (baseUrl.contains("dapi.binance.com")) {
            return "/dapi/v1/exchangeInfo";
        } else {
            return "/api/v3/exchangeInfo";
        }
    }

    /**
     * 根据 baseUrl 获取 exchangeInfo API 权重
     */
    private int getExchangeInfoWeight() {
        if (baseUrl.contains("fapi.binance.com") || baseUrl.contains("dapi.binance.com")) {
            return 1;
        }
        return 20; // 现货 API 权重为 20
    }

    /**
     * 获取 Mock 交易所信息
     */
    protected BinanceApiResponse<BinanceExchangeInfo> getMockExchangeInfo() {
        // 子类可以覆盖此方法提供更丰富的 mock 数据
        return BinanceApiResponse.success(createDefaultMockExchangeInfo());
    }

    /**
     * 创建默认的 Mock 交易所信息
     */
    protected BinanceExchangeInfo createDefaultMockExchangeInfo() {
        java.util.List<BinanceExchangeInfo.BinanceSymbol> symbols = java.util.Arrays.asList(
                BinanceExchangeInfo.BinanceSymbol.builder()
                        .symbol("BTCUSDT")
                        .status("TRADING")
                        .baseAsset("BTC")
                        .quoteAsset("USDT")
                        .baseAssetPrecision(8)
                        .quotePrecision(8)
                        .pricePrecision(2)
                        .quantityPrecision(3)
                        .build(),
                BinanceExchangeInfo.BinanceSymbol.builder()
                        .symbol("ETHUSDT")
                        .status("TRADING")
                        .baseAsset("ETH")
                        .quoteAsset("USDT")
                        .baseAssetPrecision(8)
                        .quotePrecision(8)
                        .pricePrecision(2)
                        .quantityPrecision(4)
                        .build(),
                BinanceExchangeInfo.BinanceSymbol.builder()
                        .symbol("BNBUSDT")
                        .status("TRADING")
                        .baseAsset("BNB")
                        .quoteAsset("USDT")
                        .baseAssetPrecision(8)
                        .quotePrecision(8)
                        .pricePrecision(2)
                        .quantityPrecision(3)
                        .build()
        );

        return BinanceExchangeInfo.builder()
                .timezone("UTC")
                .serverTime(System.currentTimeMillis())
                .symbols(symbols)
                .build();
    }

    /**
     * 关闭客户端
     */
    public void close() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
        log.info("BinanceClient closed");
    }

    /**
     * 连接测试结果
     */
    @lombok.Data
    @lombok.Builder
    public static class ConnectionTestResult {
        private boolean success;
        private String message;
        private long latencyMs;
        private Instant serverTime;
        private Long timeDiffMs;
    }
}
