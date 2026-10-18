import { useState } from 'react'
import { Button, Card, Input, Table, Typography, message } from 'antd'
import { getPulsarTopics, getPulsarTopicStats, peekMessages } from '../api'
import AiAssistant from '../components/AiAssistant'

const { Text } = Typography

/** Pulsar 消息页: 主题列表/统计(速率/积压)/订阅/peek 查看具体消息内容 */
export default function Pulsar() {
  const [tenant, setTenant] = useState('public')
  const [ns, setNs] = useState('default')
  const [topics, setTopics] = useState<string[]>([])
  const [selected, setSelected] = useState<string>()
  const [stats, setStats] = useState<any>(null)
  const [messages, setMessages] = useState<any[]>([])
  const [subscription, setSubscription] = useState('')

  const loadTopics = () => {
    getPulsarTopics(tenant, ns).then((list: string[]) => setTopics(list)).catch(() => message.error('Broker 不可达'))
  }
  const openTopic = (t: string) => {
    const short = t.split('/').pop()!
    setSelected(short)
    getPulsarTopicStats(short).then(setStats).catch(() => {})
  }
  const peek = () => {
    if (!selected) return
    peekMessages(selected, subscription || 'ai-service-sub', 10)
      .then(r => setMessages(Array.isArray(r) ? r : [])).catch(() => message.error('peek 失败'))
  }

  return (
    <>
      <Card size="small" style={{ marginBottom: 16 }}>
        tenant: <Input style={{ width: 120 }} value={tenant} onChange={e => setTenant(e.target.value)} />
        namespace: <Input style={{ width: 160 }} value={ns} onChange={e => setNs(e.target.value)} />
        <Button type="primary" onClick={loadTopics} style={{ marginLeft: 12 }}>加载主题</Button>
      </Card>
      <Table rowKey={(t: any) => t.key} dataSource={topics.map(t => ({ key: t, name: t }))} size="small"
        columns={[
          { title: '主题', dataIndex: 'name', ellipsis: true },
          { title: '操作', render: (_, r) => <Button size="small" onClick={() => openTopic(r.name)}>统计/查看消息</Button> },
        ]} pagination={false} />
      {selected && (
        <>
          <Card title={`主题统计 · ${selected}`} size="small" style={{ marginTop: 16 }}>
            <Text code>{JSON.stringify(stats, null, 1).slice(0, 1500)}</Text>
          </Card>
          <Card size="small" style={{ marginTop: 16 }}
            title={<>查看消息(peek, 不消费) subscription:
              <Input style={{ width: 220, marginLeft: 8 }} value={subscription}
                onChange={e => setSubscription(e.target.value)} placeholder="ai-service-sub" />
              <Button size="small" type="primary" style={{ marginLeft: 8 }} onClick={peek}>peek 10 条</Button>
            </>}>
            {messages.length === 0 ? <Text type="secondary">暂无消息</Text> : (
              <Table rowKey={(_, i) => String(i)} dataSource={messages} size="small"
                columns={[
                  { title: '内容(data)', render: (_, m) => <Text code>{JSON.stringify(m?.data ?? m).slice(0, 300)}</Text> },
                ]} pagination={{ pageSize: 10 }} />
            )}
          </Card>
        </>
      )}
      <AiAssistant pageContext="Pulsar 消息" />
    </>
  )
}
