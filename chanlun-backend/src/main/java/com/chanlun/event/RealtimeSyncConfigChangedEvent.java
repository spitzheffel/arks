package com.chanlun.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 实时同步配置变更事件
 * 
 * 当 sync.realtime.enabled 配置变更时触发
 * 
 * @author Chanlun Team
 */
@Getter
public class RealtimeSyncConfigChangedEvent extends ApplicationEvent {

    /**
     * 是否启用实时同步
     */
    private final boolean enabled;

    /**
     * 构造函数
     * 
     * @param source 事件源
     * @param enabled 是否启用
     */
    public RealtimeSyncConfigChangedEvent(Object source, boolean enabled) {
        super(source);
        this.enabled = enabled;
    }
}
