// 微课平台统一 token 存储抽象（遵循 SKILL.md §106：持久化用 localStorage）
const TOKEN_KEY = 'micro_course_token'
const REFRESH_TOKEN_KEY = 'micro_course_refresh_token'

export function getToken() { return localStorage.getItem(TOKEN_KEY) }
export function setToken(token) { localStorage.setItem(TOKEN_KEY, token) }
export function removeToken() { localStorage.removeItem(TOKEN_KEY) }

export function getRefreshToken() { return localStorage.getItem(REFRESH_TOKEN_KEY) }
export function setRefreshToken(token) { localStorage.setItem(REFRESH_TOKEN_KEY, token) }
export function removeRefreshToken() { localStorage.removeItem(REFRESH_TOKEN_KEY) }

export function isAuthenticated() { return !!getToken() }