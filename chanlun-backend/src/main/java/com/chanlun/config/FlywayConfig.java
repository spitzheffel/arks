package com.chanlun.config;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Flyway 数据库迁移配置
 * 
 * <p>配置说明：
 * <ul>
 *   <li>迁移脚本位置: classpath:db/migration</li>
 *   <li>命名规范: V{version}__{description}.sql (如 V1__init_schema.sql)</li>
 *   <li>迁移失败时应用启动将被阻止</li>
 * </ul>
 * 
 * <p>时区规范：所有 TIMESTAMPTZ 字段存储 UTC 时间
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "true", matchIfMissing = true)
public class FlywayConfig {

    /**
     * 自定义 Flyway 迁移策略
     * 
     * <p>在迁移前后输出日志，便于追踪迁移状态
     */
    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            log.info("========== Flyway 数据库迁移开始 ==========");
            
            // 输出待执行的迁移信息
            MigrationInfoService infoService = flyway.info();
            MigrationInfo[] pending = infoService.pending();
            
            if (pending.length == 0) {
                log.info("没有待执行的迁移脚本");
            } else {
                log.info("待执行的迁移脚本数量: {}", pending.length);
                for (MigrationInfo info : pending) {
                    log.info("  - {} : {}", info.getVersion(), info.getDescription());
                }
            }
            
            // 执行迁移
            flyway.migrate();
            
            // 输出当前迁移状态
            MigrationInfo current = infoService.current();
            if (current != null) {
                log.info("当前数据库版本: {} ({})", current.getVersion(), current.getDescription());
            }
            
            log.info("========== Flyway 数据库迁移完成 ==========");
        };
    }
}
