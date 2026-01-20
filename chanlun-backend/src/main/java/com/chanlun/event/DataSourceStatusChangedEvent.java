package com.chanlun.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 数据源状态变更事件
 * 
 * 当数据源被禁用或删除时发布此事件，
 * 用于触发级联禁用关联的市场和交易对同步
 * 
 * @author Chanlun Team
 */
@Getter
public class DataSourceStatusChangedEvent extends ApplicationEvent {

    /**
     * 数据源 ID
     */
    private final Long dataSourceId;

    /**
     * 新状态（true=启用, false=禁用）
     */
    private final boolean enabled;

    /**
     * 是否为删除操作
     */
    private final boolean deleted;

    public DataSourceStatusChangedEvent(Object source, Long dataSourceId, boolean enabled, boolean deleted) {
        super(source);
        this.dataSourceId = dataSourceId;
        this.enabled = enabled;
        this.deleted = deleted;
    }
}
