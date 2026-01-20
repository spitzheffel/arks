# 缠论交易分析平台 - 技术规范

## 技术栈

### 后端
- **语言**: Java 21 (路径: `D:\Env\java\jdk-21.0.9`)
- **框架**: Spring Boot 3.x
- **ORM**: MyBatis-Plus
- **数据库**: PostgreSQL 15+
- **数据库迁移**: Flyway
- **HTTP 客户端**: OkHttp (支持代理)
- **加密**: AES-256-GCM

### 前端
- **框架**: Vue 3 + TypeScript
- **构建工具**: Vite
- **CSS**: Tailwind CSS
- **图表**: ECharts
- **状态管理**: Pinia
- **HTTP 客户端**: Axios
- **路由**: Vue Router

---

## 编码规范

### Java 后端

- 使用 Lombok 简化 POJO
- Controller 层只做参数校验和响应封装
- Service 层处理业务逻辑
- 统一响应格式: `{ code, message, data }`
- 异常统一处理，返回友好错误信息
- 敏感数据（API Key、密码）必须加密存储

### Vue 前端

- 使用 Composition API (`<script setup>`)
- 组件文件使用 PascalCase 命名
- API 调用封装在 `src/api/` 目录
- 使用 TypeScript 类型定义
- 响应式布局，桌面优先（兼容移动端）

---

## 质量门槛

- 后端：`mvn -q test` 必须通过；静态检查/格式化不得有阻断级问题
- 前端：必须提供并通过 `lint`/`typecheck`/`test`/`test:e2e` 脚本（包管理器不限，E2E 使用 Playwright，仅 Chromium）
- 数据库：Flyway `validate`/`migrate` 失败禁止启动
- 变更控制：接口/数据结构变更必须同步更新文档与测试
- 交付要求：合并/发布前必须跑完上述检查

示例命令（包管理器任选）:

```
mvn -q test
npm run lint
npm run typecheck
npm run test
npm run test:e2e
```

---

## 测试规范

- 后端：使用 JUnit 5 + Mockito；核心业务逻辑必须有单测；外部依赖使用 Mock
- 前端：关键组件/交互有组件测试；关键流程至少有最小 E2E 冒烟（浏览器端，Playwright，仅 Chromium）
- E2E 环境：本地启动后端+前端，Mock 规则仅覆盖交易所等外部 API，内部 API 与数据库走真实链路；禁止依赖真实交易所线上数据
- Mock 规则开关：通过环境变量 `EXCHANGE_API_MOCK=true` 启用，仅用于本地/E2E，默认关闭

---

## 数据库规范

### 时区
- 所有时间字段使用 `TIMESTAMPTZ` 类型
- 存储 UTC 时间
- API 传输使用 ISO 8601 格式 (带 Z 后缀)
- 前端负责转换为本地时区显示

### 命名
- 表名: snake_case (如 `data_source`)
- 字段名: snake_case (如 `created_at`)
- 索引名: `idx_表名_字段名`
- 唯一约束: `uk_表名_字段名`

### 软删除
- 使用 `deleted` (BOOLEAN) + `deleted_at` (TIMESTAMPTZ)
- 查询时默认过滤已删除记录

---

## API 规范

### RESTful 设计
- GET: 查询
- POST: 创建 / 触发操作
- PUT: 全量更新
- PATCH: 部分更新
- DELETE: 删除

### 路径规范
- 基础路径: `/api/v1/`
- 资源名使用复数: `/datasources`, `/markets`, `/symbols`
- 子资源: `/markets/{id}/sync-symbols`

### 分页参数
- `page`: 页码，从 1 开始
- `size`: 每页数量，默认 20，最大 100

---

## K线周期

支持的周期（不支持 1s）:
`1m, 3m, 5m, 15m, 30m, 1h, 2h, 4h, 6h, 8h, 12h, 1d, 3d, 1w, 1M`

---

## 定时任务

- 使用 Spring Scheduler
- 时区统一为 UTC
- Cron 表达式从 `system_config` 表读取
- 支持动态刷新配置

---

## 安全规范

- API Key / Secret Key 使用 AES-256 加密存储
- 代理密码加密存储
- 敏感操作需前端二次确认
- 本系统为个人使用，暂不实现用户认证


---

## 参考资源

### 币安 API 文档

| 市场类型 | 官方文档 | GitHub 文档 |
|---------|---------|-------------|
| 现货 (SPOT) | https://developers.binance.com/docs/binance-spot-api-docs/README | https://github.com/binance/binance-spot-api-docs |
| U本位合约 (USDT_M) | https://developers.binance.com/docs/derivatives/usds-margined-futures/general-info | - |
| 币本位合约 (COIN_M) | https://developers.binance.com/docs/derivatives/coin-margined-futures/general-info | - |

### 框架与库文档

| 技术 | 官方文档 |
|------|---------|
| Spring Boot 3 | https://docs.spring.io/spring-boot/docs/current/reference/html/ |
| MyBatis-Plus | https://baomidou.com/ |
| Flyway | https://documentation.red-gate.com/flyway |
| Vue 3 | https://cn.vuejs.org/guide/introduction.html |
| Pinia | https://pinia.vuejs.org/zh/ |
| Tailwind CSS | https://tailwindcss.com/docs |
| ECharts | https://echarts.apache.org/zh/option.html |
| OkHttp | https://square.github.io/okhttp/ |

### 缠论参考资料

- 缠中说禅博客备份: https://github.com/wodewoshiwo/chzhshch-blog
- 缠论技术分析要点: 笔、线段、中枢、背驰、买卖点

### Java 开源库

| 库名 | 用途 | GitHub |
|------|------|--------|
| ta4j | 技术分析库，200+ 指标 (RSI/MACD/ATR 等)，回测框架 | <https://github.com/ta4j/ta4j> |
| binance-connector-java | 币安官方 Java SDK，支持现货/合约/WebSocket | <https://github.com/binance/binance-connector-java> |
| XChange | 多交易所统一 API (60+ 交易所)，可作为抽象层参考 | <https://github.com/knowm/XChange> |

### 缠论参考项目

| 项目 | 语言 | 说明 | GitHub |
|------|------|------|--------|
| czsc | Python | 4k+ stars，活跃维护，笔/线段/中枢算法实现 | <https://github.com/waditu/czsc> |

注：Java 暂无成熟的缠论开源库，可参考 czsc 的算法逻辑用 Java 重新实现

---

## ta4j 使用指南 (Phase 2/3)

### Maven 依赖

```xml
<dependency>
    <groupId>org.ta4j</groupId>
    <artifactId>ta4j-core</artifactId>
    <version>0.16</version>
</dependency>
```

### 核心概念

- **Bar**: 单根 K 线数据（OHLCV + 时间）
- **BarSeries**: K 线序列，ta4j 所有计算的基础
- **Indicator**: 技术指标，基于 BarSeries 计算
- **Rule**: 交易规则，用于策略定义
- **Strategy**: 交易策略，由入场/出场规则组成

### K线数据转换

将本系统的 Kline 实体转换为 ta4j 的 BarSeries：

```java
import org.ta4j.core.*;
import org.ta4j.core.num.DecimalNum;
import java.time.ZonedDateTime;
import java.util.List;

public class Ta4jConverter {
    
    public static BarSeries toBarSeries(String name, List<Kline> klines) {
        BarSeries series = new BaseBarSeriesBuilder()
            .withName(name)
            .withNumTypeOf(DecimalNum.class)
            .build();
        
        for (Kline k : klines) {
            ZonedDateTime time = k.getOpenTime().atZone(ZoneOffset.UTC);
            series.addBar(
                time,
                DecimalNum.valueOf(k.getOpen()),
                DecimalNum.valueOf(k.getHigh()),
                DecimalNum.valueOf(k.getLow()),
                DecimalNum.valueOf(k.getClose()),
                DecimalNum.valueOf(k.getVolume())
            );
        }
        return series;
    }
}
```

### 常用指标示例

```java
import org.ta4j.core.indicators.*;
import org.ta4j.core.indicators.helpers.*;

// RSI (14周期)
RSIIndicator rsi = new RSIIndicator(new ClosePriceIndicator(series), 14);

// MACD (12, 26, 9)
ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
MACDIndicator macd = new MACDIndicator(closePrice, 12, 26);
EMAIndicator signal = new EMAIndicator(macd, 9);

// 布林带 (20周期, 2倍标准差)
SMAIndicator sma = new SMAIndicator(closePrice, 20);
StandardDeviationIndicator sd = new StandardDeviationIndicator(closePrice, 20);
BollingerBandsMiddleIndicator bbMiddle = new BollingerBandsMiddleIndicator(sma);
BollingerBandsUpperIndicator bbUpper = new BollingerBandsUpperIndicator(bbMiddle, sd);
BollingerBandsLowerIndicator bbLower = new BollingerBandsLowerIndicator(bbMiddle, sd);

// ATR (14周期)
ATRIndicator atr = new ATRIndicator(series, 14);

// 获取最新值
Num latestRsi = rsi.getValue(series.getEndIndex());
```

### Phase 2/3 集成建议

1. **Phase 2 (缠论分析)**: 缠论算法需自行实现，但可复用 ta4j 的 BarSeries 作为数据结构
2. **Phase 3 (常规指标)**: 直接使用 ta4j 的 200+ 内置指标，无需重复造轮子
3. **回测框架**: ta4j 提供完整的回测支持，Phase 4 可直接使用

---

## czsc 缠论算法参考 (Phase 2)

### 核心模块

czsc 项目的核心算法位于以下模块，实现 Java 版本时可参考：

| 模块 | 文件 | 说明 |
|------|------|------|
| K线处理 | `czsc/analyze.py` | 包含合并、分型识别 |
| 笔 | `czsc/objects.py` | BI 类定义，笔的识别逻辑 |
| 线段 | `czsc/objects.py` | XD 类定义，线段的识别逻辑 |
| 中枢 | `czsc/objects.py` | ZS 类定义，中枢的识别逻辑 |
| 买卖点 | `czsc/signals/` | 各类买卖点信号 |

### 缠论核心概念

1. **分型 (FenXing)**
   - 顶分型：中间K线高点最高
   - 底分型：中间K线低点最低
   - 需要处理包含关系

2. **笔 (Bi)**
   - 顶分型到底分型 = 向下笔
   - 底分型到顶分型 = 向上笔
   - 至少包含5根K线

3. **线段 (XianDuan)**
   - 至少3笔构成
   - 线段破坏的判定

4. **中枢 (ZhongShu)**
   - 至少3个连续次级别走势重叠区间
   - 中枢的扩展与新生

5. **背驰 (BeiChi)**
   - 趋势背驰
   - 盘整背驰
   - MACD 辅助判断

### Java 实现建议

```java
// 数据结构定义示例
public enum FenXingType { TOP, BOTTOM }

public class FenXing {
    private Long id;
    private FenXingType type;
    private Kline kline;        // 分型的中间K线
    private Instant time;
    private BigDecimal high;
    private BigDecimal low;
}

public enum BiDirection { UP, DOWN }

public class Bi {
    private Long id;
    private BiDirection direction;
    private FenXing start;
    private FenXing end;
    private List<Kline> klines;
    private BigDecimal high;
    private BigDecimal low;
}

public class ZhongShu {
    private Long id;
    private List<Bi> bis;       // 构成中枢的笔
    private BigDecimal zg;      // 中枢高点
    private BigDecimal zd;      // 中枢低点
    private BigDecimal gg;      // 最高点
    private BigDecimal dd;      // 最低点
}
```

### 算法实现优先级

Phase 2 建议按以下顺序实现：

1. K线包含处理 → 2. 分型识别 → 3. 笔的划分 → 4. 线段识别 → 5. 中枢识别 → 6. 背驰判断 → 7. 买卖点信号

### 参考资源

- czsc 源码: <https://github.com/waditu/czsc>
- czsc 文档: <https://czsc.readthedocs.io/>
- 缠中说禅博客: <https://github.com/wodewoshiwo/chzhshch-blog>
