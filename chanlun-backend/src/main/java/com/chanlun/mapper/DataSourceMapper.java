package com.chanlun.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chanlun.entity.DataSource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 数据源 Mapper 接口
 * 
 * @author Chanlun Team
 */
@Mapper
public interface DataSourceMapper extends BaseMapper<DataSource> {

    /**
     * 查询所有启用且未删除的数据源
     */
    @Select("SELECT * FROM data_source WHERE enabled = true AND deleted = false")
    List<DataSource> selectEnabledDataSources();

    /**
     * 根据交易所类型查询数据源
     */
    @Select("SELECT * FROM data_source WHERE exchange_type = #{exchangeType} AND deleted = false")
    List<DataSource> selectByExchangeType(@Param("exchangeType") String exchangeType);

    /**
     * 检查名称是否已存在（排除指定ID）
     */
    @Select("SELECT COUNT(*) FROM data_source WHERE name = #{name} AND deleted = false AND (#{id} IS NULL OR id != #{id})")
    int countByNameExcludeId(@Param("name") String name, @Param("id") Long id);
}
