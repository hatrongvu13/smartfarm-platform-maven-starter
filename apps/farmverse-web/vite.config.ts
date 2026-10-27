import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// Dev: mở http://127.0.0.1:8080 (api-gateway). Gateway chuyển /api, /bff, /oauth2, /login, /logout
// cho mình xử lý và chuyển mọi đường còn lại (kể cả websocket HMR) tới Vite ở :5173.
// Không cần proxy ở đây: trình duyệt chỉ thấy một origin là gateway.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    host: '127.0.0.1',
    port: 5173,
    strictPort: true,
    hmr: { clientPort: 8080 },
  },
})
