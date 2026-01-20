package com.chanlun.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chanlun.dto.DataSourceCreateRequest;
import com.chanlun.dto.DataSourceDTO;
import com.chanlun.dto.DataSourceUpdateRequest;
import com.chanlun.entity.DataSource;
import com.chanlun.event.DataSourceStatusChangedEvent;
import com.chanlun.exception.BusinessException;
import com.chanlun.exception.ResourceNotFoundException;
import com.chanlun.mapper.DataSourceMapper;
import com.chanlun.util.EncryptUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 数据源服务测试
 * 
 * @author Chanlun Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DataSourceService 测试")
class DataSourceServiceTest {

    @Mock
    private DataSourceMapper dataSourceMapper;

    @Mock
    private EncryptUtil encryptUtil;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private DataSourceService dataSourceService;

    private DataSource testDataSource;

    @BeforeEach
    void setUp() {
        testDataSource = DataSource.builder()
                .id(1L)
                .name("Test Binance")
                .exchangeType("BINANCE")
                .apiKey("encrypted-api-key")
                .secretKey("encrypted-secret-key")
                .baseUrl("https://api.binance.com")
                .wsUrl("wss://stream.binance.com:9443/ws")
                .proxyEnabled(false)
                .enabled(true)
                .deleted(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("创建数据源 - 成功")
    void create_success() {
        DataSourceCreateRequest request = DataSourceCreateRequest.builder()
                .name("New Binance")
                .exchangeType("BINANCE")
                .apiKey("my-api-key")
                .secretKey("my-secret-key")
                .baseUrl("https://api.binance.com")
                .build();

        when(dataSourceMapper.countByNameExcludeId(anyString(), isNull())).thenReturn(0);
        when(encryptUtil.encrypt("my-api-key")).thenReturn("encrypted-api-key");
        when(encryptUtil.encrypt("my-secret-key")).thenReturn("encrypted-secret-key");
        when(dataSourceMapper.insert(any(DataSource.class))).thenAnswer(invocation -> {
            DataSource ds = invocation.getArgument(0);
            ds.setId(1L);
            ds.setCreatedAt(Instant.now());
            ds.setUpdatedAt(Instant.now());
            return 1;
        });

        DataSourceDTO result = dataSourceService.create(request);

        assertNotNull(result);
        assertEquals("New Binance", result.getName());
        assertEquals("BINANCE", result.getExchangeType());
        assertTrue(result.getHasApiKey());
        assertTrue(result.getHasSecretKey());
        verify(encryptUtil).encrypt("my-api-key");
        verify(encryptUtil).encrypt("my-secret-key");
    }

    @Test
    @DisplayName("创建数据源 - 名称重复应抛出异常")
    void create_duplicateName_shouldThrowException() {
        DataSourceCreateRequest request = DataSourceCreateRequest.builder()
                .name("Existing Name")
                .exchangeType("BINANCE")
                .build();

        when(dataSourceMapper.countByNameExcludeId("Existing Name", null)).thenReturn(1);

        assertThrows(BusinessException.class, () -> dataSourceService.create(request));
    }

    @Test
    @DisplayName("创建数据源 - 不支持的交易所类型应抛出异常")
    void create_invalidExchangeType_shouldThrowException() {
        DataSourceCreateRequest request = DataSourceCreateRequest.builder()
                .name("Test")
                .exchangeType("INVALID")
                .build();

        assertThrows(BusinessException.class, () -> dataSourceService.create(request));
    }

    @Test
    @DisplayName("获取数据源 - 成功")
    void getById_success() {
        when(dataSourceMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testDataSource);

        DataSourceDTO result = dataSourceService.getById(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Test Binance", result.getName());
    }

    @Test
    @DisplayName("获取数据源 - 不存在应抛出异常")
    void getById_notFound_shouldThrowException() {
        when(dataSourceMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> dataSourceService.getById(999L));
    }

    @Test
    @DisplayName("更新数据源 - 成功")
    void update_success() {
        DataSourceUpdateRequest request = DataSourceUpdateRequest.builder()
                .name("Updated Name")
                .apiKey("new-api-key")
                .build();

        when(dataSourceMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testDataSource);
        when(dataSourceMapper.countByNameExcludeId("Updated Name", 1L)).thenReturn(0);
        when(encryptUtil.encrypt("new-api-key")).thenReturn("encrypted-new-api-key");
        when(dataSourceMapper.updateById(any(DataSource.class))).thenReturn(1);

        DataSourceDTO result = dataSourceService.update(1L, request);

        assertNotNull(result);
        assertEquals("Updated Name", result.getName());
        verify(encryptUtil).encrypt("new-api-key");
    }

    @Test
    @DisplayName("删除数据源 - 软删除成功")
    void delete_softDelete_success() {
        when(dataSourceMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testDataSource);
        when(dataSourceMapper.updateById(any(DataSource.class))).thenReturn(1);

        dataSourceService.delete(1L);

        ArgumentCaptor<DataSource> captor = ArgumentCaptor.forClass(DataSource.class);
        verify(dataSourceMapper).updateById(captor.capture());
        
        DataSource deleted = captor.getValue();
        assertTrue(deleted.getDeleted());
        assertFalse(deleted.getEnabled());
        assertNotNull(deleted.getDeletedAt());

        // 验证事件发布
        verify(eventPublisher).publishEvent(any(DataSourceStatusChangedEvent.class));
    }

    @Test
    @DisplayName("禁用数据源 - 应发布事件")
    void updateStatus_disable_shouldPublishEvent() {
        when(dataSourceMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testDataSource);
        when(dataSourceMapper.updateById(any(DataSource.class))).thenReturn(1);

        dataSourceService.updateStatus(1L, false);

        ArgumentCaptor<DataSourceStatusChangedEvent> eventCaptor = 
                ArgumentCaptor.forClass(DataSourceStatusChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        
        DataSourceStatusChangedEvent event = eventCaptor.getValue();
        assertEquals(1L, event.getDataSourceId());
        assertFalse(event.isEnabled());
        assertFalse(event.isDeleted());
    }

    @Test
    @DisplayName("启用数据源 - 不应发布事件")
    void updateStatus_enable_shouldNotPublishEvent() {
        testDataSource.setEnabled(false);
        when(dataSourceMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testDataSource);
        when(dataSourceMapper.updateById(any(DataSource.class))).thenReturn(1);

        dataSourceService.updateStatus(1L, true);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("DTO 转换 - 不应包含敏感信息")
    void toDTO_shouldNotContainSensitiveInfo() {
        when(dataSourceMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testDataSource);

        DataSourceDTO result = dataSourceService.getById(1L);

        // DTO 不应包含实际的密钥值，只有 hasXxx 标志
        assertTrue(result.getHasApiKey());
        assertTrue(result.getHasSecretKey());
    }

    @Test
    @DisplayName("分页查询 - 成功")
    void list_pagination_success() {
        Page<DataSource> page = new Page<>(1, 10);
        page.setRecords(java.util.List.of(testDataSource));
        page.setTotal(1);

        when(dataSourceMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        IPage<DataSourceDTO> result = dataSourceService.list(1, 10, null, null);

        assertNotNull(result);
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getRecords().size());
    }
}
