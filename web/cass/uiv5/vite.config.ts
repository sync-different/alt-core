import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  base: '/cass/uiv5/dist/',
  server: {
    proxy: {
      '/cass': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        secure: false,
        ws: true,
        rewrite: (path) => path,
        cookieDomainRewrite: 'localhost',
      },
      '/formpost': {
        target: 'http://localhost:8087',
        changeOrigin: true,
        secure: false,
        rewrite: (path) => path,
      }
    }
  }
})
