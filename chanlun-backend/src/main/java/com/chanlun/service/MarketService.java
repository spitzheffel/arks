package com.chanlun.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chanlun.dto.MarketCreateRequest;
import com.chanlun.dto.MarketDTO;
import com.chanlun.dto.MarketSyncResult;
import com.chanlun.entity.DataSource;
import com.chanlun.entity.Market;
import com.chanlun.enums.MarketType;
import com.chanlun.exception.BusinessException;
import com.chanlun.exception.ResourceNotFoundException;
import com.chanlun.exchange.BinanceClient;
import com.chanlun.exchange.BinanceClientFactory;
import com.chanlun.mapper.MarketMapper;
import com.chanlun.mapper.SymbolMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 市场服务
 * 
 * 提供市场的 CRUD 操作，包括：
 * - 查询市场列表（支持按数据源筛选）
 * - 创建市场
 * - 启用/禁用市场（级联禁用交易对同步）
 * - 根据数据源同步市场信息
 * 
 * @author Chanlun Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketService {

    private final MarketMapper marketMapper;
    private final SymbolMapper symbolMapper;
    private final DataSourceService dataSourceService;
    private final BinanceClientFactory binanceClientFactory;

    /**
     * 获取市场列表（分页）
     */
    public IPage<MarketDTO> list(int page, int size, Long dataSourceId, String marketType, Boolean enabled) {
        Page<Market> pageParam = new Page<>(page, size);
        
        LambdaQueryWrapper<Market> wrapper = buildQueryWrapper(dataSourceId, marketType, enabled);
        wrapper.orderByAsc(Market::getDataSourceId)
               .orderByAsc(Market::getMarketType);
        
        IPage<Market> result = marketMapper.selectPage(pageParam, wrapper);
        return result.convert(this::toDTO);
    }

    /**
     * 获取所有市场（不分页）
     */
    public List<MarketDTO> listAll(Long dataSourceId, String marketType, Boolean enabled) {
        LambdaQueryWrapper<Market> wrapper = buildQueryWrapper(dataSourceId, marketType, enabled);
        wrapper.orderByAsc(Market::getDataSourceId)
               .orderByAsc(Market::getMarketType);
        
        return marketMapper.selectList(wrapper).stream()
                .map(this::toDTO)
                .toList();
    }

    /**
     * 根据 ID 获取市场
     */
    public MarketDTO getById(Long id) {
        Market market = findById(id);
        return toDTO(market);
    }

    /**
     * 根据数据源ID获取市场列表
     */
    public List<MarketDTO> getByDataSourceId(Long dataSourceId) {
        // 验证数据源存在
        dataSourceService.findById(dataSourceId);
        
        List<Market> markets = marketMapper.selectByDataSourceId(dataSourceId);
        return markets.stream()
                .map(this::toDTO)
                .toList();
    }

    /**
     * 根据数据源ID获取启用的市场列表
     */
    public List<MarketDTO> getEnabledByDataSourceId(Long dataSourceId) {
        // 验证数据源存在
        dataSourceService.findById(dataSourceId);
        
        List<Market> markets = marketMapper.selectEnabledByDataSourceId(dataSourceId);
        return markets.stream()
                .map(this::toDTO)
                .toList();
    }

    /**
     * 创建市场
     */
    @Transactional
    public MarketDTO create(MarketCreateRequest request) {
        // 验证数据源存在且未删除
        DataSource dataSource = dataSourceService.findById(request.getDataSourceId());
        
        // 校验市场类型
        validateMarketType(request.getMarketType());
        
        // 检查同一数据源下市场类型是否重复
        if (marketMapper.countByDataSourceIdAndMarketTypeExcludeId(
                request.getDataSourceId(), request.getMarketType(), null) > 0) {
            throw new BusinessException("该数据源下已存在相同类型的市场: " + request.getMarketType());
        }

        Market market = Market.builder()
                .dataSourceId(request.getDataSourceId())
                .name(request.getName())
                .marketType(request.getMarketType())
                .enabled(true)
                .build();

        marketMapper.insert(market);
        log.info("Created market: id={}, dataSourceId={}, marketType={}", 
                market.getId(), market.getDataSourceId(), market.getMarketType());
        
        return toDTO(market, dataSource.getName());
    }

    /**
     * 批量创建市场（用于同步）
     */
    @Transactional
    public List<MarketDTO> createBatch(Long dataSourceId, List<MarketCreateRequest> requests) {
        // 验证数据源存在
        DataSource dataSource = dataSourceService.findById(dataSourceId);
        
        return requests.stream()
                .map(request -> {
                    request.setDataSourceId(dataSourceId);
                    return createIfNotExists(request, dataSource.getName());
                })
                .toList();
    }

    /**
     * 更新市场状态（启用/禁用）
     * 
     * 禁用市场时会级联禁用该市场下所有交易对的同步（实时+历史）
     */
    @Transactional
    public MarketDTO updateStatus(Long id, boolean enabled) {
        Market market = findById(id);
        
        if (market.getEnabled() == enabled) {
            return toDTO(market);
        }

        market.setEnabled(enabled);
        market.setUpdatedAt(Instant.now());
        marketMapper.updateById(market);
        
        log.info("{} market: id={}, marketType={}", enabled ? "Enabled" : "Disabled", 
                market.getId(), market.getMarketType());

        // 如果禁用市场，级联禁用该市场下所有交易对的同步
        if (!enabled) {
            int disabledCount = cascadeDisableSymbolSync(id);
            if (disabledCount > 0) {
                log.info("Cascade disabled {} symbol(s) sync for market: id={}", disabledCount, id);
            }
        }
        
        return toDTO(market);
    }

    /**
     * 级联禁用市场下所有交易对的同步
     * 
     * @param marketId 市场ID
     * @return 被禁用的交易对数量
     */
    private int cascadeDisableSymbolSync(Long marketId) {
        return symbolMapper.disableAllSyncByMarketId(marketId);
    }

    /**
     * 根据数据源ID禁用所有市场（级联禁用）
     * 
     * 同时会级联禁用所有交易对的同步
     */
    @Transactional
    public void disableByDataSourceId(Long dataSourceId) {
        List<Market> markets = marketMapper.selectByDataSourceId(dataSourceId);
        
        for (Market market : markets) {
            if (market.getEnabled()) {
                market.setEnabled(false);
                market.setUpdatedAt(Instant.now());
                marketMapper.updateById(market);
                log.info("Cascade disabled market: id={}, marketType={}", 
                        market.getId(), market.getMarketType());
                
                // 级联禁用该市场下所有交易对的同步
                int disabledCount = cascadeDisableSymbolSync(market.getId());
                if (disabledCount > 0) {
                    log.info("Cascade disabled {} symbol(s) sync for market: id={}", 
                            disabledCount, market.getId());
                }
            }
        }
    }

    /**
     * 从币安同步市场信息
     * 
     * 根据数据源的 baseUrl 判断市场类型，并创建对应的市场记录。
     * 币安支持三种市场类型：
     * - 现货 (SPOT): https://api.binance.com
     * - U本位合约 (USDT_M): https://fapi.binance.com
     * - 币本位合约 (COIN_M): https://dapi.binance.com
     * 
     * @param dataSourceId 数据源ID
     * @return 同步结果
     */
    @Transactional
    public MarketSyncResult syncMarketsFromBinance(Long dataSourceId) {
        // 验证数据源存在且未删除
        DataSource dataSource = dataSourceService.findById(dataSourceId);
        
        // 验证数据源已启用
        if (!dataSource.getEnabled()) {
            throw new BusinessException("数据源未启用，无法同步市场信息");
        }
        
        // 验证是币安数据源
        if (!"BINANCE".equalsIgnoreCase(dataSource.getExchangeType())) {
            throw new BusinessException("仅支持币安数据源同步市场信息");
        }
        
        // 创建币安客户端并测试连接
        BinanceClient client = binanceClientFactory.createClient(dataSource);
        try {
            // 测试连接
            BinanceClient.ConnectionTestResult testResult = client.testConnection();
            if (!testResult.isSuccess()) {
                throw new BusinessException("数据源连接测试失败: " + testResult.getMessage());
            }
            
            // 根据 baseUrl 判断市场类型
            List<MarketType> marketTypes = detectMarketTypes(dataSource.getBaseUrl());
            
            if (marketTypes.isEmpty()) {
                throw new BusinessException("无法识别数据源的市场类型，请检查 baseUrl 配置");
            }
            
            // 同步市场
            List<MarketDTO> syncedMarkets = new ArrayList<>();
            int createdCount = 0;
            int existingCount = 0;
            
            for (MarketType marketType : marketTypes) {
                SyncMarketResult result = syncSingleMarket(dataSource, marketType);
                syncedMarkets.add(result.market());
                if (result.created()) {
                    createdCount++;
                } else {
                    existingCount++;
                }
            }
            
            log.info("Synced markets for data source {}: total={}, created={}, existing={}", 
                    dataSource.getName(), syncedMarkets.size(), createdCount, existingCount);
            
            return MarketSyncResult.builder()
                    .success(true)
                    .message("市场同步成功")
                    .syncedCount(syncedMarkets.size())
                    .createdCount(createdCount)
                    .existingCount(existingCount)
                    .markets(syncedMarkets)
                    .build();
                    
        } finally {
            client.close();
        }
    }
    
    /**
     * 根据 baseUrl 检测市场类型
     */
    private List<MarketType> detectMarketTypes(String baseUrl) {
        List<MarketType> types = new ArrayList<>();
        
        if (!StringUtils.hasText(baseUrl)) {
            // 默认为现货市场
            types.add(MarketType.SPOT);
            return types;
        }
        
        String url = baseUrl.toLowerCase();
        
        // 注意：检测顺序很重要，fapi 和 dapi 需要在 api 之前检测
        // 因为 fapi.binance.com 和 dapi.binance.com 都包含 api.binance.com
        if (url.contains("fapi.binance.com")) {
            types.add(MarketType.USDT_M);
        } else if (url.contains("dapi.binance.com")) {
            types.add(MarketType.COIN_M);
        } else if (url.contains("api.binance.com")) {
            types.add(MarketType.SPOT);
        } else {
            // 无法识别，默认为现货
            log.warn("Unknown baseUrl pattern: {}, defaulting to SPOT market", baseUrl);
            types.add(MarketType.SPOT);
        }
        
        return types;
    }
    
    /**
     * 同步单个市场
     */
    private SyncMarketResult syncSingleMarket(DataSource dataSource, MarketType marketType) {
        // 检查是否已存在
        Market existing = marketMapper.selectByDataSourceIdAndMarketType(
                dataSource.getId(), marketType.getCode());
        
        if (existing != null) {
            log.debug("Market already exists: dataSourceId={}, marketType={}", 
                    dataSource.getId(), marketType.getCode());
            return new SyncMarketResult(toDTO(existing, dataSource.getName()), false);
        }
        
        // 创建新市场
        Market market = Market.builder()
                .dataSourceId(dataSource.getId())
                .name(marketType.getDescription())
                .marketType(marketType.getCode())
                .enabled(true)
                .build();
        
        marketMapper.insert(market);
        log.info("Created market from sync: id={}, dataSourceId={}, marketType={}", 
                market.getId(), dataSource.getId(), marketType.getCode());
        
        return new SyncMarketResult(toDTO(market, dataSource.getName()), true);
    }
    
    /**
     * 同步市场结果记录
     */
    private record SyncMarketResult(MarketDTO market, boolean created) {}

    /**
     * 根据数据源ID和市场类型查询市场
     */
    public MarketDTO getByDataSourceIdAndMarketType(Long dataSourceId, String marketType) {
        Market market = marketMapper.selectByDataSourceIdAndMarketType(dataSourceId, marketType);
        if (market == null) {
            throw new ResourceNotFoundException("市场不存在: dataSourceId=" + dataSourceId + ", marketType=" + marketType);
        }
        return toDTO(market);
    }

    /**
     * 检查市场是否存在
     */
    public boolean existsByDataSourceIdAndMarketType(Long dataSourceId, String marketType) {
        return marketMapper.selectByDataSourceIdAndMarketType(dataSourceId, marketType) != null;
    }

    /**
     * 根据 ID 查找市场实体
     */
    public Market findById(Long id) {
        Market market = marketMapper.selectById(id);
        if (market == null) {
            throw new ResourceNotFoundException("市场不存在: " + id);
        }
        return market;
    }

    /**
     * 创建市场（如果不存在）
     */
    private MarketDTO createIfNotExists(MarketCreateRequest request, String dataSourceName) {
        // 校验市场类型
        validateMarketType(request.getMarketType());
        
        // 检查是否已存在
        Market existing = marketMapper.selectByDataSourceIdAndMarketType(
                request.getDataSourceId(), request.getMarketType());
        
        if (existing != null) {
            log.debug("Market already exists: dataSourceId={}, marketType={}", 
                    request.getDataSourceId(), request.getMarketType());
            return toDTO(existing, dataSourceName);
        }

        Market market = Market.builder()
                .dataSourceId(request.getDataSourceId())
                .name(request.getName())
                .marketType(request.getMarketType())
                .enabled(true)
                .build();

        marketMapper.insert(market);
        log.info("Created market: id={}, dataSourceId={}, marketType={}", 
                market.getId(), market.getDataSourceId(), market.getMarketType());
        
        return toDTO(market, dataSourceName);
    }

    /**
     * 构建查询条件
     */
    private LambdaQueryWrapper<Market> buildQueryWrapper(Long dataSourceId, String marketType, Boolean enabled) {
        LambdaQueryWrapper<Market> wrapper = new LambdaQueryWrapper<>();
        
        if (dataSourceId != null) {
            wrapper.eq(Market::getDataSourceId, dataSourceId);
        }
        if (StringUtils.hasText(marketType)) {
            wrapper.eq(Market::getMarketType, marketType);
        }
        if (enabled != null) {
            wrapper.eq(Market::getEnabled, enabled);
        }
        
        return wrapper;
    }

    /**
     * 转换为 DTO
     */
    private MarketDTO toDTO(Market entity) {
        String dataSourceName = null;
        try {
            DataSource dataSource = dataSourceService.findById(entity.getDataSourceId());
            dataSourceName = dataSource.getName();
        } catch (ResourceNotFoundException e) {
            // 数据源可能已被删除，忽略
        }
        return toDTO(entity, dataSourceName);
    }

    /**
     * 转换为 DTO（带数据源名称）
     */
    private MarketDTO toDTO(Market entity, String dataSourceName) {
        return MarketDTO.builder()
                .id(entity.getId())
                .dataSourceId(entity.getDataSourceId())
                .dataSourceName(dataSourceName)
                .name(entity.getName())
                .marketType(entity.getMarketType())
                .enabled(entity.getEnabled())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /**
     * 校验市场类型
     */
    private void validateMarketType(String marketType) {
        try {
            MarketType.fromCode(marketType);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("不支持的市场类型: " + marketType);
        }
    }
}
