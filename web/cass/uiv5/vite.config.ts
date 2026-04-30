import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  base: '/cass/uiv5/dist/',
  build: {
    // Clear dist/ before each build so historical content-hashed bundles
    // don't accumulate. Without this, npm run build keeps adding new
    // i-<hash>.js alongside old ones, bloating the .app bundle and the
    // appendage-path web dir on every update.
    emptyOutDir: true,
    rollupOptions: {
      input: 'i.html',
    },
  },
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
