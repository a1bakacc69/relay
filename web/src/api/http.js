import axios from 'axios'
import { ElMessage } from 'element-plus'
import { getToken, clearAuth } from '../utils/auth'

// 走 /api 前缀，由 Vite 代理到 Relay 后端（8080）。
// 浏览器直连会跨域，代理是开发期的标准解法，也省得后端加 CORS。
const http = axios.create({
  baseURL: '/api',
  timeout: 30000
})

/**
 * 每个请求自动带上 token。
 *
 * 放在拦截器里而不是每个接口函数各写一遍：
 * 以后新增一百个接口也不会漏，登录状态也只有这一个地方在读。
 */
http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/**
 * 后端统一返回 R：{ code, msg, data }。
 *
 * 关键在于 —— **HTTP 状态码永远是 200**，成功失败要看 code。
 * 这和 Relay 调供应商那边是同一个契约，也是同一个坑：
 * 只看 HTTP 200 就以为成了，出错时会拿着一堆 null 继续往下走。
 *
 * 所以这里先把 R 拆开：成功就把 data 交给调用方，失败就弹后端的 msg。
 * 页面里拿到的永远是干净的业务数据，不用每处都写 if (res.code === 200)。
 */
http.interceptors.response.use(
  (response) => {
    const r = response.data

    // 不是 R 结构的东西（比如某个接口直接返回字符串），原样放行
    if (r === null || typeof r === 'undefined' || typeof r.code === 'undefined') {
      return r
    }

    if (r.code === 200) {
      return r.data
    }

    // 这句 msg 是后端一层层传上来的 —— 供应商说什么、Relay 就转达什么。
    // 直接弹给用户，比自己编一句「操作失败」有用得多
    ElMessage.error(r.msg || '操作失败')
    return Promise.reject(new Error(r.msg || '操作失败'))
  },
  (error) => {
    const status = error.response?.status

    /**
     * 401 有两个来源，别混：
     *   - HTTP 401：Security 过滤器拦下来的，说明 token 没了 / 过期了 / 是伪造的 → 踢回登录页
     *   - HTTP 200 + body.code 401：业务层拒绝，比如登录密码填错 → 只弹消息，不能跳转
     *
     * 后者走的是上面的成功回调，根本到不了这里。
     */
    if (status === 401) {
      clearAuth()
      // 已经在登录页就别再跳了，否则页面会反复刷新
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
      return Promise.reject(error)
    }

    const msg = error.response
      ? `请求失败（HTTP ${status}）`
      : '连不上后端，确认 8080 起来了没'
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

export default http
