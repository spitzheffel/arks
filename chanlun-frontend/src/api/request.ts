import axios, { type AxiosInstance, type AxiosRequestConfig, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import type { ApiResponse } from '@/types'

/**
 * 请求配置
 */
const DEFAULT_CONFIG: AxiosRequestConfig = {
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
}

/**
 * 创建 Axios 实例
 */
const instance: AxiosInstance = axios.create(DEFAULT_CONFIG)

/**
 * 请求拦截器
 */
instance.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // 可在此添加 token 等认证信息（当前系统暂不需要）
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

/**
 * 响应拦截器
 */
instance.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    const { data } = response
    // 业务状态码判断
    if (data.code !== 200) {
      return Promise.reject(new Error(data.message || '请求失败'))
    }
    return response
  },
  (error) => {
    // 网络错误或服务器错误
    let message = '网络错误，请稍后重试'
    if (error.response) {
      const { status, data } = error.response
      switch (status) {
        case 400:
          message = data?.message || '请求参数错误'
          break
        case 404:
          message = data?.message || '请求的资源不存在'
          break
        case 500:
          message = data?.message || '服务器内部错误'
          break
        default:
          message = data?.message || `请求失败 (${status})`
      }
    } else if (error.code === 'ECONNABORTED') {
      message = '请求超时，请稍后重试'
    }
    return Promise.reject(new Error(message))
  }
)

/**
 * 封装 GET 请求
 */
export async function get<T>(url: string, params?: Record<string, unknown>, config?: AxiosRequestConfig): Promise<T> {
  const response = await instance.get<ApiResponse<T>>(url, { params, ...config })
  return response.data.data
}

/**
 * 封装 POST 请求
 */
export async function post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  const response = await instance.post<ApiResponse<T>>(url, data, config)
  return response.data.data
}

/**
 * 封装 PUT 请求
 */
export async function put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  const response = await instance.put<ApiResponse<T>>(url, data, config)
  return response.data.data
}

/**
 * 封装 PATCH 请求
 */
export async function patch<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  const response = await instance.patch<ApiResponse<T>>(url, data, config)
  return response.data.data
}

/**
 * 封装 DELETE 请求
 */
export async function del<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  const response = await instance.delete<ApiResponse<T>>(url, config)
  return response.data.data
}

/**
 * 导出 axios 实例（用于特殊场景）
 */
export { instance as axiosInstance }

/**
 * 默认导出所有方法
 */
export default {
  get,
  post,
  put,
  patch,
  delete: del
}
