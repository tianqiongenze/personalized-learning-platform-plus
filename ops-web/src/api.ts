import axios from 'axios'

export const api = axios.create({ baseURL: '/api/v1/ops', timeout: 15000 })

// 鉴权: ops-web 无独立登录页, 复用学生端演示会话 —— localStorage 无 token 时
// 用演示账号向 iam 换 accessToken 并缓存(与 web-student ensureDemoSession 同源),
// 之后所有 /api/v1/ops 请求自动携带 Bearer(nginx auth_request → iam /verify)。
const TENANT_ID = 'da98138f-486f-42db-944c-219a5a44bf52'

async function ensureDemoToken(): Promise<string | null> {
  let token = localStorage.getItem('accessToken')
  if (token) return token
  try {
    const resp = await axios.post('/api/v1/auth/login',
      { username: 'admin', password: 'Admin@12345' },
      { headers: { 'X-Tenant-Id': TENANT_ID } })
    token = resp.data?.data?.accessToken ?? null
    if (token) localStorage.setItem('accessToken', token)
  } catch { /* 登录失败时不携带 token, 由后端返回 401 展示错误态 */ }
  return token
}

api.interceptors.request.use(async (config) => {
  const token = await ensureDemoToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})
// 401 时清除缓存 token, 下次请求重新登录(处理 token 过期)
api.interceptors.response.use(undefined, (err) => {
  if (err?.response?.status === 401) localStorage.removeItem('accessToken')
  return Promise.reject(err)
})

export interface Deployment {
  namespace: string; name: string; replicas: number; ready: number
  available: number; image: string; healthy: boolean
}
export interface Hpa {
  namespace: string; name: string; currentReplicas: number; desiredReplicas: number
  minReplicas: number; maxReplicas: number; metrics: { type: string; name?: string; current?: string }[]
  scalingActive: string
}
export interface CacheNode {
  node: string; reachable: boolean; usedMemoryHuman?: string; hitRatio?: number
  connectedClients?: string; instantaneousOpsPerSec?: string; evictedKeys?: string; error?: string
}

export const getOverview = () => api.get('/overview').then(r => r.data)
export const getServices = () => api.get<Deployment[]>('/services').then(r => r.data)
export const getHpa = () => api.get<Hpa[]>('/hpa').then(r => r.data)
export const getEvents = (ns = 'platform-plus') => api.get('/events', { params: { namespace: ns } }).then(r => r.data)
export const getRedisCluster = () => api.get<CacheNode[]>('/cache/redis').then(r => r.data)
export const getMilvusCollections = () => api.get('/cache/vector/collections').then(r => r.data)
export const getNebulaMetrics = () => api.get('/cache/graph/metrics').then(r => r.data)
export const getPulsarTopics = (tenant = 'public', ns = 'default') =>
  api.get('/pulsar/topics', { params: { tenant, namespace: ns } }).then(r => r.data)
export const getPulsarTopicStats = (topic: string) =>
  api.get('/pulsar/topics/stats', { params: { tenant: 'public', namespace: 'default', topic } }).then(r => r.data)
export const peekMessages = (topic: string, subscription: string, count = 10) =>
  api.get('/pulsar/topics/peek', { params: { tenant: 'public', namespace: 'default', topic, subscription, count } }).then(r => r.data)
export const getAlerts = () => api.get('/alerts').then(r => r.data)
export const queryPromql = (promql: string) => api.get('/metrics/query', { params: { promql } }).then(r => r.data)
export const getGrafanaEmbeds = () => api.get<{ [k: string]: string }>('/grafana/embeds').then(r => r.data)
export const getLokiLogs = (logql: string, limit = 200) =>
  api.get('/logs/loki', { params: { logql, limit } }).then(r => r.data)
export const setLogLevel = (serviceUrl: string, logger: string, level: string) =>
  api.post('/logs/level', { serviceUrl, logger, level }).then(r => r.data)
