/**
 * 每页 AI 助手(所有页面通用): SSE 流式对话, 经 realtime-gateway 代理 agent-service(LangGraph)。
 * PC 端用 EventSource; 小程序端(edu-app)经网关的 WS 通道使用同一后端。
 */
import { useEffect, useRef, useState } from 'react'
import { Button, Drawer, Input, List, Tag, Typography } from 'antd'

const { Text } = Typography

interface ChatMsg { role: 'user' | 'assistant'; content: string }

export default function AiAssistant({ pageContext }: { pageContext: string }) {
  const [open, setOpen] = useState(false)
  const [input, setInput] = useState('')
  const [msgs, setMsgs] = useState<ChatMsg[]>([
    { role: 'assistant', content: `您好！我是 ${pageContext} 页面的 AI 运维助手。可以问我：服务健康状态、Pulsar 积压原因、缓存命中率分析、告警解读等。` },
  ])
  const [streaming, setStreaming] = useState(false)
  const esRef = useRef<EventSource | null>(null)

  useEffect(() => () => esRef.current?.close(), [])

  const send = () => {
    if (!input.trim() || streaming) return
    const question = input.trim()
    setMsgs(m => [...m, { role: 'user', content: question }, { role: 'assistant', content: '' }])
    setInput('')
    setStreaming(true)

    const es = new EventSource(
      `/sse?pageContext=${encodeURIComponent(pageContext)}&message=${encodeURIComponent(question)}`)
    esRef.current = es
    es.onmessage = e => {
      if (e.data === '[DONE]') { es.close(); setStreaming(false); return }
      try {
        const { content } = JSON.parse(e.data)
        if (content) {
          setMsgs(m => {
            const last = m[m.length - 1]
            return [...m.slice(0, -1), { ...last, content: last.content + content }]
          })
        }
      } catch { /* 忽略心跳 */ }
    }
    es.onerror = () => { es.close(); setStreaming(false) }
  }

  return (
    <>
      <Button type="primary" shape="circle" size="large" style={{ position: 'fixed', right: 24, bottom: 24, zIndex: 999 }}
        onClick={() => setOpen(true)} title="AI 助手">AI</Button>
      <Drawer title={<span>AI 助手 <Tag color="blue">{pageContext}</Tag></span>}
        placement="right" width={460} open={open} onClose={() => setOpen(true && false) || setOpen(false)}>
        <List
          dataSource={msgs}
          renderItem={m => (
            <List.Item>
              <Text type={m.role === 'user' ? undefined : 'secondary'} style={{ whiteSpace: 'pre-wrap' }}>
                {m.role === 'user' ? `我：${m.content}` : `AI：${m.content}`}
              </Text>
            </List.Item>
          )}
        />
        <Input.Search
          value={input} onChange={e => setInput(e.target.value)}
          placeholder="问点什么，例如：Pulsar 积压为什么涨？"
          enterButton={streaming ? '回答中…' : '发送'}
          onSearch={send} disabled={streaming} style={{ marginTop: 12 }} />
      </Drawer>
    </>
  )
}
