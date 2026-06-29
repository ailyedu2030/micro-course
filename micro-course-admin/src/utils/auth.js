// 微课平台统一 token 存储抽象（遵循 SKILL.md §106：持久化用 localStorage）
// SEC-005 安全说明:
//   - localStorage 受同源策略保护，但易受 XSS 脚本读取(JWT accessToken 包含敏感 claims)
//   - 生产环境建议: 1) 配置 CSP header 'script-src' 严格白名单 2) 定期审计第三方依赖
//   - 长期方案: 迁移到 httpOnly cookie + BFF 代理模式(jwt->session)
//   - SPA 架构下 httpOnly cookie 需要后端配合: refreshToken 设为 httpOnly+Secure+SameSite=Strict
//   - 当前 refreshToken 存储方式与 accessToken 一致, 若需提升安全请优先迁移 refreshToken 到 httpOnly cookie
const TOKEN_KEY = 'micro_course_token'
const REFRESH_TOKEN_KEY = 'micro_course_refresh_token'

export function getToken() { return localStorage.getItem(TOKEN_KEY) }
export function setToken(token) { localStorage.setItem(TOKEN_KEY, token) }
export function removeToken() { localStorage.removeItem(TOKEN_KEY) }

export function getRefreshToken() { return localStorage.getItem(REFRESH_TOKEN_KEY) }
export function setRefreshToken(token) { localStorage.setItem(REFRESH_TOKEN_KEY, token) }
export function removeRefreshToken() { localStorage.removeItem(REFRESH_TOKEN_KEY) }

export function isAuthenticated() { return !!getToken() }