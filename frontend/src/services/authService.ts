import axios, { type AxiosInstance } from 'axios'
import { apiBaseUrl } from './apiConfig'

export interface AuthUser { id: number; email: string; nom: string }
interface Session { accessToken: string; expiresAt: number; user: AuthUser }
const key = 'eventflow-session'
const api = axios.create({ baseURL: apiBaseUrl })
export const clearSession = () => { sessionStorage.removeItem(key); window.dispatchEvent(new Event('eventflow-session')) }
export const getSession = (): Session | null => {
  try {
    const value = JSON.parse(sessionStorage.getItem(key) || 'null') as Session | null
    return value?.accessToken && value.expiresAt > Date.now() ? value : null
  } catch { return null }
}
export const login = async (email: string, password: string) => {
  const { data } = await api.post<{ accessToken: string; expiresIn: number; user: AuthUser }>('/auth/login', { email, password })
  const session = { accessToken: data.accessToken, expiresAt: Date.now() + data.expiresIn * 1000, user: data.user }
  sessionStorage.setItem(key, JSON.stringify(session))
  window.dispatchEvent(new Event('eventflow-session'))
  return session
}
export const secureApi = (client: AxiosInstance) => {
  client.interceptors.request.use(config => {
    const session = getSession()
    if (session) config.headers.Authorization = `Bearer ${session.accessToken}`
    return config
  })
  client.interceptors.response.use(response => response, error => {
    if (error.response?.status === 401) clearSession()
    return Promise.reject(error)
  })
}
