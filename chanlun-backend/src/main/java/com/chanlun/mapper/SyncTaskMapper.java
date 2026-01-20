package com.chanlun.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chanlun.entity.SyncTask;
import org.apache.ibatis.annotations.*;

import java.time.Instant;
import java.util.List;

/**
 * 同步任务 Mapper 接口
 * 
 * @author Chanlun Team
 */
@Mapper
public interface SyncTaskMapper extends BaseMapper<SyncTask> {

    /**
     * 根据交易对ID查询同步任务列表
     * 
     * @param symbolId 交易对ID
     * @return 同步任务列表
     */
    @Select("SELECT * FROM sync_task WHERE symbol_id = #{symbolId} ORDER BY created_at DESC")
    List<SyncTask> selectBySymbolId(@Param("symbolId") Long symbolId);

    /**
     * 根据交易对ID和周期查询同步任务列表
     * 
     * @param symbolId 交易对ID
     * @param interval 时间周期
     * @return 同步任务列表
     */
    @Select("SELECT * FROM sync_task WHERE symbol_id = #{symbolId} AND \"interval\" = #{interval} ORDER BY created_at DESC")
    List<SyncTask> selectBySymbolIdAndInterval(@Param("symbolId") Long symbolId, @Param("interval") String interval);

    /**
     * 根据任务类型查询同步任务列表
     * 
     * @param taskType 任务类型
     * @return 同步任务列表
     */
    @Select("SELECT * FROM sync_task WHERE task_type = #{taskType} ORDER BY created_at DESC")
    List<SyncTask> selectByTaskType(@Param("taskType") String taskType);

    /**
     * 根据状态查询同步任务列表
     * 
     * @param status 状态
     * @return 同步任务列表
     */
    @Select("SELECT * FROM sync_task WHERE status = #{status} ORDER BY created_at DESC")
    List<SyncTask> selectByStatus(@Param("status") String status);

    /**
     * 根据交易对ID、周期和任务类型查询同步任务列表
     * 
     * @param symbolId 交易对ID
     * @param interval 时间周期
     * @param taskType 任务类型
     * @return 同步任务列表
     */
    @Select("SELECT * FROM sync_task WHERE symbol_id = #{symbolId} AND \"interval\" = #{interval} " +
            "AND task_type = #{taskType} ORDER BY created_at DESC")
    List<SyncTask> selectBySymbolIdAndIntervalAndTaskType(
            @Param("symbolId") Long symbolId, 
            @Param("interval") String interval,
            @Param("taskType") String taskType);

    /**
     * 查询指定交易对和周期的最新同步任务
     * 
     * @param symbolId 交易对ID
     * @param interval 时间周期
     * @return 最新同步任务
     */
    @Select("SELECT * FROM sync_task WHERE symbol_id = #{symbolId} AND \"interval\" = #{interval} " +
            "ORDER BY created_at DESC LIMIT 1")
    SyncTask selectLatestBySymbolIdAndInterval(@Param("symbolId") Long symbolId, @Param("interval") String interval);

    /**
     * 查询指定交易对、周期和任务类型的最新同步任务
     * 
     * @param symbolId 交易对ID
     * @param interval 时间周期
     * @param taskType 任务类型
     * @return 最新同步任务
     */
    @Select("SELECT * FROM sync_task WHERE symbol_id = #{symbolId} AND \"interval\" = #{interval} " +
            "AND task_type = #{taskType} ORDER BY created_at DESC LIMIT 1")
    SyncTask selectLatestBySymbolIdAndIntervalAndTaskType(
            @Param("symbolId") Long symbolId, 
            @Param("interval") String interval,
            @Param("taskType") String taskType);

    /**
     * 更新任务状态
     * 
     * @param id 任务ID
     * @param status 新状态
     * @return 更新的记录数
     */
    @Update("UPDATE sync_task SET status = #{status}, updated_at = NOW() WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /**
     * 更新任务状态和错误信息
     * 
     * @param id 任务ID
     * @param status 新状态
     * @param errorMessage 错误信息
     * @return 更新的记录数
     */
    @Update("UPDATE sync_task SET status = #{status}, error_message = #{errorMessage}, updated_at = NOW() WHERE id = #{id}")
    int updateStatusAndError(@Param("id") Long id, @Param("status") String status, @Param("errorMessage") String errorMessage);

    /**
     * 更新已同步数量
     * 
     * @param id 任务ID
     * @param syncedCount 已同步数量
     * @return 更新的记录数
     */
    @Update("UPDATE sync_task SET synced_count = #{syncedCount}, updated_at = NOW() WHERE id = #{id}")
    int updateSyncedCount(@Param("id") Long id, @Param("syncedCount") Integer syncedCount);

    /**
     * 增加重试次数
     * 
     * @param id 任务ID
     * @return 更新的记录数
     */
    @Update("UPDATE sync_task SET retry_count = retry_count + 1, updated_at = NOW() WHERE id = #{id}")
    int incrementRetryCount(@Param("id") Long id);

    /**
     * 查询待处理的任务（PENDING 状态）
     * 
     * @param limit 限制数量
     * @return 待处理任务列表
     */
    @Select("SELECT * FROM sync_task WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT #{limit}")
    List<SyncTask> selectPendingTasks(@Param("limit") int limit);

    /**
     * 查询运行中的任务（RUNNING 状态）
     * 
     * @return 运行中任务列表
     */
    @Select("SELECT * FROM sync_task WHERE status = 'RUNNING' ORDER BY created_at ASC")
    List<SyncTask> selectRunningTasks();

    /**
     * 查询失败且可重试的任务
     * 
     * @param limit 限制数量
     * @return 可重试任务列表
     */
    @Select("SELECT * FROM sync_task WHERE status = 'FAILED' AND retry_count < max_retries " +
            "ORDER BY created_at ASC LIMIT #{limit}")
    List<SyncTask> selectRetryableTasks(@Param("limit") int limit);

    /**
     * 删除指定交易对的所有同步任务
     * 
     * @param symbolId 交易对ID
     * @return 删除的记录数
     */
    @Delete("DELETE FROM sync_task WHERE symbol_id = #{symbolId}")
    int deleteBySymbolId(@Param("symbolId") Long symbolId);

    /**
     * 删除指定时间之前的已完成任务
     * 
     * @param beforeTime 时间阈值
     * @return 删除的记录数
     */
    @Delete("DELETE FROM sync_task WHERE status IN ('SUCCESS', 'FAILED') AND created_at < #{beforeTime}")
    int deleteCompletedTasksBefore(@Param("beforeTime") Instant beforeTime);

    /**
     * 统计指定状态的任务数量
     * 
     * @param status 状态
     * @return 任务数量
     */
    @Select("SELECT COUNT(*) FROM sync_task WHERE status = #{status}")
    long countByStatus(@Param("status") String status);

    /**
     * 统计指定交易对和周期的任务数量
     * 
     * @param symbolId 交易对ID
     * @param interval 时间周期
     * @return 任务数量
     */
    @Select("SELECT COUNT(*) FROM sync_task WHERE symbol_id = #{symbolId} AND \"interval\" = #{interval}")
    long countBySymbolIdAndInterval(@Param("symbolId") Long symbolId, @Param("interval") String interval);
}
