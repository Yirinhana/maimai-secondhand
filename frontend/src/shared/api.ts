/**
 * 统一 fetch 封装（契约见 docs/design/api-v1.md）。
 * - credentials: 'same-origin'，携带 HttpOnly 会话 Cookie
 * - 非 GET 请求自动从 Cookie 读取 XSRF-TOKEN 并附带 X-XSRF-TOKEN 头
 * - 错误统一抛出 { code, message }
 * - 401 统一跳转 /login
 */

export interface ApiError {
  code: string
  message: string
  traceId?: string
  timestamp?: string
}

const API_PREFIX = '/api/v1'

function readCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp('(?:^|; )' + name + '=([^;]*)'))
  return match ? decodeURIComponent(match[1]) : null
}

function toApiError(data: unknown, fallback: string): ApiError {
  if (
    data !== null &&
    typeof data === 'object' &&
    'code' in data &&
    'message' in data
  ) {
    const d = data as Record<string, unknown>
    return {
      code: String(d.code),
      message: String(d.message),
      traceId: d.traceId ? String(d.traceId) : undefined,
      timestamp: d.timestamp ? String(d.timestamp) : undefined,
    }
  }
  return { code: 'UNKNOWN', message: fallback }
}

function redirectToLogin() {
  if (window.location.pathname !== '/login') {
    const redirect = window.location.pathname + window.location.search
    window.location.href = '/login?redirect=' + encodeURIComponent(redirect)
  }
}

interface RequestOptions {
  query?: Record<string, string | number | boolean | undefined | null>
  anonymous?: boolean
}

function buildUrl(path: string, query?: RequestOptions['query']): string {
  const url = API_PREFIX + path
  if (!query) return url
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (value === undefined || value === null || value === '') continue
    params.set(key, String(value))
  }
  const qs = params.toString()
  return qs ? `${url}?${qs}` : url
}

async function request<T>(
  method: string,
  path: string,
  body?: unknown,
  options?: RequestOptions,
): Promise<T> {
  const headers: Record<string, string> = {}
  if (method !== 'GET' && method !== 'HEAD') {
    if (!readCookie('XSRF-TOKEN')) await touchCsrf()
    headers['Content-Type'] = 'application/json'
    const xsrf = readCookie('XSRF-TOKEN')
    if (xsrf) headers['X-XSRF-TOKEN'] = xsrf
  }

  let res: Response
  try {
    res = await fetch(buildUrl(path, options?.query), {
      method,
      credentials: 'same-origin',
      signal: AbortSignal.timeout(15000),
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw { code: 'NETWORK_ERROR', message: '网络连接失败，请稍后重试' } satisfies ApiError
  }

  if (res.status === 401) {
    if (!options?.anonymous && !path.startsWith('/auth/')) redirectToLogin()
  }

  const text = await res.text()
  let data: unknown = null
  if (text) {
    try {
      data = JSON.parse(text)
    } catch {
      throw { code: 'BAD_RESPONSE', message: '服务响应格式异常' } satisfies ApiError
    }
  }

  if (!res.ok) {
    throw toApiError(data, `请求失败（${res.status}）`)
  }
  return data as T
}

export function get<T>(path: string, query?: RequestOptions['query']): Promise<T> {
  return request<T>('GET', path, undefined, { query, anonymous: path === '/auth/me' })
}

export function post<T>(path: string, body?: unknown): Promise<T> {
  return request<T>('POST', path, body)
}

export function put<T>(path: string, body?: unknown): Promise<T> {
  return request<T>('PUT', path, body)
}

export function patch<T>(path: string, body?: unknown): Promise<T> {
  return request<T>('PATCH', path, body)
}

export function del<T>(path: string): Promise<T> {
  return request<T>('DELETE', path)
}

/**
 * multipart 上传（图片等）。使用 FormData，浏览器自动设置带 boundary 的
 * Content-Type，此处绝不手动设置该头。
 */
export async function upload<T>(path: string, formData: FormData): Promise<T> {
  if (!readCookie('XSRF-TOKEN')) await touchCsrf()
  const headers: Record<string, string> = {}
  const xsrf = readCookie('XSRF-TOKEN')
  if (xsrf) headers['X-XSRF-TOKEN'] = xsrf

  let res: Response
  try {
    res = await fetch(API_PREFIX + path, {
      method: 'POST',
      credentials: 'same-origin',
      signal: AbortSignal.timeout(30000),
      headers,
      body: formData,
    })
  } catch {
    throw { code: 'NETWORK_ERROR', message: '网络连接失败，请稍后重试' } satisfies ApiError
  }

  if (res.status === 401) {
    redirectToLogin()
    throw { code: 'UNAUTHORIZED', message: '请先登录' } satisfies ApiError
  }

  const text = await res.text()
  let data: unknown = null
  if (text) {
    try {
      data = JSON.parse(text)
    } catch {
      throw { code: 'BAD_RESPONSE', message: '服务响应格式异常' } satisfies ApiError
    }
  }

  if (!res.ok) {
    throw toApiError(data, `上传失败（${res.status}）`)
  }
  return data as T
}

/** 触碰 CSRF token，确保会话 Cookie 与 XSRF-TOKEN Cookie 已下发 */
export function touchCsrf(): Promise<{ token: string }> {
  return get<{ token: string }>('/auth/csrf')
}
