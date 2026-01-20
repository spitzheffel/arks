package com.chanlun.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chanlun.dto.MarketDTO;
import com.chanlun.dto.SymbolDTO;
import com.chanlun.dto.SymbolSyncResult;
import com.chanlun.entity.DataSource;
import com.chanlun.entity.Market;
import com.chanlun.entity.Symbol;
import com.chanlun.exception.BusinessException;
import com.chanlun.exception.ResourceNotFoundException;
import com.chanlun.exchange.BinanceClient;
import com.chanlun.exchange.BinanceClientFactory;
import com.chanlun.exchange.model.BinanceApiResponse;
import com.chanlun.exchange.model.BinanceExchangeInfo;
import com.chanlun.mapper.SymbolMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 交易对服务测试
 * 
 * @author Chanlun Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SymbolService 测试")
class SymbolServiceTest {

    @Mock
    private SymbolMapper symbolMapper;

    @Mock
    private MarketService marketService;

    @Mock
    private DataSourceService dataSourceService;

    @Mock
    private BinanceClientFactory binanceClientFactory;

    @InjectMocks
    private SymbolService symbolService;

    private Symbol testSymbol;
    private Market testMarket;
    private DataSource testDataSource;


    @BeforeEach
    void setUp() {
        testDataSource = DataSource.builder()
                .id(1L)
                .name("Test Binance")
                .exchangeType("BINANCE")
                .enabled(true)
                .deleted(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        testMarket = Market.builder()
                .id(1L)
                .dataSourceId(1L)
                .name("现货市场")
                .marketType("SPOT")
                .enabled(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        testSymbol = Symbol.builder()
                .id(1L)
                .marketId(1L)
                .symbol("BTCUSDT")
                .baseAsset("BTC")
                .quoteAsset("USDT")
                .pricePrecision(8)
                .quantityPrecision(8)
                .realtimeSyncEnabled(false)
                .historySyncEnabled(false)
                .status("TRADING")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("获取交易对 - 成功")
    void getById_success() {
        when(symbolMapper.selectById(1L)).thenReturn(testSymbol);
        when(marketService.findById(1L)).thenReturn(testMarket);
        when(dataSourceService.findById(1L)).thenReturn(testDataSource);

        SymbolDTO result = symbolService.getById(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("BTCUSDT", result.getSymbol());
        assertEquals("BTC", result.getBaseAsset());
        assertEquals("USDT", result.getQuoteAsset());
        assertEquals("现货市场", result.getMarketName());
        assertEquals("Test Binance", result.getDataSourceName());
    }

    @Test
    @DisplayName("获取交易对 - 不存在应抛出异常")
    void getById_notFound_shouldThrowException() {
        when(symbolMapper.selectById(999L)).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> symbolService.getById(999L));
    }

    @Test
    @DisplayName("更新实时同步状态 - 启用成功")
    void updateRealtimeSyncStatus_enable_success() {
        when(symbolMapper.selectById(1L)).thenReturn(testSymbol);
        when(marketService.findById(1L)).thenReturn(testMarket);
        when(dataSourceService.findById(1L)).thenReturn(testDataSource);
        when(symbolMapper.updateById(any(Symbol.class))).thenReturn(1);

        SymbolDTO result = symbolService.updateRealtimeSyncStatus(1L, true);

        assertNotNull(result);
        assertTrue(result.getRealtimeSyncEnabled());
        verify(symbolMapper).updateById(any(Symbol.class));
    }

    @Test
    @DisplayName("更新实时同步状态 - 禁用成功")
    void updateRealtimeSyncStatus_disable_success() {
        Symbol enabledSymbol = Symbol.builder()
                .id(1L)
                .marketId(1L)
                .symbol("BTCUSDT")
                .realtimeSyncEnabled(true)
                .historySyncEnabled(false)
                .status("TRADING")
                .build();

        when(symbolMapper.selectById(1L)).thenReturn(enabledSymbol);
        when(marketService.findById(1L)).thenReturn(testMarket);
        when(dataSourceService.findById(1L)).thenReturn(testDataSource);
        when(symbolMapper.updateById(any(Symbol.class))).thenReturn(1);

        SymbolDTO result = symbolService.updateRealtimeSyncStatus(1L, false);

        assertNotNull(result);
        assertFalse(result.getRealtimeSyncEnabled());
        verify(symbolMapper).updateById(any(Symbol.class));
    }


    @Test
    @DisplayName("更新实时同步状态 - 状态相同不更新")
    void updateRealtimeSyncStatus_sameStatus_noUpdate() {
        when(symbolMapper.selectById(1L)).thenReturn(testSymbol);
        when(marketService.findById(1L)).thenReturn(testMarket);
        when(dataSourceService.findById(1L)).thenReturn(testDataSource);

        SymbolDTO result = symbolService.updateRealtimeSyncStatus(1L, false);

        assertNotNull(result);
        assertFalse(result.getRealtimeSyncEnabled());
        verify(symbolMapper, never()).updateById(any(Symbol.class));
    }

    @Test
    @DisplayName("更新实时同步状态 - 市场未启用应抛出异常")
    void updateRealtimeSyncStatus_marketDisabled_shouldThrowException() {
        Market disabledMarket = Market.builder()
                .id(1L)
                .dataSourceId(1L)
                .enabled(false)
                .build();

        when(symbolMapper.selectById(1L)).thenReturn(testSymbol);
        when(marketService.findById(1L)).thenReturn(disabledMarket);

        assertThrows(BusinessException.class, () -> symbolService.updateRealtimeSyncStatus(1L, true));
    }

    @Test
    @DisplayName("更新实时同步状态 - 数据源未启用应抛出异常")
    void updateRealtimeSyncStatus_dataSourceDisabled_shouldThrowException() {
        DataSource disabledDataSource = DataSource.builder()
                .id(1L)
                .enabled(false)
                .build();

        when(symbolMapper.selectById(1L)).thenReturn(testSymbol);
        when(marketService.findById(1L)).thenReturn(testMarket);
        when(dataSourceService.findById(1L)).thenReturn(disabledDataSource);

        assertThrows(BusinessException.class, () -> symbolService.updateRealtimeSyncStatus(1L, true));
    }

    @Test
    @DisplayName("更新历史同步状态 - 启用成功")
    void updateHistorySyncStatus_enable_success() {
        when(symbolMapper.selectById(1L)).thenReturn(testSymbol);
        when(marketService.findById(1L)).thenReturn(testMarket);
        when(dataSourceService.findById(1L)).thenReturn(testDataSource);
        when(symbolMapper.updateById(any(Symbol.class))).thenReturn(1);

        SymbolDTO result = symbolService.updateHistorySyncStatus(1L, true);

        assertNotNull(result);
        assertTrue(result.getHistorySyncEnabled());
        verify(symbolMapper).updateById(any(Symbol.class));
    }

    @Test
    @DisplayName("配置同步周期 - 成功")
    void updateSyncIntervals_success() {
        when(symbolMapper.selectById(1L)).thenReturn(testSymbol);
        when(marketService.findById(1L)).thenReturn(testMarket);
        when(dataSourceService.findById(1L)).thenReturn(testDataSource);
        when(symbolMapper.updateById(any(Symbol.class))).thenReturn(1);

        List<String> intervals = List.of("1m", "5m", "1h", "1d");
        SymbolDTO result = symbolService.updateSyncIntervals(1L, intervals);

        assertNotNull(result);
        assertEquals(intervals, result.getSyncIntervals());
        verify(symbolMapper).updateById(any(Symbol.class));
    }

    @Test
    @DisplayName("配置同步周期 - 不支持的周期应抛出异常")
    void updateSyncIntervals_invalidInterval_shouldThrowException() {
        when(symbolMapper.selectById(1L)).thenReturn(testSymbol);

        List<String> intervals = List.of("1m", "1s", "1h");

        assertThrows(BusinessException.class, () -> symbolService.updateSyncIntervals(1L, intervals));
    }

    @Test
    @DisplayName("配置同步周期 - 空列表成功")
    void updateSyncIntervals_emptyList_success() {
        when(symbolMapper.selectById(1L)).thenReturn(testSymbol);
        when(marketService.findById(1L)).thenReturn(testMarket);
        when(dataSourceService.findById(1L)).thenReturn(testDataSource);
        when(symbolMapper.updateById(any(Symbol.class))).thenReturn(1);

        List<String> intervals = List.of();
        SymbolDTO result = symbolService.updateSyncIntervals(1L, intervals);

        assertNotNull(result);
        assertTrue(result.getSyncIntervals().isEmpty());
        verify(symbolMapper).updateById(any(Symbol.class));
    }


    @Test
    @DisplayName("创建或更新交易对 - 创建新交易对")
    void createOrUpdate_create_success() {
        when(marketService.findById(1L)).thenReturn(testMarket);
        when(symbolMapper.selectByMarketIdAndSymbol(1L, "ETHUSDT")).thenReturn(null);
        when(symbolMapper.insert(any(Symbol.class))).thenAnswer(invocation -> {
            Symbol s = invocation.getArgument(0);
            s.setId(2L);
            s.setCreatedAt(Instant.now());
            s.setUpdatedAt(Instant.now());
            return 1;
        });

        Symbol result = symbolService.createOrUpdate(1L, "ETHUSDT", "ETH", "USDT", 8, 8, "TRADING");

        assertNotNull(result);
        assertEquals("ETHUSDT", result.getSymbol());
        assertEquals("ETH", result.getBaseAsset());
        assertFalse(result.getRealtimeSyncEnabled());  // 默认关闭
        assertFalse(result.getHistorySyncEnabled());   // 默认关闭
        verify(symbolMapper).insert(any(Symbol.class));
    }

    @Test
    @DisplayName("创建或更新交易对 - 更新已存在的交易对")
    void createOrUpdate_update_success() {
        Symbol existingSymbol = Symbol.builder()
                .id(1L)
                .marketId(1L)
                .symbol("BTCUSDT")
                .baseAsset("BTC")
                .quoteAsset("USDT")
                .pricePrecision(6)  // 旧精度
                .quantityPrecision(6)
                .status("TRADING")
                .realtimeSyncEnabled(true)
                .historySyncEnabled(true)
                .build();

        when(marketService.findById(1L)).thenReturn(testMarket);
        when(symbolMapper.selectByMarketIdAndSymbol(1L, "BTCUSDT")).thenReturn(existingSymbol);
        when(symbolMapper.updateById(any(Symbol.class))).thenReturn(1);

        Symbol result = symbolService.createOrUpdate(1L, "BTCUSDT", "BTC", "USDT", 8, 8, "TRADING");

        assertNotNull(result);
        assertEquals(8, result.getPricePrecision());  // 更新后的精度
        assertTrue(result.getRealtimeSyncEnabled());  // 保持原有状态
        verify(symbolMapper).updateById(any(Symbol.class));
        verify(symbolMapper, never()).insert(any(Symbol.class));
    }

    @Test
    @DisplayName("创建或更新交易对 - 无变化不更新")
    void createOrUpdate_noChange_noUpdate() {
        when(marketService.findById(1L)).thenReturn(testMarket);
        when(symbolMapper.selectByMarketIdAndSymbol(1L, "BTCUSDT")).thenReturn(testSymbol);

        Symbol result = symbolService.createOrUpdate(1L, "BTCUSDT", "BTC", "USDT", 8, 8, "TRADING");

        assertNotNull(result);
        verify(symbolMapper, never()).updateById(any(Symbol.class));
        verify(symbolMapper, never()).insert(any(Symbol.class));
    }

    @Test
    @DisplayName("根据市场ID禁用所有交易对同步 - 成功")
    void disableAllSyncByMarketId_success() {
        when(symbolMapper.disableAllSyncByMarketId(1L)).thenReturn(5);

        int result = symbolService.disableAllSyncByMarketId(1L);

        assertEquals(5, result);
        verify(symbolMapper).disableAllSyncByMarketId(1L);
    }

    @Test
    @DisplayName("根据市场ID获取交易对列表 - 成功")
    void getByMarketId_success() {
        when(marketService.findById(1L)).thenReturn(testMarket);
        when(symbolMapper.selectByMarketId(1L)).thenReturn(List.of(testSymbol));
        when(dataSourceService.findById(1L)).thenReturn(testDataSource);

        List<SymbolDTO> result = symbolService.getByMarketId(1L);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("BTCUSDT", result.get(0).getSymbol());
    }

    @Test
    @DisplayName("分页查询 - 成功")
    void list_pagination_success() {
        Page<Symbol> page = new Page<>(1, 10);
        page.setRecords(List.of(testSymbol));
        page.setTotal(1);

        when(symbolMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);
        when(marketService.findById(1L)).thenReturn(testMarket);
        when(dataSourceService.findById(1L)).thenReturn(testDataSource);

        IPage<SymbolDTO> result = symbolService.list(1, 10, null, null, null, null, null);

        assertNotNull(result);
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getRecords().size());
    }

    @Test
    @DisplayName("检查交易对是否存在 - 存在")
    void existsByMarketIdAndSymbol_exists() {
        when(symbolMapper.selectByMarketIdAndSymbol(1L, "BTCUSDT")).thenReturn(testSymbol);

        boolean result = symbolService.existsByMarketIdAndSymbol(1L, "BTCUSDT");

        assertTrue(result);
    }

    @Test
    @DisplayName("检查交易对是否存在 - 不存在")
    void existsByMarketIdAndSymbol_notExists() {
        when(symbolMapper.selectByMarketIdAndSymbol(1L, "ETHUSDT")).thenReturn(null);

        boolean result = symbolService.existsByMarketIdAndSymbol(1L, "ETHUSDT");

        assertFalse(result);
    }

    @Test
    @DisplayName("获取支持的同步周期列表")
    void getValidIntervals_success() {
        var intervals = symbolService.getValidIntervals();

        assertNotNull(intervals);
        assertTrue(intervals.contains("1m"));
        assertTrue(intervals.contains("1h"));
        assertTrue(intervals.contains("1d"));
        assertFalse(intervals.contains("1s"));  // 不支持 1s
    }

    @Test
    @DisplayName("同步交易对 - 成功创建新交易对")
    void syncSymbolsFromBinance_createNew_success() {
        // 准备 Mock 数据
        BinanceClient mockClient = mock(BinanceClient.class);
        
        BinanceExchangeInfo.BinanceSymbol btcSymbol = BinanceExchangeInfo.BinanceSymbol.builder()
                .symbol("BTCUSDT")
                .baseAsset("BTC")
                .quoteAsset("USDT")
                .baseAssetPrecision(8)
                .quotePrecision(8)
                .status("TRADING")
                .build();
        
        BinanceExchangeInfo exchangeInfo = BinanceExchangeInfo.builder()
                .timezone("UTC")
                .serverTime(System.currentTimeMillis())
                .symbols(List.of(btcSymbol))
                .build();
        
        when(marketService.findById(1L)).thenReturn(testMarket);
        when(dataSourceService.findById(1L)).thenReturn(testDataSource);
        when(binanceClientFactory.createClient(testDataSource)).thenReturn(mockClient);
        when(mockClient.testConnection()).thenReturn(
                BinanceClient.ConnectionTestResult.builder()
                        .success(true)
                        .message("OK")
                        .build()
        );
        when(mockClient.getExchangeInfo()).thenReturn(BinanceApiResponse.success(exchangeInfo));
        when(symbolMapper.selectByMarketIdAndSymbol(1L, "BTCUSDT")).thenReturn(null);
        doAnswer(invocation -> {
            Symbol s = invocation.getArgument(0);
            s.setId(1L);
            s.setCreatedAt(Instant.now());
            s.setUpdatedAt(Instant.now());
            return 1;
        }).when(symbolMapper).insert(any(Symbol.class));
        
        SymbolSyncResult result = symbolService.syncSymbolsFromBinance(1L);
        
        assertTrue(result.isSuccess());
        assertEquals(1, result.getSyncedCount());
        assertEquals(1, result.getCreatedCount());
        assertEquals(0, result.getExistingCount());
        
        // 验证新创建的交易对默认关闭同步
        verify(symbolMapper).insert(argThat((Symbol symbol) -> 
                !symbol.getRealtimeSyncEnabled() && !symbol.getHistorySyncEnabled()
        ));
        verify(mockClient).close();
    }

    @Test
    @DisplayName("同步交易对 - 更新已存在的交易对")
    void syncSymbolsFromBinance_updateExisting_success() {
        BinanceClient mockClient = mock(BinanceClient.class);
        
        // 币安返回的新数据（精度变化）
        BinanceExchangeInfo.BinanceSymbol btcSymbol = BinanceExchangeInfo.BinanceSymbol.builder()
                .symbol("BTCUSDT")
                .baseAsset("BTC")
                .quoteAsset("USDT")
                .baseAssetPrecision(8)
                .quotePrecision(6)  // 精度变化
                .status("TRADING")
                .build();
        
        BinanceExchangeInfo exchangeInfo = BinanceExchangeInfo.builder()
                .timezone("UTC")
                .serverTime(System.currentTimeMillis())
                .symbols(List.of(btcSymbol))
                .build();
        
        // 已存在的交易对（旧精度）
        Symbol existingSymbol = Symbol.builder()
                .id(1L)
                .marketId(1L)
                .symbol("BTCUSDT")
                .baseAsset("BTC")
                .quoteAsset("USDT")
                .pricePrecision(8)  // 旧精度
                .quantityPrecision(8)
                .realtimeSyncEnabled(true)  // 已开启同步
                .historySyncEnabled(true)
                .status("TRADING")
                .createdAt(Instant.now())
                .build();
        
        when(marketService.findById(1L)).thenReturn(testMarket);
        when(dataSourceService.findById(1L)).thenReturn(testDataSource);
        when(binanceClientFactory.createClient(testDataSource)).thenReturn(mockClient);
        when(mockClient.testConnection()).thenReturn(
                BinanceClient.ConnectionTestResult.builder()
                        .success(true)
                        .message("OK")
                        .build()
        );
        when(mockClient.getExchangeInfo()).thenReturn(BinanceApiResponse.success(exchangeInfo));
        when(symbolMapper.selectByMarketIdAndSymbol(1L, "BTCUSDT")).thenReturn(existingSymbol);
        when(symbolMapper.updateById(any(Symbol.class))).thenReturn(1);
        
        SymbolSyncResult result = symbolService.syncSymbolsFromBinance(1L);
        
        assertTrue(result.isSuccess());
        assertEquals(1, result.getSyncedCount());
        assertEquals(0, result.getCreatedCount());
        assertEquals(1, result.getUpdatedCount());
        
        // 验证更新时保持原有同步状态
        verify(symbolMapper).updateById(argThat((Symbol symbol) -> 
                symbol.getRealtimeSyncEnabled() && symbol.getHistorySyncEnabled()
        ));
        verify(mockClient).close();
    }

    @Test
    @DisplayName("同步交易对 - 市场未启用应抛出异常")
    void syncSymbolsFromBinance_marketDisabled_shouldThrowException() {
        Market disabledMarket = Market.builder()
                .id(1L)
                .dataSourceId(1L)
                .enabled(false)
                .build();
        
        when(marketService.findById(1L)).thenReturn(disabledMarket);
        
        assertThrows(BusinessException.class, () -> symbolService.syncSymbolsFromBinance(1L));
    }

    @Test
    @DisplayName("同步交易对 - 数据源未启用应抛出异常")
    void syncSymbolsFromBinance_dataSourceDisabled_shouldThrowException() {
        DataSource disabledDataSource = DataSource.builder()
                .id(1L)
                .enabled(false)
                .exchangeType("BINANCE")
                .build();
        
        when(marketService.findById(1L)).thenReturn(testMarket);
        when(dataSourceService.findById(1L)).thenReturn(disabledDataSource);
        
        assertThrows(BusinessException.class, () -> symbolService.syncSymbolsFromBinance(1L));
    }

    @Test
    @DisplayName("同步交易对 - 非币安数据源应抛出异常")
    void syncSymbolsFromBinance_notBinance_shouldThrowException() {
        DataSource okxDataSource = DataSource.builder()
                .id(1L)
                .enabled(true)
                .exchangeType("OKX")
                .build();
        
        when(marketService.findById(1L)).thenReturn(testMarket);
        when(dataSourceService.findById(1L)).thenReturn(okxDataSource);
        
        assertThrows(BusinessException.class, () -> symbolService.syncSymbolsFromBinance(1L));
    }

    @Test
    @DisplayName("同步交易对 - 连接测试失败应抛出异常")
    void syncSymbolsFromBinance_connectionFailed_shouldThrowException() {
        BinanceClient mockClient = mock(BinanceClient.class);
        
        when(marketService.findById(1L)).thenReturn(testMarket);
        when(dataSourceService.findById(1L)).thenReturn(testDataSource);
        when(binanceClientFactory.createClient(testDataSource)).thenReturn(mockClient);
        when(mockClient.testConnection()).thenReturn(
                BinanceClient.ConnectionTestResult.builder()
                        .success(false)
                        .message("Connection timeout")
                        .build()
        );
        
        assertThrows(BusinessException.class, () -> symbolService.syncSymbolsFromBinance(1L));
        verify(mockClient).close();
    }
}
