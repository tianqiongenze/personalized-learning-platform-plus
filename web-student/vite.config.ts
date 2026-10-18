import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 学生 Web 端: 经 nginx-gateway 反代 graphql-gateway / agent-service
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5180,
    proxy: {
      '/gateway': { target: process.env.VITE_GATEWAY_URL || 'http://localhost:8091', changeOrigin: true },
      '/agent': { target: process.env.VITE_AGENT_URL || 'http://localhost:8090', changeOrigin: true, rewrite: p => p.replace(/^\/agent/, '') },
    },
  },
  build: { outDir: 'dist', sourcemap: false },
})
