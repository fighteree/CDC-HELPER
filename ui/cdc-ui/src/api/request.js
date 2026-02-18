import axios from 'axios'
import { ElMessage } from 'element-plus'

/**
 * Axios 实例统一封装：
 * - baseURL 走 Vite proxy（/api -> http://localhost:8080）
 * - 统一超时
 * - 统一错误提示（后端 message 优先）
 */
const instance = axios.create({
  baseURL: '/api',
  timeout: 10000,
})

instance.interceptors.response.use(
  (response) => response.data,
  (error) => {
    const msg =
      error?.response?.data?.message ||
      error?.message ||
      '请求发生错误，请稍后重试'
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

export default instance

