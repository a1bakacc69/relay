import http from './http'

/** 注册：{ username, password, displayName } */
export const register = (data) => http.post('/auth/register', data)

/**
 * 登录：{ username, password }
 * 成功后返回 { token, username, displayName, role }
 */
export const login = (data) => http.post('/auth/login', data)
