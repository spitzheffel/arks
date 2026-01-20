package com.chanlun.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * MyBatis-Plus 自动填充处理器
 * 
 * 自动填充 createdAt 和 updatedAt 字段
 * 
 * @author Chanlun Team
 */
@Slf4j
@Component
public class MyBatisMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        log.debug("Start insert fill...");
        Instant now = Instant.now();
        this.strictInsertFill(metaObject, "createdAt", Instant.class, now);
        this.strictInsertFill(metaObject, "updatedAt", Instant.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        log.debug("Start update fill...");
        this.strictUpdateFill(metaObject, "updatedAt", Instant.class, Instant.now());
    }
}
