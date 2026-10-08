import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// Gateway là ingress duy nhất (REST /api/v1, GraphQL /graphql, WS /ws).
// Proxy qua Vite dev server để tránh CORS khi phát triển.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const target = env.VITE_GATEWAY_URL || 'http://localhost:8080'
  return {
    plugins: [react(), tailwindcss()],
    server: {
      port: 5173,
      proxy: {
        '/api': { target, changeOrigin: true },
        '/graphql': { target, changeOrigin: true },
        '/actuator': { target, changeOrigin: true },
        '/ws': { target, changeOrigin: true, ws: true },
      },
    },
  }
})
