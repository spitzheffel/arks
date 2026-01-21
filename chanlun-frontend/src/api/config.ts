/**
 * 系统配置 API 模块
 */
import { get, put } from './request'
import type { SystemConfig, ConfigUpdateRequest } from '@/types'

const BASE_URL = '/config'

/**
 * 获取所有配置
 */
export async function getAllConfigs(): Promise<SystemConfig[]> {
  return get<SystemConfig[]>(BASE_URL)
}

/**
 * 获取单个配置
 */
export async function getConfig(key: string): Promise<SystemConfig> {
  return get<SystemConfig>(`${BASE_URL}/${key}`)
}

/**
 * 更新配置
 */
export async function updateConfig(key: string, data: ConfigUpdateRequest): Promise<SystemConfig> {
  return put<SystemConfig>(`${BASE_URL}/${key}`, data)
}
