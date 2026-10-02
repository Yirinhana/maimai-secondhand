/**
 * 私信实时连接 composable。
 * - 同源 ws(s)://当前站点/api/v1/messages/socket
 * - 收到 {"type":"ready"} 后开始工作，随后每 30 秒发送字符串 ping
 * - 收到 {"type":"messages.changed","conversationId"} 回调刷新（推送不含正文）
 * - 断线按 1/2/5/10/30 秒退避重连；每次 ready 后回调 onReady 重新拉取
 * - 会话失效（握手被拒/连接被关闭且 /auth/me 返回 401）停止重连并跳登录
 * - 组件卸载自动清理；发消息与已读永远走 HTTP，不上行其他消息
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import type { ComputedRef, Ref } from 'vue'
import type { MessageSocketEvent } from '../../shared/types'

export type MessageSocketStatus = 'connecting' | 'ready' | 'reconnecting' | 'stopped'

const BACKOFF_MS = [1000, 2000, 5000, 10000, 30000]
const PING_INTERVAL_MS = 30000

export interface UseMessageSocketOptions {
  /** 收到 messages.changed 推送（conversationId 匹配的会话需重新拉取） */
  onChanged?: (conversationId: number) => void
  /** 每次连接 ready（含重连恢复），用于重新拉取历史与未读 */
  onReady?: () => void
}

export interface UseMessageSocket {
  status: Ref<MessageSocketStatus>
  /** 连接状态文字提示；ready 时为空字符串 */
  statusText: ComputedRef<string>
}

/** 校验会话是否仍有效；网络故障视为仍登录（继续重连），仅 401 视为登出 */
async function sessionAlive(): Promise<boolean> {
  try {
    const res = await fetch('/api/v1/auth/me', { credentials: 'same-origin', signal: AbortSignal.timeout(8000) })
    return res.status !== 401
  } catch {
    return true
  }
}

function redirectToLogin() {
  if (window.location.pathname !== '/login') {
    const redirect = window.location.pathname + window.location.search
    window.location.href = '/login?redirect=' + encodeURIComponent(redirect)
  }
}

export function useMessageSocket(options: UseMessageSocketOptions = {}): UseMessageSocket {
  const status = ref<MessageSocketStatus>('connecting')
  const statusText = computed(() => {
    switch (status.value) {
      case 'ready':
        return ''
      case 'connecting':
        return '正在连接实时消息…'
      case 'reconnecting':
        return '实时连接已断开，正在自动重连…'
      case 'stopped':
        return '实时连接已断开，请刷新页面重试'
      default:
        return ''
    }
  })

  let ws: WebSocket | null = null
  let pingTimer: number | undefined
  let reconnectTimer: number | undefined
  let attempts = 0
  let disposed = false

  function socketUrl(): string {
    const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    return `${proto}//${window.location.host}/api/v1/messages/socket`
  }

  function stopPing() {
    if (pingTimer !== undefined) {
      window.clearInterval(pingTimer)
      pingTimer = undefined
    }
  }

  function stopReconnect() {
    if (reconnectTimer !== undefined) {
      window.clearTimeout(reconnectTimer)
      reconnectTimer = undefined
    }
  }

  function startPing() {
    stopPing()
    pingTimer = window.setInterval(() => {
      if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send('ping')
      }
    }, PING_INTERVAL_MS)
  }

  function scheduleReconnect() {
    if (disposed) return
    status.value = 'reconnecting'
    const delay = BACKOFF_MS[Math.min(attempts, BACKOFF_MS.length - 1)]
    attempts += 1
    stopReconnect()
    reconnectTimer = window.setTimeout(connect, delay)
  }

  async function handleClose() {
    if (disposed) return
    // 连接被关闭且登录态已失效：停止重连并回登录页，不无限重连
    if (!(await sessionAlive())) {
      status.value = 'stopped'
      redirectToLogin()
      return
    }
    scheduleReconnect()
  }

  function connect() {
    if (disposed) return
    stopPing()
    stopReconnect()
    status.value = attempts === 0 ? 'connecting' : 'reconnecting'
    try {
      ws = new WebSocket(socketUrl())
    } catch {
      scheduleReconnect()
      return
    }
    ws.onmessage = (ev) => {
      if (typeof ev.data !== 'string') return
      let msg: MessageSocketEvent
      try {
        msg = JSON.parse(ev.data) as MessageSocketEvent
      } catch {
        return
      }
      if (msg.type === 'ready') {
        attempts = 0
        status.value = 'ready'
        startPing()
        options.onReady?.()
      } else if (msg.type === 'messages.changed') {
        options.onChanged?.(msg.conversationId)
      }
      // pong 及其余下行消息无需处理
    }
    ws.onclose = () => {
      stopPing()
      void handleClose()
    }
    ws.onerror = () => {
      // 交由 onclose 统一处理重连
      ws?.close()
    }
  }

  onMounted(connect)

  onBeforeUnmount(() => {
    disposed = true
    stopPing()
    stopReconnect()
    if (ws) {
      ws.onmessage = null
      ws.onclose = null
      ws.onerror = null
      ws.close()
      ws = null
    }
  })

  return { status, statusText }
}
