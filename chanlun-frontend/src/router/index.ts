import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'Home',
    component: () => import('@/views/HomeView.vue'),
    meta: { title: '首页' }
  },
  {
    path: '/datasources',
    name: 'DataSources',
    component: () => import('@/views/datasource/DataSourceList.vue'),
    meta: { title: '数据源管理' }
  },
  {
    path: '/markets',
    name: 'Markets',
    component: () => import('@/views/market/MarketList.vue'),
    meta: { title: '市场管理' }
  },
  {
    path: '/symbols',
    name: 'symbols',
    component: () => import('@/views/symbol/SymbolList.vue'),
    meta: { title: '交易对管理' }
  },
  {
    path: '/sync',
    name: 'Sync',
    component: () => import('@/views/sync/SyncTaskList.vue'),
    meta: { title: '数据同步' }
  },
  {
    path: '/gaps',
    name: 'Gaps',
    component: () => import('@/views/gap/GapList.vue'),
    meta: { title: '缺口管理' }
  },
  {
    path: '/config',
    name: 'Config',
    component: () => import('@/views/config/ConfigList.vue'),
    meta: { title: '系统配置' }
  }
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes
})

// 路由守卫 - 设置页面标题
router.beforeEach((to, _from, next) => {
  const title = to.meta.title as string
  document.title = title ? `${title} - 缠论交易分析平台` : '缠论交易分析平台'
  next()
})

export default router
