package com.chanlun.exception;

/**
 * 资源未找到异常
 * 
 * 用于表示请求的资源不存在
 * 
 * @author Chanlun Team
 */
public class ResourceNotFoundException extends BusinessException {

    /**
     * 构造函数
     */
    public ResourceNotFoundException(String message) {
        super(404, message);
    }

    /**
     * 构造函数（带资源类型和 ID）
     */
    public ResourceNotFoundException(String resourceType, Long id) {
        super(404, String.format("%s not found with id: %d", resourceType, id));
    }

    /**
     * 构造函数（带资源类型和标识符）
     */
    public ResourceNotFoundException(String resourceType, String identifier) {
        super(404, String.format("%s not found: %s", resourceType, identifier));
    }
}
