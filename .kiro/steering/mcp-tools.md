# MCP 工具使用指南

本文档描述可用的 MCP 工具及其使用场景，帮助 AI 在合适的时机主动调用。

---

## 代码搜索与理解

### Augment Context Engine (codebase-retrieval)

**用途**: 语义化代码搜索，理解代码库结构

**何时使用**:
- 不知道某个功能在哪个文件实现
- 需要了解代码库整体架构
- 查找某个类/函数的定义和用法
- 开始新任务前了解相关代码

**示例**:
```
mcp_augment_context_engine_codebase_retrieval
- "用户认证是如何实现的？"
- "K线数据存储在哪里？"
- "WebSocket 连接管理的代码在哪？"
```

---

## 文档查询

### Context7 (resolve-library-id / query-docs)

**用途**: 查询编程库/框架的最新文档

**何时使用**:
- 需要查询 Spring Boot、Vue、MyBatis-Plus 等框架用法
- 不确定某个 API 的参数或返回值
- 需要最新版本的使用示例

**使用流程**:
1. 先用 `resolve-library-id` 获取库 ID
2. 再用 `query-docs` 查询具体问题

**示例**:
```
mcp_context7_resolve_library_id: libraryName="mybatis-plus", query="如何配置分页插件"
mcp_context7_query_docs: libraryId="/baomidou/mybatis-plus", query="分页插件配置"
```

### DeepWiki (deepwiki_fetch)

**用途**: 获取 GitHub 仓库的文档和说明

**何时使用**:
- 需要了解某个开源项目的使用方法
- 查看项目的 README 和文档

**示例**:
```
mcp_mcp_deepwiki_deepwiki_fetch: url="ta4j/ta4j"
mcp_mcp_deepwiki_deepwiki_fetch: url="binance/binance-connector-java"
```

---

## 数据库操作

### MySQL MCP

**用途**: 连接和操作 MySQL 数据库

**可用操作**:
- `mcp_mysql_connect_db` - 连接数据库
- `mcp_mysql_list_tables` - 列出所有表
- `mcp_mysql_describe_table` - 查看表结构
- `mcp_mysql_query` - 执行 SELECT 查询
- `mcp_mysql_execute` - 执行 INSERT/UPDATE/DELETE

**何时使用**:
- 需要查看数据库表结构
- 验证数据是否正确写入
- 调试数据相关问题

---

## API 文档

### govmApi (OpenAPI Spec)

**用途**: 读取项目的 OpenAPI 规范文件

**可用操作**:
- `mcp_govmApi_read_project_oas_y8eajr` - 读取 OpenAPI Spec
- `mcp_govmApi_read_project_oas_ref_resources_y8eajr` - 读取 $ref 引用的资源
- `mcp_govmApi_refresh_project_oas_y8eajr` - 刷新最新的 OpenAPI Spec

**何时使用**:
- 需要了解 API 接口定义
- 生成 API 客户端代码
- 验证 API 实现是否符合规范

---

## 浏览器自动化

### Chrome DevTools MCP

**用途**: 控制 Chrome 浏览器进行自动化测试和调试

**常用操作**:
- `mcp_chrome_devtools_new_page` - 打开新页面
- `mcp_chrome_devtools_navigate_page` - 导航到 URL
- `mcp_chrome_devtools_take_snapshot` - 获取页面快照
- `mcp_chrome_devtools_take_screenshot` - 截图
- `mcp_chrome_devtools_click` - 点击元素
- `mcp_chrome_devtools_fill` - 填写表单

**何时使用**:
- 前端页面调试
- E2E 测试
- 验证页面功能

---

## 时间工具

### Time MCP

**用途**: 获取和转换时区时间

**可用操作**:
- `mcp_mcp_server_time_get_current_time` - 获取指定时区当前时间
- `mcp_mcp_server_time_convert_time` - 时区转换

**何时使用**:
- 处理 UTC 时间转换
- 验证定时任务时间

---

## 使用原则

1. **主动使用**: 遇到不确定的技术问题时，优先使用 MCP 工具查询，而不是凭记忆回答
2. **验证信息**: 提供技术建议前，用 Context7 或 DeepWiki 验证文档是否最新
3. **代码搜索**: 修改代码前，先用 codebase-retrieval 了解现有实现
4. **数据库调试**: 数据问题优先用 MySQL MCP 直接查询验证
