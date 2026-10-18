import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api/v1/ops': { target: 'http://localhost:8096', changeOrigin: true },
      '/sse': { target: 'http://localhost:8095', changeOrigin: true },
      '/grafana': { target: 'http://localhost:3000', changeOrigin: true, rewrite: p => p.replace(/^\/grafana/, '') },
    },
  },
})
