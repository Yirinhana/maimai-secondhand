import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/_AMapService': { target: 'http://127.0.0.1:8081', changeOrigin: false },
      '/api': {
        target: 'http://127.0.0.1:8081',
        changeOrigin: false,
        ws: true,
      },
      '/uploads': {
        target: 'http://127.0.0.1:8081',
        changeOrigin: false,
      },
    },
  },
})
