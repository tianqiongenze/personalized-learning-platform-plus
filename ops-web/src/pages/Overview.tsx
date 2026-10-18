import { useEffect, useState } from 'react'
import { Card, Col, Row, Statistic, Table, Tag, Alert } from 'antd'
import { getOverview, getAlerts, type Deployment, type Hpa } from '../api'
import AiAssistant from '../components/AiAssistant'

export default function Overview() {
  const [deployments, setDeployments] = useState<Deployment[]>([])
  const [hpas, setHpas] = useState<Hpa[]>([])
  const [embeds, setEmbeds] = useState<{ [k: string]: string }>({})
  const [alerts, setAlerts] = useState<any[]>([])

  useEffect(() => {
    getOverview().then(o => { setDeployments(o.deployments); setHpas(o.hpas); setEmbeds(o.grafanaEmbeds) })
    getAlerts().then(a => setAlerts(a?.data?.alerts ?? [])).catch(() => {})
    const t = setInterval(() => getOverview().then(o => setDeployments(o.deployments)), 30000)
    return () => clearInterval(t)
  }, [])

  return (
    <>
      <Row gutter={[16, 16]}>
        <Col span={6}><Card><Statistic title="服务" value={deployments.length} /></Card></Col>
        <Col span={6}><Card><Statistic title="健康" value={deployments.filter(d => d.healthy).length}
          valueStyle={{ color: '#3f8600' }} /></Card></Col>
        <Col span={6}><Card><Statistic title="异常" value={deployments.filter(d => !d.healthy).length}
          valueStyle={{ color: '#cf1322' }} /></Card></Col>
        <Col span={6}><Card><Statistic title="HPA" value={hpas.length} /></Card></Col>
      </Row>

      {alerts.length > 0 && (
        <Alert style={{ marginTop: 16 }} type="warning" showIcon
          message={`当前 ${alerts.length} 条告警触发`} description={alerts.map(a => a.labels?.alertname).join(', ')} />
      )}

      <Card title="服务运行状态" size="small" style={{ marginTop: 16 }}>
        <Table<Deployment> rowKey={r => r.namespace + r.name} dataSource={deployments} size="small"
          columns={[
            { title: '命名空间', dataIndex: 'namespace' },
            { title: '服务', dataIndex: 'name' },
            { title: '就绪/期望', render: (_, d) => `${d.ready}/${d.replicas}` },
            { title: '镜像', dataIndex: 'image', ellipsis: true },
            { title: '状态', render: (_, d) => d.healthy ? <Tag color="green">正常</Tag> : <Tag color="red">异常</Tag> },
          ]} pagination={false} />
      </Card>

      <Card title="Grafana 总览大屏(kiosk 嵌入)" size="small" style={{ marginTop: 16 }}>
        {embeds.platformOverview && (
          <iframe src={embeds.platformOverview} style={{ width: '100%', height: 600, border: 0 }}
            title="platform-overview" />
        )}
      </Card>
      <AiAssistant pageContext="总览" />
    </>
  )
}
