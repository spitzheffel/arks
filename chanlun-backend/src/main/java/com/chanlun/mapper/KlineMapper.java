package com.chanlun.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chanlun.entity.Kline;
import org.apache.ibatis.annotations.*;

import java.time.Instant;
import java.util.List;

/**
 * K线数据 Mapper 接口
 * 
 * @author Chanlun Team
 */
@Mapper
public interface KlineMapper extends BaseMapper<Kline> {

    /**
     * 根据交易对ID和周期查询K线列表
     * 
     * @param symbolId 交易对ID
     * @param interval 时间周期
     * @return K线列表
     */
    @Select("SELECT * FROM kline WHERE symbol_id = #{symbolId} AND \"interval\" = #{interval} ORDER BY open_time ASC")
    List<Kline> selectBySymbolIdAndInterval(@Param("symbolId") Long symbolId, @Param("interval") String interval);

    /**
     * 根据交易对ID、周期和时间范围查询K线列表
     * 
     * @param symbolId 交易对ID
     * @param interval 时间周期
     * @param startTime 开始时间 (包含)
     * @param endTime 结束时间 (包含)
     * @return K线列表
     */
    @Select("SELECT * FROM kline WHERE symbol_id = #{symbolId} AND \"interval\" = #{interval} " +
            "AND open_time >= #{startTime} AND open_time <= #{endTime} ORDER BY open_time ASC")
    List<Kline> selectBySymbolIdAndIntervalAndTimeRange(
            @Param("symbolId") Long symbolId,
            @Param("interval") String interval,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime);

    /**
     * 根据交易对ID、周期和时间范围查询K线列表（带数量限制）
     * 
     * @param symbolId 交易对ID
     * @param interval 时间周期
     * @param startTime 开始时间 (包含)
     * @param endTime 结束时间 (包含)
     * @param limit 返回数量限制
     * @return K线列表
     */
    @Select("SELECT * FROM kline WHERE symbol_id = #{symbolId} AND \"interval\" = #{interval} " +
            "AND open_time >= #{startTime} AND open_time <= #{endTime} ORDER BY open_time ASC LIMIT #{limit}")
    List<Kline> selectBySymbolIdAndIntervalAndTimeRangeWithLimit(
            @Param("symbolId") Long symbolId,
            @Param("interval") String interval,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime,
            @Param("limit") int limit);

    /**
     * 查询指定交易对和周期的最新一根K线
     * 
     * @param symbolId 交易对ID
     * @param interval 时间周期
     * @return 最新K线
     */
    @Select("SELECT * FROM kline WHERE symbol_id = #{symbolId} AND \"interval\" = #{interval} " +
            "ORDER BY open_time DESC LIMIT 1")
    Kline selectLatestBySymbolIdAndInterval(@Param("symbolId") Long symbolId, @Param("interval") String interval);

    /**
     * 查询指定交易对和周期的最早一根K线
     * 
     * @param symbolId 交易对ID
     * @param interval 时间周期
     * @return 最早K线
     */
    @Select("SELECT * FROM kline WHERE symbol_id = #{symbolId} AND \"interval\" = #{interval} " +
            "ORDER BY open_time ASC LIMIT 1")
    Kline selectEarliestBySymbolIdAndInterval(@Param("symbolId") Long symbolId, @Param("interval") String interval);

    /**
     * 统计指定交易对和周期的K线数量
     * 
     * @param symbolId 交易对ID
     * @param interval 时间周期
     * @return K线数量
     */
    @Select("SELECT COUNT(*) FROM kline WHERE symbol_id = #{symbolId} AND \"interval\" = #{interval}")
    long countBySymbolIdAndInterval(@Param("symbolId") Long symbolId, @Param("interval") String interval);

    /**
     * 统计指定交易对和周期在时间范围内的K线数量
     * 
     * @param symbolId 交易对ID
     * @param interval 时间周期
     * @param startTime 开始时间 (包含)
     * @param endTime 结束时间 (包含)
     * @return K线数量
     */
    @Select("SELECT COUNT(*) FROM kline WHERE symbol_id = #{symbolId} AND \"interval\" = #{interval} " +
            "AND open_time >= #{startTime} AND open_time <= #{endTime}")
    long countBySymbolIdAndIntervalAndTimeRange(
            @Param("symbolId") Long symbolId,
            @Param("interval") String interval,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime);

    /**
     * 根据交易对ID、周期和开盘时间查询K线（用于唯一性检查）
     * 
     * @param symbolId 交易对ID
     * @param interval 时间周期
     * @param openTime 开盘时间
     * @return K线
     */
    @Select("SELECT * FROM kline WHERE symbol_id = #{symbolId} AND \"interval\" = #{interval} AND open_time = #{openTime}")
    Kline selectBySymbolIdAndIntervalAndOpenTime(
            @Param("symbolId") Long symbolId,
            @Param("interval") String interval,
            @Param("openTime") Instant openTime);

    /**
     * 删除指定交易对和周期在时间范围内的K线数据
     * 
     * @param symbolId 交易对ID
     * @param interval 时间周期
     * @param startTime 开始时间 (包含)
     * @param endTime 结束时间 (包含)
     * @return 删除的记录数
     */
    @Delete("DELETE FROM kline WHERE symbol_id = #{symbolId} AND \"interval\" = #{interval} " +
            "AND open_time >= #{startTime} AND open_time <= #{endTime}")
    int deleteBySymbolIdAndIntervalAndTimeRange(
            @Param("symbolId") Long symbolId,
            @Param("interval") String interval,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime);

    /**
     * 删除指定交易对的所有K线数据
     * 
     * @param symbolId 交易对ID
     * @return 删除的记录数
     */
    @Delete("DELETE FROM kline WHERE symbol_id = #{symbolId}")
    int deleteBySymbolId(@Param("symbolId") Long symbolId);

    /**
     * 删除指定交易对和周期的所有K线数据
     * 
     * @param symbolId 交易对ID
     * @param interval 时间周期
     * @return 删除的记录数
     */
    @Delete("DELETE FROM kline WHERE symbol_id = #{symbolId} AND \"interval\" = #{interval}")
    int deleteBySymbolIdAndInterval(@Param("symbolId") Long symbolId, @Param("interval") String interval);

    /**
     * 批量插入K线数据（使用 ON CONFLICT 实现 upsert）
     * 
     * @param klines K线列表
     * @return 插入的记录数
     */
    @Insert("<script>" +
            "INSERT INTO kline (symbol_id, \"interval\", open_time, \"open\", high, low, \"close\", volume, quote_volume, trades, close_time, created_at) " +
            "VALUES " +
            "<foreach collection='klines' item='k' separator=','>" +
            "(#{k.symbolId}, #{k.interval}, #{k.openTime}, #{k.open}, #{k.high}, #{k.low}, #{k.close}, #{k.volume}, #{k.quoteVolume}, #{k.trades}, #{k.closeTime}, NOW())" +
            "</foreach>" +
            " ON CONFLICT (symbol_id, \"interval\", open_time) DO UPDATE SET " +
            "\"open\" = EXCLUDED.\"open\", high = EXCLUDED.high, low = EXCLUDED.low, \"close\" = EXCLUDED.\"close\", " +
            "volume = EXCLUDED.volume, quote_volume = EXCLUDED.quote_volume, trades = EXCLUDED.trades, close_time = EXCLUDED.close_time" +
            "</script>")
    int batchUpsert(@Param("klines") List<Kline> klines);

    /**
     * 查询指定交易对和周期的最大开盘时间
     * 
     * @param symbolId 交易对ID
     * @param interval 时间周期
     * @return 最大开盘时间
     */
    @Select("SELECT MAX(open_time) FROM kline WHERE symbol_id = #{symbolId} AND \"interval\" = #{interval}")
    Instant selectMaxOpenTimeBySymbolIdAndInterval(@Param("symbolId") Long symbolId, @Param("interval") String interval);

    /**
     * 查询指定交易对和周期的最小开盘时间
     * 
     * @param symbolId 交易对ID
     * @param interval 时间周期
     * @return 最小开盘时间
     */
    @Select("SELECT MIN(open_time) FROM kline WHERE symbol_id = #{symbolId} AND \"interval\" = #{interval}")
    Instant selectMinOpenTimeBySymbolIdAndInterval(@Param("symbolId") Long symbolId, @Param("interval") String interval);
}
