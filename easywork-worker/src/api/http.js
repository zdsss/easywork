import axios from 'axios'
import { showToast } from 'vant'

const http = axios.create({
  baseURL: '/api',
  timeout: 10000,
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers['Authorization'] = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code !== 200) {
      // 乐观锁冲突特殊处理
      if (res.message && res.message.includes('modified by another user')) {
        showToast({
          message: '数据已被其他用户修改，请刷新后重试',
          duration: 3000,
        })
      } else {
        showToast(res.message || '请求失败')
      }
      return Promise.reject(new Error(res.message))
    }
    return res.data
  },
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      window.location.href = '/login'
    } else {
      showToast(error.message || '网络错误')
    }
    return Promise.reject(error)
  }
)

export default http
