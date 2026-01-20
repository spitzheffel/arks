package com.chanlun.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chanlun.entity.SyncStatus;
import org.apache.ibatis.annotations.*;

import java.time.Instant;
import java.util.List;

/**
 * 同步状态 Mapper 接口
 * 
 * @author Chanlun Team
 */
@Mapper
public interface SyncStatusMapper extends BaseMapper<SyncStatus> {

    /**
     * 根据交易对ID和周期查询同步状态
     * 
     * @param symbolId 交易对ID
     * @param interval 时间周期
     * @return 同步状态
     */
    @Select("SELECT * FROM sync_status WHERE symbol_id = #{symbolId} AND \"interval\" = #{interval}")
    SyncStatus selectBySymbolIdAndInterval(@Param("symbolId") Long symbolId, @Param("interval") String interval);

    /**
     * 根据交易对ID查询所有周期的同步状态
     * 
     * @param symbolId 交易对ID
     * @return 同步状态列表
     */
    @Select("SELECT * FROM sync_status WHERE symbol_id = #{symbolId} ORDER BY \"interval\"")
    List<SyncStatus> selectBySymbolId(@Param("symbolId") Long symbolId);

    /**
     * 更新 last_kline_time 字段
     * 
     * @param symbolId 交易对ID
     * @param interval 时间周期
     * @param lastKlineTime 最后K线时间（可为 null）
     * @return 更新的记录数
     */
    @Update("UPDATE sync_status SET last_kline_time = #{lastKlineTime}, updated_at = NOW() " +
            "WHERE symbol_id = #{symbolId} AND \"interval\" = #{interval}")
    int updateLastKlineTime(@Param("symbolId") Long symbolId, 
                            @Param("interval") String interval, 
                            @Param("lastKlineTime") Instant lastKlineTime);

    /**
     * 更新 total_klines 字段
     * 
     * @param symbolId 交易对ID
     * @param interval 时间周期
     * @param totalKlines 总K线数量
     * @return 更新的记录数
     */
    @Update("UPDATE sync_status SET total_klines = #{totalKlines}, updated_at = NOW() " +
            "WHERE symbol_id = #{symbolId} AND \"interval\" = #{interval}")
    int updateTotalKlines(@Param("symbolId") Long symbolId, 
                          @Param("interval") String interval, 
                          @Param("totalKlines") Long totalKlines);

    /**
     * 更新 auto_gap_fill_enabled 字段
     * 
     * @param symbolId 交易对ID
     * @param interval 时间周期
     * @param enabled 是否启用自动回补
     * @return 更新的记录数
     */
    @Update("UPDATE sync_status SET auto_gap_fill_enabled = #{enabled}, updated_at = NOW() " +
            "WHERE symbol_id = #{symbolId} AND \"interval\" = #{interval}")
    int updateAutoGapFillEnabled(@Param("symbolId") Long symbolId, 
                                  @Param("interval") String interval, 
                                  @Param("enabled") Boolean enabled);

    /**
     * 插入或更新同步状态（upsert）
     * 
     * @param syncStatus 同步状态
     * @return 影响的记录数
     */
    @Insert("INSERT INTO sync_status (symbol_id, \"interval\", last_sync_time, last_kline_time, total_klines, auto_gap_fill_enabled, created_at, updated_at) " +
            "VALUES (#{symbolId}, #{interval}, #{lastSyncTime}, #{lastKlineTime}, #{totalKlines}, #{autoGapFillEnabled}, NOW(), NOW()) " +
            "ON CONFLICT (symbol_id, \"interval\") DO UPDATE SET " +
            "last_sync_time = EXCLUDED.last_sync_time, " +
            "last_kline_time = EXCLUDED.last_kline_time, " +
            "total_klines = EXCLUDED.total_klines, " +
            "auto_gap_fill_enabled = EXCLUDED.auto_gap_fill_enabled, " +
            "updated_at = NOW()")
    int upsert(SyncStatus syncStatus);

    /**
     * 删除指定交易对的所有同步状态
     * 
     * @param symbolId 交易对ID
     * @return 删除的记录数
     */
    @Delete("DELETE FROM sync_status WHERE symbol_id = #{symbolId}")
    int deleteBySymbolId(@Param("symbolId") Long symbolId);

    /**
     * 删除指定交易对和周期的同步状态
     * 
     * @param symbolId 交易对ID
     * @param interval 时间周期
     * @return 删除的记录数
     */
    @Delete("DELETE FROM sync_status WHERE symbol_id = #{symbolId} AND \"interval\" = #{interval}")
    int deleteBySymbolIdAndInterval(@Param("symbolId") Long symbolId, @Param("interval") String interval);
}
