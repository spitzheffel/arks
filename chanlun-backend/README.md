# 缠论交易分析平台 - 后端服务

基于 Spring Boot 3 的缠论交易分析平台后端服务。

## 技术栈

- **Java**: 21
- **框架**: Spring Boot 3.3.0
- **ORM**: MyBatis-Plus 3.5.7
- **数据库**: PostgreSQL 15+
- **数据库迁移**: Flyway 10.x
- **HTTP 客户端**: OkHttp 4.x

## 项目结构

```
src/main/java/com/chanlun/
├── ChanlunApplication.java    # 主应用类
├── config/                    # 配置类
├── controller/                # REST API 控制器
├── service/                   # 业务逻辑层
├── mapper/                    # MyBatis-Plus Mapper
├── entity/                    # 数据库实体
├── dto/                       # 数据传输对象
├── exchange/                  # 交易所客户端
├── scheduler/                 # 定时任务
└── util/                      # 工具类
```

## 快速开始

### 环境要求

- JDK 21+
- Maven 3.9+
- PostgreSQL 15+

### 配置数据库

1. 创建 PostgreSQL 数据库:

```sql
CREATE DATABASE chanlun;
CREATE USER chanlun WITH PASSWORD 'chanlun';
GRANT ALL PRIVILEGES ON DATABASE chanlun TO chanlun;
```

2. 配置环境变量 (可选):

```bash
export DB_USERNAME=chanlun
export DB_PASSWORD=chanlun
export ENCRYPTION_KEY=your-32-byte-encryption-key
```

### 运行项目

```bash
# 编译项目
mvn clean compile

# 运行测试
mvn test

# 启动应用
mvn spring-boot:run
```

### API 文档

启动后访问: http://localhost:8080/

## 开发规范

- 所有时间使用 UTC 时区存储和传输
- API 响应格式: `{ "code": 200, "message": "success", "data": {} }`
- 敏感数据 (API Key, Secret Key) 使用 AES-256 加密存储

## 测试

```bash
# 运行所有测试
mvn test

# 运行单个测试类
mvn test -Dtest=ChanlunApplicationTests
```
