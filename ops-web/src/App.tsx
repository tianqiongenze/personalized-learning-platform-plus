import { Card, Layout, Menu } from 'antd'
import { useState } from 'react'
import { BrowserRouter, Route, Routes, Link } from 'react-router-dom'
import Overview from './pages/Overview'
import Services from './pages/Services'
import Cache from './pages/Cache'
import PulsarPage from './pages/Pulsar'
import Logs from './pages/Logs'
import AiAssistant from './components/AiAssistant'

const { Header, Sider, Content } = Layout

export default function App() {
  const [collapsed, setCollapsed] = useState(false)
  return (
    <BrowserRouter>
      <Layout style={{ minHeight: '100vh' }}>
        <Header style={{ color: '#fff', fontSize: 16 }}>
          千人千面个性化学习平台 · 统一运维监测平台
        </Header>
        <Layout>
          <Sider collapsible collapsed={collapsed} onCollapse={setCollapsed}>
            <Menu theme="dark" mode="inline" defaultSelectedKeys={['overview']} items={[
              { key: 'overview', label: <Link to="/">总览</Link> },
              { key: 'services', label: <Link to="/services">服务运行</Link> },
              { key: 'cache', label: <Link to="/cache">缓存监控</Link> },
              { key: 'pulsar', label: <Link to="/pulsar">Pulsar 消息</Link> },
              { key: 'logs', label: <Link to="/logs">日志中心</Link> },
            ]} />
          </Sider>
          <Content style={{ padding: 16 }}>
            <Routes>
              <Route path="/" element={<Overview />} />
              <Route path="/services" element={<Services />} />
              <Route path="/cache" element={<Cache />} />
              <Route path="/pulsar" element={<PulsarPage />} />
              <Route path="/logs" element={<Logs />} />
            </Routes>
          </Content>
        </Layout>
      </Layout>
      <AiAssistant pageContext="平台运维" />
    </BrowserRouter>
  )
}
