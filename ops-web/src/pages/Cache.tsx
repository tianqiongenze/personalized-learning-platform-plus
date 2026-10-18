import { useEffect, useState } from 'react'
import { Card, Col, Row, Table, Tag, Typography } from 'antd'
import { getRedisCluster, getMilvusCollections, getNebulaMetrics, type CacheNode } from '../api'
import AiAssistant from '../components/AiAssistant'

const { Text } = Typography

/** 缓存监控页: L2 Redis Cluster 各节点实时状态 + Milvus 集合统计 + NebulaGraph 指标 */
export default function Cache() {
  const [nodes, setNodes] = useState<CacheNode[]>([])
  const [milvus, setMilvus] = useState<any>(null)
  const [nebula, setNebula] = useState<{ metric: string; value: string }[]>([])

  const refresh = () => {
    getRedisCluster().then(setNodes).catch(() => {})
    getMilvusCollections().then(setMilvus).catch(() => {})
    getNebulaMetrics().then(setNebula).catch(() => {})
  }
  useEffect(() => {
    refresh()
    const t = setInterval(refresh, 30000)
    return () => clearInterval(t)
  }, [])

  return (
    <>
      <Row gutter={[16, 16]}>
        <Col span={12}>
          <Card title="L2 · Redis Cluster(既有集群)" size="small">
            <Table<CacheNode> rowKey={r => r.node} dataSource={nodes} size="small"
              columns={[
                { title: '节点', dataIndex: 'node', ellipsis: true },
                { title: '内存', dataIndex: 'usedMemoryHuman' },
                { title: '命中率', render: (_, n) => n.hitRatio != null ? `${(n.hitRatio * 100).toFixed(1)}%` : '-' },
                { title: 'OPS', dataIndex: 'instantaneousOpsPerSec' },
                { title: '状态', render: (_, n) => n.reachable ? <Tag color="green">在线</Tag> : <Tag color="red">失联</Tag> },
              ]} pagination={false} />
          </Card>
        </Col>
        <Col span={12}>
          <Card title="向量库 · Milvus 集合" size="small">
            <Text code>{JSON.stringify(milvus ?? { status: '加载中…' }).slice(0, 600)}</Text>
          </Card>
          <Card title="图库 · NebulaGraph (graphd:19669 /stats)" size="small" style={{ marginTop: 16 }}>
            {nebula.slice(0, 10).map(m => (
              <div key={m.metric}><Text code>{m.metric} = {m.value}</Text></div>
            ))}
          </Card>
        </Col>
      </Row>
      <Card size="small" style={{ marginTop: 16 }}>
        <Text type="secondary">L1 Caffeine 命中率: 查询 PromQL <Text code>caffeine_requests_total</Text>；
        L1+L2 总命中率曲线见 Grafana「cache-monitor」看板。</Text>
      </Card>
      <AiAssistant pageContext="缓存监控" />
    </>
  )
}
