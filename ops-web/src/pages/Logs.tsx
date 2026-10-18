import { useEffect, useState } from 'react'
import { Card, Col, Row, Table, Tag, Typography, Select, Button, Drawer, List } from 'antd'
import { getEvents, getLokiLogs, setLogLevel } from '../api'
import AiAssistant from '../components/AiAssistant'

const { Text } = Typography

/** 日志中心: Loki 聚合检索(LogQL) + Pod 实时日志(SSE) + 动态日志级别 */
export default function Logs() {
  const [logql, setLogql] = useState('{namespace="platform-plus"} | json | line_format "{{.log}}"')
  const [entries, setEntries] = useState<{ ts: string; line: string }[]>([])
  const [pod, setPod] = useState<{ ns: string; pod: string } | null>(null)
  const [liveLog, setLiveLog] = useState<string[]>([])
  const [events, setEvents] = useState<any[]>([])

  const query = () => {
    getLokiLogs(logql, 200).then(r => {
      const streams = r?.data?.result ?? []
      const rows = streams.flatMap(s => s.values.map((v: string[]) => ({ ts: v[0], line: v[1] })))
      setEntries(rows)
    }).catch(() => {})
  }
  useEffect(() => {
    getEvents().then(setEvents).catch(() => {})
  }, [])

  const openLive = (ns: string, pod: string) => {
    setPod({ ns, pod })
    setLiveLog([])
    const es = new EventSource(`/api/v1/ops/logs/pod/stream?ns=${ns}&pod=${pod}`)
    es.onmessage = e => setLiveLog(l => [...l.slice(-500), e.data])
    es.onerror = () => es.close()
  }

  return (
    <>
      <Card size="small" style={{ marginBottom: 16 }}>
        <Input.TextArea rows={2} value={logql} onChange={e => setLogql(e.target.value)} />
        <Button type="primary" onClick={query} style={{ marginTop: 8 }}>Loki 查询</Button>
      </Card>
      <Row gutter={16}>
        <Col span={12}>
          <Card title="聚合日志(Loki)" size="small">
            {entries.length === 0 ? <Text type="secondary">无结果</Text> : (
              <List size="small" dataSource={entries.slice(0, 100)}
                renderItem={e => <List.Item><Text code style={{ fontSize: 12 }}>{e.line.slice(0, 220)}</Text></List.Item>} />
            )}
          </Card>
        </Col>
        <Col span={12}>
          <Card title="K8s Warning 事件" size="small" style={{ marginBottom: 16 }}>
            <Table dataSource={events} rowKey={(_, i) => String(i)} size="small"
              columns={[
                { title: '对象', dataIndex: 'object', ellipsis: true },
                { title: '原因', dataIndex: 'reason' },
                { title: '消息', dataIndex: 'message', ellipsis: true },
              ]} pagination={{ pageSize: 8 }} />
          </Card>
          <Card size="small" title="动态日志级别(目标服务 actuator)">
            <Select style={{ width: 160 }} placeholder="服务" options={[
              { value: 'http://tenant-service:8081/api/v1', label: 'tenant' },
              { value: 'http://iam-service:8082/api/v1', label: 'iam' },
              { value: 'http://course-service:8083/api/v1', label: 'course' },
              { value: 'http://teaching-service:8084/api/v1', label: 'teaching' },
            ]} onChange={v => setLogLevel(v, 'com.platform', 'DEBUG').then(() => window.alert('已调整'))} />
          </Card>
        </Col>
      </Row>
      {pod && (
        <Drawer title={`实时日志 · ${pod.pod}`} width={720} open onClose={() => setPod(null)}>
          <div style={{ fontFamily: 'monospace', fontSize: 12, maxHeight: '80vh', overflow: 'auto' }}>
            {liveLog.map((l, i) => <div key={i}>{l}</div>)}
          </div>
        </Drawer>
      )}
      <AiAssistant pageContext="日志中心" />
    </>
  )
}
