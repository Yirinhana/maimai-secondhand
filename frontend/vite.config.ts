import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { readFileSync } from 'node:fs'

const version = readFileSync(new URL('../VERSION', import.meta.url), 'utf8').trim()
const build = `${version}-${Date.now()}`

export default defineConfig({
  plugins: [vue(), { name: 'release-manifest', generateBundle() { this.emitFile({ type: 'asset', fileName: 'release.json', source: JSON.stringify({ version, build }) }) } }],
  define: { __MAIMAI_BUILD__: JSON.stringify(build) },
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
