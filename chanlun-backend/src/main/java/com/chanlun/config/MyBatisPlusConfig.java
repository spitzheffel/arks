package com.chanlun.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置类
 * 
 * 配置内容：
 * - 分页插件：支持 PostgreSQL 分页查询
 * - Mapper 扫描：自动扫描 com.chanlun.mapper 包下的 Mapper 接口
 * 
 * @author Chanlun Team
 */
@Configuration
@MapperScan("com.chanlun.mapper")
public class MyBatisPlusConfig {

    /**
     * 配置 MyBatis-Plus 拦截器
     * 
     * @return MybatisPlusInterceptor 拦截器实例
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        
        // 分页插件 - 指定数据库类型为 PostgreSQL
        PaginationInnerInterceptor paginationInterceptor = new PaginationInnerInterceptor(DbType.POSTGRE_SQL);
        // 设置最大单页限制数量，默认 500 条，-1 不受限制
        paginationInterceptor.setMaxLimit(100L);
        // 溢出总页数后是否进行处理
        paginationInterceptor.setOverflow(false);
        
        interceptor.addInnerInterceptor(paginationInterceptor);
        
        return interceptor;
    }
}
