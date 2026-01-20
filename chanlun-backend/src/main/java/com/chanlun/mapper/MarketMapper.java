package com.chanlun.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chanlun.entity.Market;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 市场 Mapper 接口
 * 
 * @author Chanlun Team
 */
@Mapper
public interface MarketMapper extends BaseMapper<Market> {

    /**
     * 根据数据源ID查询市场列表
     */
    @Select("SELECT * FROM market WHERE data_source_id = #{dataSourceId}")
    List<Market> selectByDataSourceId(@Param("dataSourceId") Long dataSourceId);

    /**
     * 根据数据源ID查询启用的市场列表
     */
    @Select("SELECT * FROM market WHERE data_source_id = #{dataSourceId} AND enabled = true")
    List<Market> selectEnabledByDataSourceId(@Param("dataSourceId") Long dataSourceId);

    /**
     * 根据市场类型查询市场列表
     */
    @Select("SELECT * FROM market WHERE market_type = #{marketType}")
    List<Market> selectByMarketType(@Param("marketType") String marketType);

    /**
     * 查询所有启用的市场
     */
    @Select("SELECT * FROM market WHERE enabled = true")
    List<Market> selectEnabledMarkets();

    /**
     * 根据数据源ID和市场类型查询市场
     */
    @Select("SELECT * FROM market WHERE data_source_id = #{dataSourceId} AND market_type = #{marketType}")
    Market selectByDataSourceIdAndMarketType(@Param("dataSourceId") Long dataSourceId, @Param("marketType") String marketType);

    /**
     * 检查数据源下是否存在指定市场类型（用于唯一性校验）
     */
    @Select("SELECT COUNT(*) FROM market WHERE data_source_id = #{dataSourceId} AND market_type = #{marketType} AND (#{id} IS NULL OR id != #{id})")
    int countByDataSourceIdAndMarketTypeExcludeId(@Param("dataSourceId") Long dataSourceId, @Param("marketType") String marketType, @Param("id") Long id);
}
