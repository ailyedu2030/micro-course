const TOKEN_KEY = 'micro_course_token'

export function getToken() { return sessionStorage.getItem(TOKEN_KEY) }
export function setToken(token) { sessionStorage.setItem(TOKEN_KEY, token) }
export function removeToken() { sessionStorage.removeItem(TOKEN_KEY) }
export function isAuthenticated() { return !!getToken() }