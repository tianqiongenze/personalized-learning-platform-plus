import { useEffect, useState } from 'react'
import { Card, Col, Row, Table, Tag, Typography } from 'antd'
import { getHpa, queryPromql, type Hpa } from '../api'
import AiAssistant from '../components/AiAssistant'

const { Text } = Typography

/** 服务运行页: HPA 弹性状态 + 熔断/限流 + JVM + 主机资源(PromQL 实时查询) */
export default function Services() {
  const [hpas, setHpas] = useState<Hpa[]>([])
  const [cbStates, setCbStates] = useState<any>(null)
  const [load, setLoad] = useState<any>(null)

  const refresh = () => {
    getHpa().then(setHpas).catch(() => {})
    queryPromql('resilience4j_circuitbreaker_state').then(r => setCbStates(r?.data)).catch(() => {})
    queryPromql('node_load5').then(r => setLoad(r?.data)).catch(() => {})
  }
  useEffect(() => {
    refresh()
    const t = setInterval(refresh, 20000)
    return () => clearInterval(t)
  }, [])

  return (
    <>
      <Card title="HPA 弹性伸缩状态" size="small">
        <Table<Hpa> rowKey={r => r.namespace + r.name} dataSource={hpas} size="small"
          columns={[
            { title: '服务', dataIndex: 'name' },
            { title: '当前/期望', render: (_, h) => `${h.currentReplicas}/${h.desiredReplicas}` },
            { title: '区间', render: (_, h) => `${h.minReplicas}~${h.maxReplicas}` },
            { title: '指标', render: (_, h) => h.metrics.map(m => `${m.name ?? m.type}: ${m.current}`).join(', ') },
            { title: '扩缩容', render: (_, h) => h.scalingActive === 'True' ? <Tag color="green">活动</Tag> : <Tag>{h.scalingActive}</Tag> },
          ]} pagination={false} />
      </Card>
      <Row gutter={16} style={{ marginTop: 16 }}>
        <Col span={12}>
          <Card title="熔断器状态(Resilience4j)" size="small">
            <Text code>{JSON.stringify(cbStates ?? '查询中…').slice(0, 700)}</Text>
          </Card>
        </Col>
        <Col span={12}>
          <Card title="主机负载 node_load5" size="small">
            <Text code>{JSON.stringify(load ?? '查询中…').slice(0, 700)}</Text>
          </Card>
        </Col>
      </Row>
      <Card size="small" style={{ marginTop: 16 }}>
        <Text type="secondary">限流识别: PromQL <Text code>sum(rate(nginx_ingress_controller_requests&#123;status="429"&#125;[5m])) by (service)</Text>；
        P95: <Text code>histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le, application))</Text></Text>
      </Card>
      <AiAssistant pageContext="服务运行" />
    </>
  )
}
