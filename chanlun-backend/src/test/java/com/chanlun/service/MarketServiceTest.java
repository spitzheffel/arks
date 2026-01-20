package com.chanlun.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chanlun.dto.MarketCreateRequest;
import com.chanlun.dto.MarketDTO;
import com.chanlun.dto.MarketSyncResult;
import com.chanlun.entity.DataSource;
import com.chanlun.entity.Market;
import com.chanlun.exception.BusinessException;
import com.chanlun.exception.ResourceNotFoundException;
import com.chanlun.exchange.BinanceClient;
import com.chanlun.exchange.BinanceClientFactory;
import com.chanlun.mapper.MarketMapper;
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
 * 市场服务测试
 * 
 * @author Chanlun Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MarketService 测试")
class MarketServiceTest {

    @Mock
    private MarketMapper marketMapper;

    @Mock
    private SymbolMapper symbolMapper;

    @Mock
    private DataSourceService dataSourceService;

    @Mock
    private BinanceClientFactory binanceClientFactory;

    @Mock
    private BinanceClient binanceClient;

    @InjectMocks
    private MarketService marketService;

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
    }

    @Test
    @DisplayName("创建市场 - 成功")
    void create_success() {
        MarketCreateRequest request = MarketCreateRequest.builder()
                .dataSourceId(1L)
                .name("现货市场")
                .marketType("SPOT")
                .build();

        when(dataSourceService.findById(1L)).thenReturn(testDataSource);
        when(marketMapper.countByDataSourceIdAndMarketTypeExcludeId(1L, "SPOT", null)).thenReturn(0);
        when(marketMapper.insert(any(Market.class))).thenAnswer(invocation -> {
            Market m = invocation.getArgument(0);
            m.setId(1L);
            m.setCreatedAt(Instant.now());
            m.setUpdatedAt(Instant.now());
            return 1;
        });

        MarketDTO result = marketService.create(request);

        assertNotNull(result);
        assertEquals("现货市场", result.getName());
        assertEquals("SPOT", result.getMarketType());
        assertEquals(1L, result.getDataSourceId());
        assertEquals("Test Binance", result.getDataSourceName());
        verify(marketMapper).insert(any(Market.class));
    }

    @Test
    @DisplayName("创建市场 - 数据源不存在应抛出异常")
    void create_dataSourceNotFound_shouldThrowException() {
        MarketCreateRequest request = MarketCreateRequest.builder()
                .dataSourceId(999L)
                .name("现货市场")
                .marketType("SPOT")
                .build();

        when(dataSourceService.findById(999L)).thenThrow(new ResourceNotFoundException("数据源不存在: 999"));

        assertThrows(ResourceNotFoundException.class, () -> marketService.create(request));
    }

    @Test
    @DisplayName("创建市场 - 市场类型重复应抛出异常")
    void create_duplicateMarketType_shouldThrowException() {
        MarketCreateRequest request = MarketCreateRequest.builder()
                .dataSourceId(1L)
                .name("现货市场")
                .marketType("SPOT")
                .build();

        when(dataSourceService.findById(1L)).thenReturn(testDataSource);
        when(marketMapper.countByDataSourceIdAndMarketTypeExcludeId(1L, "SPOT", null)).thenReturn(1);

        assertThrows(BusinessException.class, () -> marketService.create(request));
    }

    @Test
    @DisplayName("创建市场 - 不支持的市场类型应抛出异常")
    void create_invalidMarketType_shouldThrowException() {
        MarketCreateRequest request = MarketCreateRequest.builder()
                .dataSourceId(1L)
                .name("测试市场")
                .marketType("INVALID")
                .build();

        when(dataSourceService.findById(1L)).thenReturn(testDataSource);

        assertThrows(BusinessException.class, () -> marketService.create(request));
    }

    @Test
    @DisplayName("获取市场 - 成功")
    void getById_success() {
        when(marketMapper.selectById(1L)).thenReturn(testMarket);
        when(dataSourceService.findById(1L)).thenReturn(testDataSource);

        MarketDTO result = marketService.getById(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("现货市场", result.getName());
        assertEquals("SPOT", result.getMarketType());
    }

    @Test
    @DisplayName("获取市场 - 不存在应抛出异常")
    void getById_notFound_shouldThrowException() {
        when(marketMapper.selectById(999L)).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> marketService.getById(999L));
    }

    @Test
    @DisplayName("更新市场状态 - 禁用成功并级联禁用交易对同步")
    void updateStatus_disable_success() {
        when(marketMapper.selectById(1L)).thenReturn(testMarket);
        when(marketMapper.updateById(any(Market.class))).thenReturn(1);
        when(dataSourceService.findById(1L)).thenReturn(testDataSource);
        when(symbolMapper.disableAllSyncByMarketId(1L)).thenReturn(3);

        MarketDTO result = marketService.updateStatus(1L, false);

        assertNotNull(result);
        assertFalse(result.getEnabled());
        verify(marketMapper).updateById(any(Market.class));
        verify(symbolMapper).disableAllSyncByMarketId(1L);
    }

    @Test
    @DisplayName("更新市场状态 - 启用成功不触发级联禁用")
    void updateStatus_enable_success() {
        Market disabledMarket = Market.builder()
                .id(1L)
                .dataSourceId(1L)
                .name("现货市场")
                .marketType("SPOT")
                .enabled(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(marketMapper.selectById(1L)).thenReturn(disabledMarket);
        when(marketMapper.updateById(any(Market.class))).thenReturn(1);
        when(dataSourceService.findById(1L)).thenReturn(testDataSource);

        MarketDTO result = marketService.updateStatus(1L, true);

        assertNotNull(result);
        assertTrue(result.getEnabled());
        verify(marketMapper).updateById(any(Market.class));
        verify(symbolMapper, never()).disableAllSyncByMarketId(anyLong());
    }

    @Test
    @DisplayName("更新市场状态 - 状态相同不更新")
    void updateStatus_sameStatus_noUpdate() {
        when(marketMapper.selectById(1L)).thenReturn(testMarket);
        when(dataSourceService.findById(1L)).thenReturn(testDataSource);

        MarketDTO result = marketService.updateStatus(1L, true);

        assertNotNull(result);
        assertTrue(result.getEnabled());
        verify(marketMapper, never()).updateById(any(Market.class));
    }

    @Test
    @DisplayName("根据数据源ID获取市场列表 - 成功")
    void getByDataSourceId_success() {
        when(dataSourceService.findById(1L)).thenReturn(testDataSource);
        when(marketMapper.selectByDataSourceId(1L)).thenReturn(List.of(testMarket));

        List<MarketDTO> result = marketService.getByDataSourceId(1L);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("SPOT", result.get(0).getMarketType());
    }

    @Test
    @DisplayName("根据数据源ID禁用所有市场 - 成功并级联禁用交易对同步")
    void disableByDataSourceId_success() {
        Market market1 = Market.builder()
                .id(1L)
                .dataSourceId(1L)
                .marketType("SPOT")
                .enabled(true)
                .build();
        Market market2 = Market.builder()
                .id(2L)
                .dataSourceId(1L)
                .marketType("USDT_M")
                .enabled(true)
                .build();

        when(marketMapper.selectByDataSourceId(1L)).thenReturn(List.of(market1, market2));
        when(marketMapper.updateById(any(Market.class))).thenReturn(1);
        when(symbolMapper.disableAllSyncByMarketId(1L)).thenReturn(5);
        when(symbolMapper.disableAllSyncByMarketId(2L)).thenReturn(3);

        marketService.disableByDataSourceId(1L);

        verify(marketMapper, times(2)).updateById(any(Market.class));
        verify(symbolMapper).disableAllSyncByMarketId(1L);
        verify(symbolMapper).disableAllSyncByMarketId(2L);
    }

    @Test
    @DisplayName("根据数据源ID禁用所有市场 - 已禁用的市场不重复处理")
    void disableByDataSourceId_alreadyDisabled_noUpdate() {
        Market disabledMarket = Market.builder()
                .id(1L)
                .dataSourceId(1L)
                .marketType("SPOT")
                .enabled(false)
                .build();

        when(marketMapper.selectByDataSourceId(1L)).thenReturn(List.of(disabledMarket));

        marketService.disableByDataSourceId(1L);

        verify(marketMapper, never()).updateById(any(Market.class));
        verify(symbolMapper, never()).disableAllSyncByMarketId(anyLong());
    }

    @Test
    @DisplayName("分页查询 - 成功")
    void list_pagination_success() {
        Page<Market> page = new Page<>(1, 10);
        page.setRecords(List.of(testMarket));
        page.setTotal(1);

        when(marketMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);
        when(dataSourceService.findById(1L)).thenReturn(testDataSource);

        IPage<MarketDTO> result = marketService.list(1, 10, null, null, null);

        assertNotNull(result);
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getRecords().size());
    }

    @Test
    @DisplayName("检查市场是否存在 - 存在")
    void existsByDataSourceIdAndMarketType_exists() {
        when(marketMapper.selectByDataSourceIdAndMarketType(1L, "SPOT")).thenReturn(testMarket);

        boolean result = marketService.existsByDataSourceIdAndMarketType(1L, "SPOT");

        assertTrue(result);
    }

    @Test
    @DisplayName("检查市场是否存在 - 不存在")
    void existsByDataSourceIdAndMarketType_notExists() {
        when(marketMapper.selectByDataSourceIdAndMarketType(1L, "COIN_M")).thenReturn(null);

        boolean result = marketService.existsByDataSourceIdAndMarketType(1L, "COIN_M");

        assertFalse(result);
    }

    @Test
    @DisplayName("同步市场 - 现货市场成功")
    void syncMarketsFromBinance_spot_success() {
        DataSource spotDataSource = DataSource.builder()
                .id(1L)
                .name("Binance Spot")
                .exchangeType("BINANCE")
                .baseUrl("https://api.binance.com")
                .enabled(true)
                .deleted(false)
                .build();

        BinanceClient.ConnectionTestResult testResult = BinanceClient.ConnectionTestResult.builder()
                .success(true)
                .message("Connection successful")
                .latencyMs(50L)
                .build();

        when(dataSourceService.findById(1L)).thenReturn(spotDataSource);
        when(binanceClientFactory.createClient(spotDataSource)).thenReturn(binanceClient);
        when(binanceClient.testConnection()).thenReturn(testResult);
        when(marketMapper.selectByDataSourceIdAndMarketType(1L, "SPOT")).thenReturn(null);
        when(marketMapper.insert(any(Market.class))).thenAnswer(invocation -> {
            Market m = invocation.getArgument(0);
            m.setId(1L);
            m.setCreatedAt(Instant.now());
            m.setUpdatedAt(Instant.now());
            return 1;
        });

        MarketSyncResult result = marketService.syncMarketsFromBinance(1L);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(1, result.getSyncedCount());
        assertEquals(1, result.getCreatedCount());
        assertEquals(0, result.getExistingCount());
        assertEquals(1, result.getMarkets().size());
        assertEquals("SPOT", result.getMarkets().get(0).getMarketType());
        verify(binanceClient).close();
    }

    @Test
    @DisplayName("同步市场 - U本位合约市场成功")
    void syncMarketsFromBinance_usdtM_success() {
        DataSource futuresDataSource = DataSource.builder()
                .id(2L)
                .name("Binance Futures")
                .exchangeType("BINANCE")
                .baseUrl("https://fapi.binance.com")
                .enabled(true)
                .deleted(false)
                .build();

        BinanceClient.ConnectionTestResult testResult = BinanceClient.ConnectionTestResult.builder()
                .success(true)
                .message("Connection successful")
                .latencyMs(50L)
                .build();

        when(dataSourceService.findById(2L)).thenReturn(futuresDataSource);
        when(binanceClientFactory.createClient(futuresDataSource)).thenReturn(binanceClient);
        when(binanceClient.testConnection()).thenReturn(testResult);
        when(marketMapper.selectByDataSourceIdAndMarketType(2L, "USDT_M")).thenReturn(null);
        when(marketMapper.insert(any(Market.class))).thenAnswer(invocation -> {
            Market m = invocation.getArgument(0);
            m.setId(2L);
            m.setCreatedAt(Instant.now());
            m.setUpdatedAt(Instant.now());
            return 1;
        });

        MarketSyncResult result = marketService.syncMarketsFromBinance(2L);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(1, result.getSyncedCount());
        assertEquals("USDT_M", result.getMarkets().get(0).getMarketType());
        verify(binanceClient).close();
    }

    @Test
    @DisplayName("同步市场 - 币本位合约市场成功")
    void syncMarketsFromBinance_coinM_success() {
        DataSource coinFuturesDataSource = DataSource.builder()
                .id(3L)
                .name("Binance Coin Futures")
                .exchangeType("BINANCE")
                .baseUrl("https://dapi.binance.com")
                .enabled(true)
                .deleted(false)
                .build();

        BinanceClient.ConnectionTestResult testResult = BinanceClient.ConnectionTestResult.builder()
                .success(true)
                .message("Connection successful")
                .latencyMs(50L)
                .build();

        when(dataSourceService.findById(3L)).thenReturn(coinFuturesDataSource);
        when(binanceClientFactory.createClient(coinFuturesDataSource)).thenReturn(binanceClient);
        when(binanceClient.testConnection()).thenReturn(testResult);
        when(marketMapper.selectByDataSourceIdAndMarketType(3L, "COIN_M")).thenReturn(null);
        when(marketMapper.insert(any(Market.class))).thenAnswer(invocation -> {
            Market m = invocation.getArgument(0);
            m.setId(3L);
            m.setCreatedAt(Instant.now());
            m.setUpdatedAt(Instant.now());
            return 1;
        });

        MarketSyncResult result = marketService.syncMarketsFromBinance(3L);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(1, result.getSyncedCount());
        assertEquals("COIN_M", result.getMarkets().get(0).getMarketType());
        verify(binanceClient).close();
    }

    @Test
    @DisplayName("同步市场 - 市场已存在")
    void syncMarketsFromBinance_marketExists() {
        DataSource spotDataSource = DataSource.builder()
                .id(1L)
                .name("Binance Spot")
                .exchangeType("BINANCE")
                .baseUrl("https://api.binance.com")
                .enabled(true)
                .deleted(false)
                .build();

        Market existingMarket = Market.builder()
                .id(1L)
                .dataSourceId(1L)
                .name("现货")
                .marketType("SPOT")
                .enabled(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        BinanceClient.ConnectionTestResult testResult = BinanceClient.ConnectionTestResult.builder()
                .success(true)
                .message("Connection successful")
                .latencyMs(50L)
                .build();

        when(dataSourceService.findById(1L)).thenReturn(spotDataSource);
        when(binanceClientFactory.createClient(spotDataSource)).thenReturn(binanceClient);
        when(binanceClient.testConnection()).thenReturn(testResult);
        when(marketMapper.selectByDataSourceIdAndMarketType(1L, "SPOT")).thenReturn(existingMarket);

        MarketSyncResult result = marketService.syncMarketsFromBinance(1L);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(1, result.getSyncedCount());
        assertEquals(0, result.getCreatedCount());
        assertEquals(1, result.getExistingCount());
        verify(marketMapper, never()).insert(any(Market.class));
        verify(binanceClient).close();
    }

    @Test
    @DisplayName("同步市场 - 数据源未启用应抛出异常")
    void syncMarketsFromBinance_dataSourceDisabled_shouldThrowException() {
        DataSource disabledDataSource = DataSource.builder()
                .id(1L)
                .name("Binance Spot")
                .exchangeType("BINANCE")
                .baseUrl("https://api.binance.com")
                .enabled(false)
                .deleted(false)
                .build();

        when(dataSourceService.findById(1L)).thenReturn(disabledDataSource);

        BusinessException exception = assertThrows(BusinessException.class, 
                () -> marketService.syncMarketsFromBinance(1L));
        assertEquals("数据源未启用，无法同步市场信息", exception.getMessage());
    }

    @Test
    @DisplayName("同步市场 - 非币安数据源应抛出异常")
    void syncMarketsFromBinance_notBinance_shouldThrowException() {
        DataSource okxDataSource = DataSource.builder()
                .id(1L)
                .name("OKX")
                .exchangeType("OKX")
                .enabled(true)
                .deleted(false)
                .build();

        when(dataSourceService.findById(1L)).thenReturn(okxDataSource);

        BusinessException exception = assertThrows(BusinessException.class, 
                () -> marketService.syncMarketsFromBinance(1L));
        assertEquals("仅支持币安数据源同步市场信息", exception.getMessage());
    }

    @Test
    @DisplayName("同步市场 - 连接测试失败应抛出异常")
    void syncMarketsFromBinance_connectionFailed_shouldThrowException() {
        DataSource spotDataSource = DataSource.builder()
                .id(1L)
                .name("Binance Spot")
                .exchangeType("BINANCE")
                .baseUrl("https://api.binance.com")
                .enabled(true)
                .deleted(false)
                .build();

        BinanceClient.ConnectionTestResult testResult = BinanceClient.ConnectionTestResult.builder()
                .success(false)
                .message("Connection timeout")
                .latencyMs(10000L)
                .build();

        when(dataSourceService.findById(1L)).thenReturn(spotDataSource);
        when(binanceClientFactory.createClient(spotDataSource)).thenReturn(binanceClient);
        when(binanceClient.testConnection()).thenReturn(testResult);

        BusinessException exception = assertThrows(BusinessException.class, 
                () -> marketService.syncMarketsFromBinance(1L));
        assertTrue(exception.getMessage().contains("数据源连接测试失败"));
        verify(binanceClient).close();
    }
}
