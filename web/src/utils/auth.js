/**
 * 登录态的本地存储。
 *
 * <p>为什么用 localStorage 不用 cookie：
 * 后端是 Bearer token，不是 session cookie，token 由前端主动放进请求头。
 * 代价是 XSS 能偷到它 —— 所以前端绝不能用 v-html 渲染用户输入。
 *
 * <p>user 只存展示用的信息（用户名、显示名、角色），不存密码，
 * 也不拿它做任何权限判断 —— 真正的权限在服务端按 token 里的角色判。
 */

const TOKEN_KEY = 'relay_token'
const USER_KEY = 'relay_user'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function getStoredUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  } catch {
    // 存进去的东西被人手动改坏了，当作没登录
    return null
  }
}

export function saveAuth(token, user) {
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

export function clearAuth() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

export function isLoggedIn() {
  return !!getToken()
}
