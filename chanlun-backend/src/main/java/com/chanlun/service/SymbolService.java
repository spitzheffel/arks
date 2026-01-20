package com.chanlun.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 交易对服务
 * 
 * 提供交易对的 CRUD 操作，包括：
 * - 查询交易对列表（支持按市场、数据源、关键词筛选）
 * - 开启/关闭实时同步
 * - 开启/关闭历史同步
 * - 配置同步周期
 * - 从交易所同步交易对列表
 * 
 * @author Chanlun Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SymbolService {

    private final SymbolMapper symbolMapper;
    private final MarketService marketService;
    private final DataSourceService dataSourceService;
    private final BinanceClientFactory binanceClientFactory;

    /**
     * 支持的同步周期列表（不支持 1s）
     */
    private static final Set<String> VALID_INTERVALS = Set.of(
            "1m", "3m", "5m", "15m", "30m",
            "1h", "2h", "4h", "6h", "8h", "12h",
            "1d", "3d", "1w", "1M"
    );

    /**
     * 获取交易对列表（分页）
     */
    public IPage<SymbolDTO> list(int page, int size, Long marketId, Long dataSourceId,
                                  String keyword, Boolean realtimeSyncEnabled, Boolean historySyncEnabled) {
        Page<Symbol> pageParam = new Page<>(page, size);
        
        LambdaQueryWrapper<Symbol> wrapper = buildQueryWrapper(marketId, dataSourceId, keyword, 
                realtimeSyncEnabled, historySyncEnabled);
        wrapper.orderByAsc(Symbol::getMarketId)
               .orderByAsc(Symbol::getSymbol);
        
        IPage<Symbol> result = symbolMapper.selectPage(pageParam, wrapper);
        return result.convert(this::toDTO);
    }


    /**
     * 获取所有交易对（不分页）
     */
    public List<SymbolDTO> listAll(Long marketId, Long dataSourceId, String keyword,
                                    Boolean realtimeSyncEnabled, Boolean historySyncEnabled) {
        LambdaQueryWrapper<Symbol> wrapper = buildQueryWrapper(marketId, dataSourceId, keyword,
                realtimeSyncEnabled, historySyncEnabled);
        wrapper.orderByAsc(Symbol::getMarketId)
               .orderByAsc(Symbol::getSymbol);
        
        return symbolMapper.selectList(wrapper).stream()
                .map(this::toDTO)
                .toList();
    }

    /**
     * 根据 ID 获取交易对
     */
    public SymbolDTO getById(Long id) {
        Symbol symbol = findById(id);
        return toDTO(symbol);
    }

    /**
     * 根据市场ID获取交易对列表
     */
    public List<SymbolDTO> getByMarketId(Long marketId) {
        // 验证市场存在
        marketService.findById(marketId);
        
        List<Symbol> symbols = symbolMapper.selectByMarketId(marketId);
        return symbols.stream()
                .map(this::toDTO)
                .toList();
    }

    /**
     * 更新实时同步状态
     * 
     * @param id 交易对ID
     * @param enabled 是否启用
     * @return 更新后的交易对
     */
    @Transactional
    public SymbolDTO updateRealtimeSyncStatus(Long id, boolean enabled) {
        Symbol symbol = findById(id);
        
        // 验证市场和数据源状态
        if (enabled) {
            validateMarketAndDataSourceEnabled(symbol.getMarketId());
        }
        
        if (symbol.getRealtimeSyncEnabled() == enabled) {
            return toDTO(symbol);
        }

        symbol.setRealtimeSyncEnabled(enabled);
        symbol.setUpdatedAt(Instant.now());
        symbolMapper.updateById(symbol);
        
        log.info("{} realtime sync for symbol: id={}, symbol={}", 
                enabled ? "Enabled" : "Disabled", symbol.getId(), symbol.getSymbol());
        
        return toDTO(symbol);
    }

    /**
     * 更新历史同步状态
     * 
     * @param id 交易对ID
     * @param enabled 是否启用
     * @return 更新后的交易对
     */
    @Transactional
    public SymbolDTO updateHistorySyncStatus(Long id, boolean enabled) {
        Symbol symbol = findById(id);
        
        // 验证市场和数据源状态
        if (enabled) {
            validateMarketAndDataSourceEnabled(symbol.getMarketId());
        }
        
        if (symbol.getHistorySyncEnabled() == enabled) {
            return toDTO(symbol);
        }

        symbol.setHistorySyncEnabled(enabled);
        symbol.setUpdatedAt(Instant.now());
        symbolMapper.updateById(symbol);
        
        log.info("{} history sync for symbol: id={}, symbol={}", 
                enabled ? "Enabled" : "Disabled", symbol.getId(), symbol.getSymbol());
        
        return toDTO(symbol);
    }

    /**
     * 配置同步周期
     * 
     * @param id 交易对ID
     * @param intervals 同步周期列表
     * @return 更新后的交易对
     */
    @Transactional
    public SymbolDTO updateSyncIntervals(Long id, List<String> intervals) {
        Symbol symbol = findById(id);
        
        // 校验周期合法性
        validateIntervals(intervals);
        
        String intervalsStr = intervals.isEmpty() ? null : String.join(",", intervals);
        symbol.setSyncIntervals(intervalsStr);
        symbol.setUpdatedAt(Instant.now());
        symbolMapper.updateById(symbol);
        
        log.info("Updated sync intervals for symbol: id={}, symbol={}, intervals={}", 
                symbol.getId(), symbol.getSymbol(), intervalsStr);
        
        return toDTO(symbol);
    }


    /**
     * 根据市场ID禁用所有交易对的实时同步
     * 
     * @param marketId 市场ID
     * @return 被禁用的交易对数量
     */
    @Transactional
    public int disableRealtimeSyncByMarketId(Long marketId) {
        int count = symbolMapper.disableRealtimeSyncByMarketId(marketId);
        if (count > 0) {
            log.info("Disabled realtime sync for {} symbol(s) in market: {}", count, marketId);
        }
        return count;
    }

    /**
     * 根据市场ID禁用所有交易对的历史同步
     * 
     * @param marketId 市场ID
     * @return 被禁用的交易对数量
     */
    @Transactional
    public int disableHistorySyncByMarketId(Long marketId) {
        int count = symbolMapper.disableHistorySyncByMarketId(marketId);
        if (count > 0) {
            log.info("Disabled history sync for {} symbol(s) in market: {}", count, marketId);
        }
        return count;
    }

    /**
     * 根据市场ID禁用所有交易对的同步（实时+历史）
     * 
     * @param marketId 市场ID
     * @return 被禁用的交易对数量
     */
    @Transactional
    public int disableAllSyncByMarketId(Long marketId) {
        int count = symbolMapper.disableAllSyncByMarketId(marketId);
        if (count > 0) {
            log.info("Disabled all sync for {} symbol(s) in market: {}", count, marketId);
        }
        return count;
    }

    /**
     * 获取启用了实时同步的交易对列表
     */
    public List<SymbolDTO> getRealtimeSyncEnabled() {
        return symbolMapper.selectRealtimeSyncEnabled().stream()
                .map(this::toDTO)
                .toList();
    }

    /**
     * 获取启用了实时同步的交易对实体列表
     * 
     * 仅返回：
     * - 实时同步已启用
     * - 所属市场已启用
     * - 所属数据源已启用且未删除
     */
    public List<Symbol> getRealtimeSyncEnabledSymbols() {
        return symbolMapper.selectRealtimeSyncEnabledWithValidDataSource();
    }

    /**
     * 获取启用了历史同步的交易对列表
     */
    public List<SymbolDTO> getHistorySyncEnabled() {
        return symbolMapper.selectHistorySyncEnabled().stream()
                .map(this::toDTO)
                .toList();
    }

    /**
     * 创建或更新交易对（用于同步）
     * 
     * @param marketId 市场ID
     * @param symbolCode 交易对代码
     * @param baseAsset 基础货币
     * @param quoteAsset 报价货币
     * @param pricePrecision 价格精度
     * @param quantityPrecision 数量精度
     * @param status 状态
     * @return 创建或更新的交易对
     */
    @Transactional
    public Symbol createOrUpdate(Long marketId, String symbolCode, String baseAsset, String quoteAsset,
                                  Integer pricePrecision, Integer quantityPrecision, String status) {
        // 验证市场存在
        marketService.findById(marketId);
        
        // 查找是否已存在
        Symbol existing = symbolMapper.selectByMarketIdAndSymbol(marketId, symbolCode);
        
        if (existing != null) {
            // 更新已存在的交易对
            boolean updated = false;
            
            if (!existing.getBaseAsset().equals(baseAsset)) {
                existing.setBaseAsset(baseAsset);
                updated = true;
            }
            if (!existing.getQuoteAsset().equals(quoteAsset)) {
                existing.setQuoteAsset(quoteAsset);
                updated = true;
            }
            if (!existing.getPricePrecision().equals(pricePrecision)) {
                existing.setPricePrecision(pricePrecision);
                updated = true;
            }
            if (!existing.getQuantityPrecision().equals(quantityPrecision)) {
                existing.setQuantityPrecision(quantityPrecision);
                updated = true;
            }
            if (!existing.getStatus().equals(status)) {
                existing.setStatus(status);
                updated = true;
            }
            
            if (updated) {
                existing.setUpdatedAt(Instant.now());
                symbolMapper.updateById(existing);
                log.debug("Updated symbol: marketId={}, symbol={}", marketId, symbolCode);
            }
            
            return existing;
        }
        
        // 创建新交易对（同步开关默认关闭）
        Symbol symbol = Symbol.builder()
                .marketId(marketId)
                .symbol(symbolCode)
                .baseAsset(baseAsset)
                .quoteAsset(quoteAsset)
                .pricePrecision(pricePrecision)
                .quantityPrecision(quantityPrecision)
                .realtimeSyncEnabled(false)  // 默认关闭
                .historySyncEnabled(false)   // 默认关闭
                .status(status)
                .build();
        
        symbolMapper.insert(symbol);
        log.debug("Created symbol: marketId={}, symbol={}", marketId, symbolCode);
        
        return symbol;
    }


    /**
     * 根据 ID 查找交易对实体
     */
    public Symbol findById(Long id) {
        Symbol symbol = symbolMapper.selectById(id);
        if (symbol == null) {
            throw new ResourceNotFoundException("交易对不存在: " + id);
        }
        return symbol;
    }

    /**
     * 检查交易对是否存在
     */
    public boolean existsByMarketIdAndSymbol(Long marketId, String symbolCode) {
        return symbolMapper.selectByMarketIdAndSymbol(marketId, symbolCode) != null;
    }

    /**
     * 统计市场下的交易对数量
     */
    public int countByMarketId(Long marketId) {
        return symbolMapper.countByMarketId(marketId);
    }

    /**
     * 统计市场下启用了同步的交易对数量
     */
    public int countEnabledSyncByMarketId(Long marketId) {
        return symbolMapper.countEnabledSyncByMarketId(marketId);
    }

    /**
     * 构建查询条件
     */
    private LambdaQueryWrapper<Symbol> buildQueryWrapper(Long marketId, Long dataSourceId, String keyword,
                                                          Boolean realtimeSyncEnabled, Boolean historySyncEnabled) {
        LambdaQueryWrapper<Symbol> wrapper = new LambdaQueryWrapper<>();
        
        if (marketId != null) {
            wrapper.eq(Symbol::getMarketId, marketId);
        }
        
        // 如果指定了数据源ID，需要先查询该数据源下的所有市场
        if (dataSourceId != null) {
            List<Long> marketIds = marketService.getByDataSourceId(dataSourceId).stream()
                    .map(m -> m.getId())
                    .toList();
            if (marketIds.isEmpty()) {
                // 如果没有市场，返回空结果
                wrapper.eq(Symbol::getId, -1L);
            } else {
                wrapper.in(Symbol::getMarketId, marketIds);
            }
        }
        
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w
                    .like(Symbol::getSymbol, keyword)
                    .or()
                    .like(Symbol::getBaseAsset, keyword)
                    .or()
                    .like(Symbol::getQuoteAsset, keyword));
        }
        
        if (realtimeSyncEnabled != null) {
            wrapper.eq(Symbol::getRealtimeSyncEnabled, realtimeSyncEnabled);
        }
        
        if (historySyncEnabled != null) {
            wrapper.eq(Symbol::getHistorySyncEnabled, historySyncEnabled);
        }
        
        return wrapper;
    }

    /**
     * 校验同步周期合法性
     */
    private void validateIntervals(List<String> intervals) {
        if (intervals == null) {
            return;
        }
        
        for (String interval : intervals) {
            if (!VALID_INTERVALS.contains(interval)) {
                throw new BusinessException("不支持的同步周期: " + interval + 
                        "。支持的周期: " + String.join(", ", VALID_INTERVALS));
            }
        }
    }

    /**
     * 验证市场和数据源是否启用
     */
    private void validateMarketAndDataSourceEnabled(Long marketId) {
        Market market = marketService.findById(marketId);
        if (!market.getEnabled()) {
            throw new BusinessException("市场未启用，无法开启同步");
        }
        
        DataSource dataSource = dataSourceService.findById(market.getDataSourceId());
        if (!dataSource.getEnabled()) {
            throw new BusinessException("数据源未启用，无法开启同步");
        }
    }


    /**
     * 转换为 DTO
     */
    private SymbolDTO toDTO(Symbol entity) {
        Market market = null;
        DataSource dataSource = null;
        
        try {
            market = marketService.findById(entity.getMarketId());
            dataSource = dataSourceService.findById(market.getDataSourceId());
        } catch (ResourceNotFoundException e) {
            // 市场或数据源可能已被删除，忽略
        }
        
        return toDTO(entity, market, dataSource);
    }

    /**
     * 转换为 DTO（带市场和数据源信息）
     */
    private SymbolDTO toDTO(Symbol entity, Market market, DataSource dataSource) {
        List<String> intervals = parseIntervals(entity.getSyncIntervals());
        
        return SymbolDTO.builder()
                .id(entity.getId())
                .marketId(entity.getMarketId())
                .marketName(market != null ? market.getName() : null)
                .marketType(market != null ? market.getMarketType() : null)
                .dataSourceId(dataSource != null ? dataSource.getId() : null)
                .dataSourceName(dataSource != null ? dataSource.getName() : null)
                .symbol(entity.getSymbol())
                .baseAsset(entity.getBaseAsset())
                .quoteAsset(entity.getQuoteAsset())
                .pricePrecision(entity.getPricePrecision())
                .quantityPrecision(entity.getQuantityPrecision())
                .realtimeSyncEnabled(entity.getRealtimeSyncEnabled())
                .historySyncEnabled(entity.getHistorySyncEnabled())
                .syncIntervals(intervals)
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /**
     * 解析同步周期字符串
     */
    private List<String> parseIntervals(String intervalsStr) {
        if (!StringUtils.hasText(intervalsStr)) {
            return Collections.emptyList();
        }
        return Arrays.stream(intervalsStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * 获取支持的同步周期列表
     */
    public Set<String> getValidIntervals() {
        return VALID_INTERVALS;
    }

    /**
     * 从币安同步交易对列表
     * 
     * 根据市场的数据源调用币安 exchangeInfo API 获取交易对列表，
     * 并创建或更新本地交易对记录。
     * 
     * 新同步的交易对默认关闭实时同步和历史同步（符合需求 3.3、3.4）
     * 
     * @param marketId 市场ID
     * @return 同步结果
     */
    @Transactional
    public SymbolSyncResult syncSymbolsFromBinance(Long marketId) {
        // 验证市场存在
        Market market = marketService.findById(marketId);
        
        // 验证市场已启用
        if (!market.getEnabled()) {
            throw new BusinessException("市场未启用，无法同步交易对");
        }
        
        // 获取数据源
        DataSource dataSource = dataSourceService.findById(market.getDataSourceId());
        
        // 验证数据源已启用
        if (!dataSource.getEnabled()) {
            throw new BusinessException("数据源未启用，无法同步交易对");
        }
        
        // 验证是币安数据源
        if (!"BINANCE".equalsIgnoreCase(dataSource.getExchangeType())) {
            throw new BusinessException("仅支持币安数据源同步交易对");
        }
        
        // 创建币安客户端
        BinanceClient client = binanceClientFactory.createClient(dataSource);
        try {
            // 测试连接
            BinanceClient.ConnectionTestResult testResult = client.testConnection();
            if (!testResult.isSuccess()) {
                throw new BusinessException("数据源连接测试失败: " + testResult.getMessage());
            }
            
            // 获取交易所信息
            BinanceApiResponse<BinanceExchangeInfo> response = client.getExchangeInfo();
            if (!response.isSuccess()) {
                throw new BusinessException("获取交易所信息失败: " + response.getMessage());
            }
            
            BinanceExchangeInfo exchangeInfo = response.getData();
            if (exchangeInfo == null || exchangeInfo.getSymbols() == null) {
                throw new BusinessException("交易所信息为空");
            }
            
            // 同步交易对
            List<SymbolDTO> syncedSymbols = new ArrayList<>();
            int createdCount = 0;
            int updatedCount = 0;
            int existingCount = 0;
            
            for (BinanceExchangeInfo.BinanceSymbol binanceSymbol : exchangeInfo.getSymbols()) {
                SyncSymbolResult result = syncSingleSymbol(market, binanceSymbol);
                syncedSymbols.add(result.symbol());
                
                switch (result.action()) {
                    case CREATED -> createdCount++;
                    case UPDATED -> updatedCount++;
                    case UNCHANGED -> existingCount++;
                }
            }
            
            log.info("Synced symbols for market {}: total={}, created={}, updated={}, unchanged={}", 
                    market.getName(), syncedSymbols.size(), createdCount, updatedCount, existingCount);
            
            return SymbolSyncResult.builder()
                    .success(true)
                    .message("交易对同步成功")
                    .syncedCount(syncedSymbols.size())
                    .createdCount(createdCount)
                    .updatedCount(updatedCount)
                    .existingCount(existingCount)
                    .symbols(syncedSymbols)
                    .build();
                    
        } finally {
            client.close();
        }
    }

    /**
     * 同步单个交易对
     */
    private SyncSymbolResult syncSingleSymbol(Market market, BinanceExchangeInfo.BinanceSymbol binanceSymbol) {
        String symbolCode = binanceSymbol.getSymbol();
        String baseAsset = binanceSymbol.getBaseAsset();
        String quoteAsset = binanceSymbol.getQuoteAsset();
        Integer pricePrecision = binanceSymbol.getEffectivePricePrecision();
        Integer quantityPrecision = binanceSymbol.getEffectiveQuantityPrecision();
        String status = binanceSymbol.getStatus();
        
        // 查找是否已存在
        Symbol existing = symbolMapper.selectByMarketIdAndSymbol(market.getId(), symbolCode);
        
        if (existing != null) {
            // 检查是否需要更新
            boolean needUpdate = false;
            
            if (!existing.getBaseAsset().equals(baseAsset)) {
                existing.setBaseAsset(baseAsset);
                needUpdate = true;
            }
            if (!existing.getQuoteAsset().equals(quoteAsset)) {
                existing.setQuoteAsset(quoteAsset);
                needUpdate = true;
            }
            if (!existing.getPricePrecision().equals(pricePrecision)) {
                existing.setPricePrecision(pricePrecision);
                needUpdate = true;
            }
            if (!existing.getQuantityPrecision().equals(quantityPrecision)) {
                existing.setQuantityPrecision(quantityPrecision);
                needUpdate = true;
            }
            if (!existing.getStatus().equals(status)) {
                existing.setStatus(status);
                needUpdate = true;
            }
            
            if (needUpdate) {
                existing.setUpdatedAt(Instant.now());
                symbolMapper.updateById(existing);
                log.debug("Updated symbol: marketId={}, symbol={}", market.getId(), symbolCode);
                return new SyncSymbolResult(toDTO(existing), SyncAction.UPDATED);
            }
            
            return new SyncSymbolResult(toDTO(existing), SyncAction.UNCHANGED);
        }
        
        // 创建新交易对（同步开关默认关闭，符合需求 3.3、3.4）
        Symbol symbol = Symbol.builder()
                .marketId(market.getId())
                .symbol(symbolCode)
                .baseAsset(baseAsset)
                .quoteAsset(quoteAsset)
                .pricePrecision(pricePrecision)
                .quantityPrecision(quantityPrecision)
                .realtimeSyncEnabled(false)  // 默认关闭
                .historySyncEnabled(false)   // 默认关闭
                .status(status)
                .build();
        
        symbolMapper.insert(symbol);
        log.debug("Created symbol: marketId={}, symbol={}", market.getId(), symbolCode);
        
        return new SyncSymbolResult(toDTO(symbol), SyncAction.CREATED);
    }

    /**
     * 同步操作类型
     */
    private enum SyncAction {
        CREATED, UPDATED, UNCHANGED
    }

    /**
     * 同步交易对结果记录
     */
    private record SyncSymbolResult(SymbolDTO symbol, SyncAction action) {}
}
