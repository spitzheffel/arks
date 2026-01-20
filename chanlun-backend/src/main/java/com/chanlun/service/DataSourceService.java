package com.chanlun.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chanlun.dto.DataSourceCreateRequest;
import com.chanlun.dto.DataSourceDTO;
import com.chanlun.dto.DataSourceUpdateRequest;
import com.chanlun.entity.DataSource;
import com.chanlun.enums.ExchangeType;
import com.chanlun.enums.ProxyType;
import com.chanlun.event.DataSourceStatusChangedEvent;
import com.chanlun.exception.BusinessException;
import com.chanlun.exception.ResourceNotFoundException;
import com.chanlun.mapper.DataSourceMapper;
import com.chanlun.util.EncryptUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;

/**
 * 数据源服务
 * 
 * 提供数据源的 CRUD 操作，包括：
 * - 创建/更新/删除数据源
 * - 启用/禁用数据源
 * - API Key 和 Secret Key 的加密存储
 * - 软删除和级联禁用
 * 
 * @author Chanlun Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataSourceService {

    private final DataSourceMapper dataSourceMapper;
    private final EncryptUtil encryptUtil;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 获取数据源列表（分页）
     */
    public IPage<DataSourceDTO> list(int page, int size, String exchangeType, Boolean enabled) {
        Page<DataSource> pageParam = new Page<>(page, size);
        
        LambdaQueryWrapper<DataSource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DataSource::getDeleted, false);
        
        if (StringUtils.hasText(exchangeType)) {
            wrapper.eq(DataSource::getExchangeType, exchangeType);
        }
        if (enabled != null) {
            wrapper.eq(DataSource::getEnabled, enabled);
        }
        
        wrapper.orderByDesc(DataSource::getCreatedAt);
        
        IPage<DataSource> result = dataSourceMapper.selectPage(pageParam, wrapper);
        return result.convert(this::toDTO);
    }

    /**
     * 获取所有数据源（不分页）
     */
    public List<DataSourceDTO> listAll(String exchangeType, Boolean enabled) {
        LambdaQueryWrapper<DataSource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DataSource::getDeleted, false);
        
        if (StringUtils.hasText(exchangeType)) {
            wrapper.eq(DataSource::getExchangeType, exchangeType);
        }
        if (enabled != null) {
            wrapper.eq(DataSource::getEnabled, enabled);
        }
        
        wrapper.orderByDesc(DataSource::getCreatedAt);
        
        return dataSourceMapper.selectList(wrapper).stream()
                .map(this::toDTO)
                .toList();
    }

    /**
     * 根据 ID 获取数据源
     */
    public DataSourceDTO getById(Long id) {
        DataSource dataSource = findById(id);
        return toDTO(dataSource);
    }

    /**
     * 创建数据源
     */
    @Transactional
    public DataSourceDTO create(DataSourceCreateRequest request) {
        // 校验交易所类型
        validateExchangeType(request.getExchangeType());
        
        // 校验代理类型
        if (Boolean.TRUE.equals(request.getProxyEnabled())) {
            validateProxyType(request.getProxyType());
        }
        
        // 检查名称是否重复
        if (dataSourceMapper.countByNameExcludeId(request.getName(), null) > 0) {
            throw new BusinessException("数据源名称已存在: " + request.getName());
        }

        DataSource dataSource = DataSource.builder()
                .name(request.getName())
                .exchangeType(request.getExchangeType())
                .apiKey(encryptIfNotEmpty(request.getApiKey()))
                .secretKey(encryptIfNotEmpty(request.getSecretKey()))
                .baseUrl(request.getBaseUrl())
                .wsUrl(request.getWsUrl())
                .proxyEnabled(request.getProxyEnabled() != null ? request.getProxyEnabled() : false)
                .proxyType(request.getProxyType())
                .proxyHost(request.getProxyHost())
                .proxyPort(request.getProxyPort())
                .proxyUsername(request.getProxyUsername())
                .proxyPassword(encryptIfNotEmpty(request.getProxyPassword()))
                .enabled(true)
                .deleted(false)
                .build();

        dataSourceMapper.insert(dataSource);
        log.info("Created data source: id={}, name={}", dataSource.getId(), dataSource.getName());
        
        return toDTO(dataSource);
    }

    /**
     * 更新数据源
     */
    @Transactional
    public DataSourceDTO update(Long id, DataSourceUpdateRequest request) {
        DataSource dataSource = findById(id);

        // 校验交易所类型
        if (StringUtils.hasText(request.getExchangeType())) {
            validateExchangeType(request.getExchangeType());
            dataSource.setExchangeType(request.getExchangeType());
        }

        // 校验代理类型
        if (Boolean.TRUE.equals(request.getProxyEnabled()) && StringUtils.hasText(request.getProxyType())) {
            validateProxyType(request.getProxyType());
        }

        // 检查名称是否重复
        if (StringUtils.hasText(request.getName()) && !request.getName().equals(dataSource.getName())) {
            if (dataSourceMapper.countByNameExcludeId(request.getName(), id) > 0) {
                throw new BusinessException("数据源名称已存在: " + request.getName());
            }
            dataSource.setName(request.getName());
        }

        // 更新基本信息
        if (request.getBaseUrl() != null) {
            dataSource.setBaseUrl(request.getBaseUrl());
        }
        if (request.getWsUrl() != null) {
            dataSource.setWsUrl(request.getWsUrl());
        }

        // 更新 API Key
        if (Boolean.TRUE.equals(request.getClearApiKey())) {
            dataSource.setApiKey(null);
        } else if (StringUtils.hasText(request.getApiKey())) {
            dataSource.setApiKey(encryptUtil.encrypt(request.getApiKey()));
        }

        // 更新 Secret Key
        if (Boolean.TRUE.equals(request.getClearSecretKey())) {
            dataSource.setSecretKey(null);
        } else if (StringUtils.hasText(request.getSecretKey())) {
            dataSource.setSecretKey(encryptUtil.encrypt(request.getSecretKey()));
        }

        // 更新代理配置
        if (request.getProxyEnabled() != null) {
            dataSource.setProxyEnabled(request.getProxyEnabled());
        }
        if (request.getProxyType() != null) {
            dataSource.setProxyType(request.getProxyType());
        }
        if (request.getProxyHost() != null) {
            dataSource.setProxyHost(request.getProxyHost());
        }
        if (request.getProxyPort() != null) {
            dataSource.setProxyPort(request.getProxyPort());
        }
        if (request.getProxyUsername() != null) {
            dataSource.setProxyUsername(request.getProxyUsername());
        }

        // 更新代理密码
        if (Boolean.TRUE.equals(request.getClearProxyPassword())) {
            dataSource.setProxyPassword(null);
        } else if (StringUtils.hasText(request.getProxyPassword())) {
            dataSource.setProxyPassword(encryptUtil.encrypt(request.getProxyPassword()));
        }

        dataSource.setUpdatedAt(Instant.now());
        dataSourceMapper.updateById(dataSource);
        log.info("Updated data source: id={}, name={}", dataSource.getId(), dataSource.getName());

        return toDTO(dataSource);
    }

    /**
     * 删除数据源（软删除）
     */
    @Transactional
    public void delete(Long id) {
        DataSource dataSource = findById(id);
        
        // 软删除
        dataSource.setDeleted(true);
        dataSource.setDeletedAt(Instant.now());
        dataSource.setEnabled(false);
        dataSource.setUpdatedAt(Instant.now());
        
        dataSourceMapper.updateById(dataSource);
        log.info("Soft deleted data source: id={}, name={}", dataSource.getId(), dataSource.getName());
        
        // 发布事件，触发级联禁用
        eventPublisher.publishEvent(new DataSourceStatusChangedEvent(this, id, false, true));
    }

    /**
     * 更新数据源状态（启用/禁用）
     */
    @Transactional
    public DataSourceDTO updateStatus(Long id, boolean enabled) {
        DataSource dataSource = findById(id);
        
        if (dataSource.getEnabled() == enabled) {
            return toDTO(dataSource);
        }

        dataSource.setEnabled(enabled);
        dataSource.setUpdatedAt(Instant.now());
        dataSourceMapper.updateById(dataSource);
        
        log.info("{} data source: id={}, name={}", enabled ? "Enabled" : "Disabled", 
                dataSource.getId(), dataSource.getName());

        // 如果禁用，发布事件触发级联禁用
        if (!enabled) {
            eventPublisher.publishEvent(new DataSourceStatusChangedEvent(this, id, false, false));
        }
        
        return toDTO(dataSource);
    }

    /**
     * 获取解密后的 API Key（内部使用）
     */
    public String getDecryptedApiKey(Long id) {
        DataSource dataSource = findById(id);
        return decryptIfNotEmpty(dataSource.getApiKey());
    }

    /**
     * 获取解密后的 Secret Key（内部使用）
     */
    public String getDecryptedSecretKey(Long id) {
        DataSource dataSource = findById(id);
        return decryptIfNotEmpty(dataSource.getSecretKey());
    }

    /**
     * 获取解密后的代理密码（内部使用）
     */
    public String getDecryptedProxyPassword(Long id) {
        DataSource dataSource = findById(id);
        return decryptIfNotEmpty(dataSource.getProxyPassword());
    }

    /**
     * 根据 ID 查找数据源实体
     */
    public DataSource findById(Long id) {
        LambdaQueryWrapper<DataSource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DataSource::getId, id)
               .eq(DataSource::getDeleted, false);
        
        DataSource dataSource = dataSourceMapper.selectOne(wrapper);
        if (dataSource == null) {
            throw new ResourceNotFoundException("数据源不存在: " + id);
        }
        return dataSource;
    }

    /**
     * 转换为 DTO（不包含敏感信息）
     */
    private DataSourceDTO toDTO(DataSource entity) {
        return DataSourceDTO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .exchangeType(entity.getExchangeType())
                .baseUrl(entity.getBaseUrl())
                .wsUrl(entity.getWsUrl())
                .proxyEnabled(entity.getProxyEnabled())
                .proxyType(entity.getProxyType())
                .proxyHost(entity.getProxyHost())
                .proxyPort(entity.getProxyPort())
                .proxyUsername(entity.getProxyUsername())
                .enabled(entity.getEnabled())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .hasApiKey(StringUtils.hasText(entity.getApiKey()))
                .hasSecretKey(StringUtils.hasText(entity.getSecretKey()))
                .hasProxyPassword(StringUtils.hasText(entity.getProxyPassword()))
                .build();
    }

    /**
     * 加密非空字符串
     */
    private String encryptIfNotEmpty(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return encryptUtil.encrypt(value);
    }

    /**
     * 解密非空字符串
     */
    private String decryptIfNotEmpty(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return encryptUtil.decrypt(value);
    }

    /**
     * 校验交易所类型
     */
    private void validateExchangeType(String exchangeType) {
        try {
            ExchangeType.fromCode(exchangeType);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("不支持的交易所类型: " + exchangeType);
        }
    }

    /**
     * 校验代理类型
     */
    private void validateProxyType(String proxyType) {
        if (!StringUtils.hasText(proxyType)) {
            throw new BusinessException("启用代理时必须指定代理类型");
        }
        try {
            ProxyType.fromCode(proxyType);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("不支持的代理类型: " + proxyType);
        }
    }
}
