/**
 * 系统配置类型定义
 */

/**
 * 系统配置
 */
export interface SystemConfig {
  id: number
  configKey: string
  configValue: string
  description: string
  createdAt: string
  updatedAt: string
}

/**
 * 配置更新请求
 */
export interface ConfigUpdateRequest {
  value: string
}

/**
 * 配置分组（用于前端展示）
 */
export interface ConfigGroup {
  name: string
  description: string
  prefix: string
  configs: SystemConfig[]
}
