package com.chanlun.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * 数据源配置类
 * 
 * 配置 PostgreSQL 数据源，使用 HikariCP 连接池
 * 
 * 配置说明：
 * - 使用 HikariCP 作为连接池（Spring Boot 默认）
 * - 支持通过环境变量配置数据库连接信息
 * - 连接池参数已针对本地单用户场景优化
 * 
 * @author Chanlun Team
 */
@Configuration
public class DataSourceConfig {

    /**
     * 数据源属性配置
     * 
     * @return DataSourceProperties 数据源属性
     */
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    /**
     * 配置 HikariCP 数据源
     * 
     * @param properties 数据源属性
     * @return DataSource 数据源实例
     */
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource dataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }
}
