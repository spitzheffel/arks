package com.chanlun.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Flyway 配置测试
 */
@DisplayName("Flyway 配置测试")
class FlywayConfigTest {

    private final FlywayConfig flywayConfig = new FlywayConfig();

    @Test
    @DisplayName("应该创建 FlywayMigrationStrategy Bean")
    void shouldCreateFlywayMigrationStrategy() {
        FlywayMigrationStrategy strategy = flywayConfig.flywayMigrationStrategy();
        
        assertNotNull(strategy, "FlywayMigrationStrategy 不应为空");
    }
}
