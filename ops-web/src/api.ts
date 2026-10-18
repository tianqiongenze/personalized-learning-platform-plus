import axios from 'axios'

export const api = axios.create({ baseURL: '/api/v1/ops', timeout: 15000 })

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
