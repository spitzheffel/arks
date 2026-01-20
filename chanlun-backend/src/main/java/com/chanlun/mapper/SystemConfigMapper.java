package com.chanlun.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chanlun.entity.SystemConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 系统配置 Mapper
 * 
 * @author Chanlun Team
 */
@Mapper
public interface SystemConfigMapper extends BaseMapper<SystemConfig> {

    /**
     * 根据配置键查询配置
     * 
     * @param configKey 配置键
     * @return 系统配置
     */
    @Select("SELECT * FROM system_config WHERE config_key = #{configKey}")
    SystemConfig selectByKey(@Param("configKey") String configKey);

    /**
     * 根据配置键查询配置值
     * 
     * @param configKey 配置键
     * @return 配置值
     */
    @Select("SELECT config_value FROM system_config WHERE config_key = #{configKey}")
    String selectValueByKey(@Param("configKey") String configKey);

    /**
     * 更新配置值
     * 
     * @param configKey 配置键
     * @param configValue 配置值
     * @return 更新行数
     */
    @Update("UPDATE system_config SET config_value = #{configValue}, updated_at = NOW() WHERE config_key = #{configKey}")
    int updateValueByKey(@Param("configKey") String configKey, @Param("configValue") String configValue);
}
