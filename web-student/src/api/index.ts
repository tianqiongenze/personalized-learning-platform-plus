/**
 * 统一 API 契约层: GraphQL 查询 + agent REST
 * 与三端共享同一契约(docs/MULTI_CLIENT_DESIGN_SPEC.md §2.1 ADR-021)
 */
import axios from 'axios'

export const http = axios.create({ baseURL: '/agent', timeout: 30000 })

/** GraphQL(经 nginx /gateway) */
export async function gql<T = any>(query: string, variables: Record<string, unknown> = {}): Promise<T> {
  const r = await axios.post('/gateway/graphql', { query, variables }, { timeout: 30000 })
  if (r.data.errors) throw new Error(r.data.errors[0]?.message || 'graphql error')
  return r.data.data as T
}

/** AG-UI 标准事件流(fetch-SSE; 三端统一事件消费) */
export async function aguiRun(message: string, onEvent: (ev: any) => void): Promise<void> {
  const resp = await fetch('/agent/agui/runs', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ message, pageContext: location.pathname, studentId: 'guest' }),
  })
  const reader = resp.body!.getReader()
  const decoder = new TextDecoder()
  let buf = ''
  for (;;) {
    const { done, value } = await reader.read()
    if (done) break
    buf += decoder.decode(value, { stream: true })
    let idx
    while ((idx = buf.indexOf('\n\n')) >= 0) {
      const line = buf.slice(0, idx).trim()
      buf = buf.slice(idx + 2)
      if (line.startsWith('data: ')) {
        try { onEvent(JSON.parse(line.slice(6))) } catch { /* 忽略坏帧 */ }
      }
    }
  }
}

/** 世界模型 what-if(学情推演) */
export function worldWhatIf(masteries: Record<string, number>, knowledgePoint: string, prereqs: string[] = []) {
  return http.post('/api/v1/world/what_if', { masteries, knowledgePoint, prereqs })
    .then(r => r.data.result)
}

/** KT 掌握度 */
export function mastery(studentId: string) {
  return http.post('/api/v1/mcp/call', { tool: 'kt_mastery', args: { studentId } })
    .then(r => r.data)
}
