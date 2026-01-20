package com.chanlun.exchange;

import com.chanlun.exchange.model.BinanceApiResponse;
import com.chanlun.exchange.model.BinanceExchangeInfo;
import com.chanlun.exchange.model.BinanceKline;
import com.chanlun.exchange.model.BinanceServerTime;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 币安 Mock 客户端
 * 
 * 用于本地开发和 E2E 测试，不实际调用币安 API
 * 通过环境变量 EXCHANGE_API_MOCK=true 启用
 * 
 * @author Chanlun Team
 */
@Slf4j
public class BinanceMockClient extends BinanceClient {

    /**
     * 模拟延迟（毫秒）
     */
    private static final long MOCK_DELAY_MS = 50;

    public BinanceMockClient(String baseUrl) {
        super(baseUrl, null, null, null, true);
        log.info("BinanceMockClient initialized - all API calls will be mocked");
    }

    @Override
    public boolean ping() {
        simulateDelay();
        log.debug("Mock ping: returning true");
        return true;
    }

    @Override
    public BinanceApiResponse<BinanceServerTime> getServerTime() {
        simulateDelay();
        BinanceServerTime serverTime = new BinanceServerTime();
        serverTime.setServerTime(System.currentTimeMillis());
        log.debug("Mock getServerTime: returning current time");
        return BinanceApiResponse.success(serverTime);
    }

    @Override
    public ConnectionTestResult testConnection() {
        simulateDelay();
        return ConnectionTestResult.builder()
                .success(true)
                .message("Mock connection successful")
                .latencyMs(MOCK_DELAY_MS)
                .serverTime(java.time.Instant.now())
                .timeDiffMs(0L)
                .build();
    }

    @Override
    public BinanceApiResponse<BinanceExchangeInfo> getExchangeInfo() {
        simulateDelay();
        log.debug("Mock getExchangeInfo: returning mock exchange info");
        return BinanceApiResponse.success(createMockExchangeInfo());
    }

    @Override
    protected BinanceApiResponse<List<BinanceKline>> getMockKlines(String symbol, String interval,
                                                                    Long startTime, Long endTime, Integer limit) {
        simulateDelay();
        log.debug("Mock getKlines: symbol={}, interval={}, limit={}", symbol, interval, limit);
        return BinanceApiResponse.success(createMockKlines(symbol, interval, startTime, endTime, limit));
    }

    /**
     * 创建 Mock 交易所信息
     * 根据 baseUrl 返回不同市场类型的交易对
     */
    private BinanceExchangeInfo createMockExchangeInfo() {
        List<BinanceExchangeInfo.BinanceSymbol> symbols = new ArrayList<>();
        
        String baseUrl = getBaseUrl();
        
        if (baseUrl.contains("fapi.binance.com")) {
            // U本位合约交易对
            symbols.addAll(createUsdtMarginalSymbols());
        } else if (baseUrl.contains("dapi.binance.com")) {
            // 币本位合约交易对
            symbols.addAll(createCoinMarginalSymbols());
        } else {
            // 现货交易对
            symbols.addAll(createSpotSymbols());
        }

        return BinanceExchangeInfo.builder()
                .timezone("UTC")
                .serverTime(System.currentTimeMillis())
                .symbols(symbols)
                .build();
    }

    /**
     * 创建现货交易对 Mock 数据
     */
    private List<BinanceExchangeInfo.BinanceSymbol> createSpotSymbols() {
        List<BinanceExchangeInfo.BinanceSymbol> symbols = new ArrayList<>();
        
        // 主流交易对
        symbols.add(createSpotSymbol("BTCUSDT", "BTC", "USDT", 8, 8, "TRADING"));
        symbols.add(createSpotSymbol("ETHUSDT", "ETH", "USDT", 8, 8, "TRADING"));
        symbols.add(createSpotSymbol("BNBUSDT", "BNB", "USDT", 8, 8, "TRADING"));
        symbols.add(createSpotSymbol("XRPUSDT", "XRP", "USDT", 8, 8, "TRADING"));
        symbols.add(createSpotSymbol("ADAUSDT", "ADA", "USDT", 8, 8, "TRADING"));
        symbols.add(createSpotSymbol("SOLUSDT", "SOL", "USDT", 8, 8, "TRADING"));
        symbols.add(createSpotSymbol("DOGEUSDT", "DOGE", "USDT", 8, 8, "TRADING"));
        symbols.add(createSpotSymbol("DOTUSDT", "DOT", "USDT", 8, 8, "TRADING"));
        
        // BTC 交易对
        symbols.add(createSpotSymbol("ETHBTC", "ETH", "BTC", 8, 8, "TRADING"));
        symbols.add(createSpotSymbol("BNBBTC", "BNB", "BTC", 8, 8, "TRADING"));
        
        // 停止交易的交易对
        symbols.add(createSpotSymbol("LUNAUSDT", "LUNA", "USDT", 8, 8, "HALT"));
        
        return symbols;
    }

    /**
     * 创建 U 本位合约交易对 Mock 数据
     */
    private List<BinanceExchangeInfo.BinanceSymbol> createUsdtMarginalSymbols() {
        List<BinanceExchangeInfo.BinanceSymbol> symbols = new ArrayList<>();
        
        symbols.add(createFuturesSymbol("BTCUSDT", "BTC", "USDT", 2, 3, "PERPETUAL", "TRADING"));
        symbols.add(createFuturesSymbol("ETHUSDT", "ETH", "USDT", 2, 3, "PERPETUAL", "TRADING"));
        symbols.add(createFuturesSymbol("BNBUSDT", "BNB", "USDT", 2, 2, "PERPETUAL", "TRADING"));
        symbols.add(createFuturesSymbol("XRPUSDT", "XRP", "USDT", 4, 1, "PERPETUAL", "TRADING"));
        symbols.add(createFuturesSymbol("ADAUSDT", "ADA", "USDT", 5, 0, "PERPETUAL", "TRADING"));
        symbols.add(createFuturesSymbol("SOLUSDT", "SOL", "USDT", 3, 0, "PERPETUAL", "TRADING"));
        
        return symbols;
    }

    /**
     * 创建币本位合约交易对 Mock 数据
     */
    private List<BinanceExchangeInfo.BinanceSymbol> createCoinMarginalSymbols() {
        List<BinanceExchangeInfo.BinanceSymbol> symbols = new ArrayList<>();
        
        symbols.add(createFuturesSymbol("BTCUSD_PERP", "BTC", "USD", 1, 0, "PERPETUAL", "TRADING"));
        symbols.add(createFuturesSymbol("ETHUSD_PERP", "ETH", "USD", 2, 0, "PERPETUAL", "TRADING"));
        symbols.add(createFuturesSymbol("BNBUSD_PERP", "BNB", "USD", 3, 0, "PERPETUAL", "TRADING"));
        
        return symbols;
    }

    /**
     * 创建现货交易对
     */
    private BinanceExchangeInfo.BinanceSymbol createSpotSymbol(
            String symbol, String baseAsset, String quoteAsset,
            int baseAssetPrecision, int quotePrecision, String status) {
        return BinanceExchangeInfo.BinanceSymbol.builder()
                .symbol(symbol)
                .baseAsset(baseAsset)
                .quoteAsset(quoteAsset)
                .baseAssetPrecision(baseAssetPrecision)
                .quotePrecision(quotePrecision)
                .status(status)
                .build();
    }

    /**
     * 创建合约交易对
     */
    private BinanceExchangeInfo.BinanceSymbol createFuturesSymbol(
            String symbol, String baseAsset, String quoteAsset,
            int pricePrecision, int quantityPrecision, String contractType, String status) {
        return BinanceExchangeInfo.BinanceSymbol.builder()
                .symbol(symbol)
                .baseAsset(baseAsset)
                .quoteAsset(quoteAsset)
                .pricePrecision(pricePrecision)
                .quantityPrecision(quantityPrecision)
                .contractType(contractType)
                .status(status)
                .build();
    }

    /**
     * 模拟网络延迟
     */
    private void simulateDelay() {
        try {
            Thread.sleep(MOCK_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
