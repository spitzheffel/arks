package com.chanlun.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chanlun.entity.DataGap;
import org.apache.ibatis.annotations.*;

import java.time.Instant;
import java.util.List;

/**
 * 数据缺口 Mapper 接口
 * 
 * @author Chanlun Team
 */
@Mapper
public interface DataGapMapper extends BaseMapper<DataGap> {

    /**
     * 根据交易对ID和周期查询缺口列表
     * 
     * @param symbolId 交易对ID
     * @param interval 时间周期
     * @return 缺口列表
     */
    @Select("SELECT * FROM data_gap WHERE symbol_id = #{symbolId} AND \"interval\" = #{interval} ORDER BY gap_start ASC")
    List<DataGap> selectBySymbolIdAndInterval(@Param("symbolId") Long symbolId, @Param("interval") String interval);

    /**
     * 根据交易对ID、周期和状态查询缺口列表
     * 
     * @param symbolId 交易对ID
     * @param interval 时间周期
     * @param status 状态
     * @return 缺口列表
     */
    @Select("SELECT * FROM data_gap WHERE symbol_id = #{symbolId} AND \"interval\" = #{interval} " +
            "AND status = #{status} ORDER BY gap_start ASC")
    List<DataGap> selectBySymbolIdAndIntervalAndStatus(
            @Param("symbolId") Long symbolId,
            @Param("interval") String interval,
            @Param("status") String status);

    /**
     * 删除指定交易对和周期在时间范围内的缺口记录
     * 
     * 删除条件：缺口的时间范围与指定时间范围有重叠
     * - gap_start <= endTime AND gap_end >= startTime
     * 
     * @param symbolId 交易对ID
     * @param interval 时间周期
     * @param startTime 开始时间 (包含)
     * @param endTime 结束时间 (包含)
     * @return 删除的记录数
     */
    @Delete("DELETE FROM data_gap WHERE symbol_id = #{symbolId} AND \"interval\" = #{interval} " +
            "AND gap_start <= #{endTime} AND gap_end >= #{startTime}")
    int deleteBySymbolIdAndIntervalAndTimeRange(
            @Param("symbolId") Long symbolId,
            @Param("interval") String interval,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime);

    /**
     * 删除指定交易对的所有缺口记录
     * 
     * @param symbolId 交易对ID
     * @return 删除的记录数
     */
    @Delete("DELETE FROM data_gap WHERE symbol_id = #{symbolId}")
    int deleteBySymbolId(@Param("symbolId") Long symbolId);

    /**
     * 删除指定交易对和周期的所有缺口记录
     * 
     * @param symbolId 交易对ID
     * @param interval 时间周期
     * @return 删除的记录数
     */
    @Delete("DELETE FROM data_gap WHERE symbol_id = #{symbolId} AND \"interval\" = #{interval}")
    int deleteBySymbolIdAndInterval(@Param("symbolId") Long symbolId, @Param("interval") String interval);

    /**
     * 统计指定交易对和周期的缺口数量
     * 
     * @param symbolId 交易对ID
     * @param interval 时间周期
     * @return 缺口数量
     */
    @Select("SELECT COUNT(*) FROM data_gap WHERE symbol_id = #{symbolId} AND \"interval\" = #{interval}")
    long countBySymbolIdAndInterval(@Param("symbolId") Long symbolId, @Param("interval") String interval);

    /**
     * 统计指定交易对和周期在时间范围内的缺口数量
     * 
     * @param symbolId 交易对ID
     * @param interval 时间周期
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 缺口数量
     */
    @Select("SELECT COUNT(*) FROM data_gap WHERE symbol_id = #{symbolId} AND \"interval\" = #{interval} " +
            "AND gap_start <= #{endTime} AND gap_end >= #{startTime}")
    long countBySymbolIdAndIntervalAndTimeRange(
            @Param("symbolId") Long symbolId,
            @Param("interval") String interval,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime);

    /**
     * 根据状态查询缺口列表
     * 
     * @param status 状态
     * @return 缺口列表
     */
    @Select("SELECT * FROM data_gap WHERE status = #{status} ORDER BY created_at ASC")
    List<DataGap> selectByStatus(@Param("status") String status);

    /**
     * 根据交易对ID查询缺口列表
     * 
     * @param symbolId 交易对ID
     * @return 缺口列表
     */
    @Select("SELECT * FROM data_gap WHERE symbol_id = #{symbolId} ORDER BY \"interval\", gap_start ASC")
    List<DataGap> selectBySymbolId(@Param("symbolId") Long symbolId);

    /**
     * 查询指定交易对和周期是否存在重叠的缺口
     * 
     * @param symbolId 交易对ID
     * @param interval 时间周期
     * @param gapStart 缺口开始时间
     * @param gapEnd 缺口结束时间
     * @return 重叠的缺口列表
     */
    @Select("SELECT * FROM data_gap WHERE symbol_id = #{symbolId} AND \"interval\" = #{interval} " +
            "AND gap_start <= #{gapEnd} AND gap_end >= #{gapStart}")
    List<DataGap> selectOverlapping(
            @Param("symbolId") Long symbolId,
            @Param("interval") String interval,
            @Param("gapStart") Instant gapStart,
            @Param("gapEnd") Instant gapEnd);

    /**
     * 更新缺口状态
     * 
     * @param id 缺口ID
     * @param status 新状态
     * @return 更新的记录数
     */
    @Update("UPDATE data_gap SET status = #{status}, updated_at = NOW() WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /**
     * 更新缺口状态和错误信息
     * 
     * @param id 缺口ID
     * @param status 新状态
     * @param errorMessage 错误信息
     * @return 更新的记录数
     */
    @Update("UPDATE data_gap SET status = #{status}, error_message = #{errorMessage}, updated_at = NOW() WHERE id = #{id}")
    int updateStatusAndError(@Param("id") Long id, @Param("status") String status, @Param("errorMessage") String errorMessage);

    /**
     * 增加重试次数
     * 
     * @param id 缺口ID
     * @return 更新的记录数
     */
    @Update("UPDATE data_gap SET retry_count = retry_count + 1, updated_at = NOW() WHERE id = #{id}")
    int incrementRetryCount(@Param("id") Long id);

    /**
     * 批量插入缺口记录
     * 
     * @param gaps 缺口列表
     * @return 插入的记录数
     */
    @Insert("<script>" +
            "INSERT INTO data_gap (symbol_id, \"interval\", gap_start, gap_end, missing_count, status, retry_count, created_at, updated_at) " +
            "VALUES " +
            "<foreach collection='gaps' item='g' separator=','>" +
            "(#{g.symbolId}, #{g.interval}, #{g.gapStart}, #{g.gapEnd}, #{g.missingCount}, #{g.status}, #{g.retryCount}, NOW(), NOW())" +
            "</foreach>" +
            "</script>")
    int batchInsert(@Param("gaps") List<DataGap> gaps);

    /**
     * 统计指定状态的缺口数量
     * 
     * @param status 状态
     * @return 缺口数量
     */
    @Select("SELECT COUNT(*) FROM data_gap WHERE status = #{status}")
    long countByStatus(@Param("status") String status);

    /**
     * 查询待回补的缺口列表（带限制）
     * 
     * @param limit 返回数量限制
     * @return 缺口列表
     */
    @Select("SELECT * FROM data_gap WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT #{limit}")
    List<DataGap> selectPendingWithLimit(@Param("limit") int limit);
}
