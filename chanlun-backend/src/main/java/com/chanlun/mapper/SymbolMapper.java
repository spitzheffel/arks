package com.chanlun.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chanlun.entity.Symbol;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 交易对 Mapper 接口
 * 
 * @author Chanlun Team
 */
@Mapper
public interface SymbolMapper extends BaseMapper<Symbol> {

    /**
     * 根据市场ID查询交易对列表
     * 
     * @param marketId 市场ID
     * @return 交易对列表
     */
    @Select("SELECT * FROM symbol WHERE market_id = #{marketId}")
    List<Symbol> selectByMarketId(@Param("marketId") Long marketId);

    /**
     * 根据市场ID和交易对代码查询
     * 
     * @param marketId 市场ID
     * @param symbol 交易对代码
     * @return 交易对
     */
    @Select("SELECT * FROM symbol WHERE market_id = #{marketId} AND symbol = #{symbol}")
    Symbol selectByMarketIdAndSymbol(@Param("marketId") Long marketId, @Param("symbol") String symbol);

    /**
     * 查询启用了实时同步的交易对列表
     * 
     * @return 交易对列表
     */
    @Select("SELECT * FROM symbol WHERE realtime_sync_enabled = true")
    List<Symbol> selectRealtimeSyncEnabled();

    /**
     * 查询启用了历史同步的交易对列表
     * 
     * @return 交易对列表
     */
    @Select("SELECT * FROM symbol WHERE history_sync_enabled = true")
    List<Symbol> selectHistorySyncEnabled();

    /**
     * 根据市场ID查询启用了实时同步的交易对列表
     * 
     * @param marketId 市场ID
     * @return 交易对列表
     */
    @Select("SELECT * FROM symbol WHERE market_id = #{marketId} AND realtime_sync_enabled = true")
    List<Symbol> selectRealtimeSyncEnabledByMarketId(@Param("marketId") Long marketId);

    /**
     * 根据市场ID查询启用了历史同步的交易对列表
     * 
     * @param marketId 市场ID
     * @return 交易对列表
     */
    @Select("SELECT * FROM symbol WHERE market_id = #{marketId} AND history_sync_enabled = true")
    List<Symbol> selectHistorySyncEnabledByMarketId(@Param("marketId") Long marketId);

    /**
     * 根据状态查询交易对列表
     * 
     * @param status 状态 (TRADING/HALT)
     * @return 交易对列表
     */
    @Select("SELECT * FROM symbol WHERE status = #{status}")
    List<Symbol> selectByStatus(@Param("status") String status);

    /**
     * 根据市场ID禁用所有交易对的实时同步
     * 
     * @param marketId 市场ID
     * @return 更新的记录数
     */
    @Update("UPDATE symbol SET realtime_sync_enabled = false, updated_at = NOW() WHERE market_id = #{marketId} AND realtime_sync_enabled = true")
    int disableRealtimeSyncByMarketId(@Param("marketId") Long marketId);

    /**
     * 根据市场ID禁用所有交易对的历史同步
     * 
     * @param marketId 市场ID
     * @return 更新的记录数
     */
    @Update("UPDATE symbol SET history_sync_enabled = false, updated_at = NOW() WHERE market_id = #{marketId} AND history_sync_enabled = true")
    int disableHistorySyncByMarketId(@Param("marketId") Long marketId);

    /**
     * 根据市场ID禁用所有交易对的同步（实时+历史）
     * 
     * @param marketId 市场ID
     * @return 更新的记录数
     */
    @Update("UPDATE symbol SET realtime_sync_enabled = false, history_sync_enabled = false, updated_at = NOW() WHERE market_id = #{marketId} AND (realtime_sync_enabled = true OR history_sync_enabled = true)")
    int disableAllSyncByMarketId(@Param("marketId") Long marketId);

    /**
     * 统计市场下启用了同步的交易对数量
     * 
     * @param marketId 市场ID
     * @return 启用同步的交易对数量
     */
    @Select("SELECT COUNT(*) FROM symbol WHERE market_id = #{marketId} AND (realtime_sync_enabled = true OR history_sync_enabled = true)")
    int countEnabledSyncByMarketId(@Param("marketId") Long marketId);

    /**
     * 检查市场下是否存在指定交易对代码（用于唯一性校验）
     * 
     * @param marketId 市场ID
     * @param symbol 交易对代码
     * @param id 排除的ID（更新时使用）
     * @return 数量
     */
    @Select("SELECT COUNT(*) FROM symbol WHERE market_id = #{marketId} AND symbol = #{symbol} AND (#{id} IS NULL OR id != #{id})")
    int countByMarketIdAndSymbolExcludeId(@Param("marketId") Long marketId, @Param("symbol") String symbol, @Param("id") Long id);

    /**
     * 统计市场下的交易对数量
     * 
     * @param marketId 市场ID
     * @return 交易对数量
     */
    @Select("SELECT COUNT(*) FROM symbol WHERE market_id = #{marketId}")
    int countByMarketId(@Param("marketId") Long marketId);

    /**
     * 查询启用了实时同步的交易对列表（包含有效的数据源和市场）
     * 
     * 仅返回：
     * - 实时同步已启用
     * - 所属市场已启用
     * - 所属数据源已启用且未删除
     * 
     * @return 交易对列表
     */
    @Select("""
            SELECT s.* FROM symbol s
            INNER JOIN market m ON s.market_id = m.id
            INNER JOIN data_source ds ON m.data_source_id = ds.id
            WHERE s.realtime_sync_enabled = true
              AND m.enabled = true
              AND ds.enabled = true
              AND ds.deleted = false
            """)
    List<Symbol> selectRealtimeSyncEnabledWithValidDataSource();

    /**
     * 查询启用了历史同步的交易对列表（包含有效的数据源和市场）
     * 
     * 仅返回：
     * - 历史同步已启用
     * - 所属市场已启用
     * - 所属数据源已启用且未删除
     * 
     * @return 交易对列表
     */
    @Select("""
            SELECT s.* FROM symbol s
            INNER JOIN market m ON s.market_id = m.id
            INNER JOIN data_source ds ON m.data_source_id = ds.id
            WHERE s.history_sync_enabled = true
              AND m.enabled = true
              AND ds.enabled = true
              AND ds.deleted = false
            """)
    List<Symbol> selectHistorySyncEnabledWithValidDataSource();
}
