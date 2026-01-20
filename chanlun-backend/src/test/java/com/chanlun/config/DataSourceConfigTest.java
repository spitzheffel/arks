package com.chanlun.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据源配置测试
 * 
 * 验证数据源配置是否正确加载
 * 测试环境使用 H2 内存数据库模拟 PostgreSQL
 */
@SpringBootTest
@ActiveProfiles("test")
class DataSourceConfigTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void dataSourceShouldBeConfigured() {
        assertNotNull(dataSource, "DataSource should be configured");
    }

    @Test
    void shouldGetConnection() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            assertNotNull(connection, "Should be able to get connection");
            assertFalse(connection.isClosed(), "Connection should be open");
        }
    }

    @Test
    void connectionShouldBeValid() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            assertTrue(connection.isValid(5), "Connection should be valid");
        }
    }
}
