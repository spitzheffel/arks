# 缠论交易分析平台 - 前端

基于 Vue 3 + TypeScript + Vite 构建的缠论交易分析平台前端项目。

## 技术栈

- **框架**: Vue 3 (Composition API)
- **语言**: TypeScript
- **构建工具**: Vite
- **CSS**: Tailwind CSS (待配置)
- **状态管理**: Pinia (待配置)
- **路由**: Vue Router (待配置)
- **HTTP 客户端**: Axios (待配置)
- **图表**: ECharts (待配置)

## 项目结构

```
src/
├── api/          # API 接口封装
├── assets/       # 静态资源
├── components/   # 公共组件
├── router/       # 路由配置
├── stores/       # Pinia 状态管理
├── types/        # TypeScript 类型定义
├── utils/        # 工具函数
├── views/        # 页面组件
├── App.vue       # 根组件
└── main.ts       # 入口文件
```

## 开发命令

```bash
# 安装依赖
npm install

# 启动开发服务器
npm run dev

# 构建生产版本
npm run build

# 预览生产构建
npm run preview
```

## 开发规范

- 使用 Composition API (`<script setup>`)
- 组件文件使用 PascalCase 命名
- API 调用封装在 `src/api/` 目录
- 使用 TypeScript 类型定义
- 响应式布局，桌面优先
