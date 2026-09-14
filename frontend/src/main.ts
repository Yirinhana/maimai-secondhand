import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import { router } from './router'
import { touchCsrf } from './shared/api'
import './shared/styles/tokens.css'
import './shared/styles/base.css'
import './shared/styles/sections.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.mount('#app')

// 触碰 CSRF token，确保会话与 XSRF-TOKEN Cookie 下发（不阻塞首屏）
touchCsrf().catch(() => {
  /* 后端未启动时静默忽略 */
})
