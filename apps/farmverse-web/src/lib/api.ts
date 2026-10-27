// API client cho gateway (BFF). Trình duyệt KHÔNG giữ token: chỉ có cookie session FV_SESSION
// (HttpOnly) do gateway quản. Request ghi gửi kèm header X-XSRF-TOKEN lấy từ cookie XSRF-TOKEN.

let onSessionLost: (() => void) | null = null
export function setSessionLostHandler(fn: () => void) { onSessionLost = fn }

export class ApiError extends Error {
  status: number
  code?: string
  body: unknown
  constructor(status: number, message: string, body?: unknown) {
    super(message)
    this.status = status
    this.body = body
    this.code = (body as { code?: string } | undefined)?.code
  }
}

export const newIdempotencyKey = () =>
  (crypto.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`)

type Opts = {
  method?: string
  body?: unknown
  query?: Record<string, string | number | undefined | null>
  idempotent?: boolean   // thêm header Idempotency-Key
  headers?: Record<string, string>
}

function buildUrl(path: string, query?: Opts['query']) {
  if (!query) return path
  const qs = new URLSearchParams()
  Object.entries(query).forEach(([k, v]) => { if (v !== undefined && v !== null && v !== '') qs.set(k, String(v)) })
  const s = qs.toString()
  return s ? `${path}?${s}` : path
}

function readCookie(name: string): string | null {
  const match = document.cookie.split('; ').find(row => row.startsWith(name + '='))
  return match ? decodeURIComponent(match.substring(name.length + 1)) : null
}

async function parse(res: Response) {
  if (res.status === 204) return undefined
  const text = await res.text()
  if (!text) return undefined
  try { return JSON.parse(text) } catch { return text }
}

export async function api<T = any>(path: string, opts: Opts = {}): Promise<T> {
  const { method = 'GET', body, query, idempotent, headers: extraHeaders } = opts
  const headers: Record<string, string> = { Accept: 'application/json', ...extraHeaders }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (idempotent) headers['Idempotency-Key'] = newIdempotencyKey()
  if (method !== 'GET' && method !== 'HEAD') {
    const xsrf = readCookie('XSRF-TOKEN')
    if (xsrf) headers['X-XSRF-TOKEN'] = xsrf
  }

  const res = await fetch(buildUrl(path, query), {
    method, headers, credentials: 'same-origin',
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })
  if (res.status === 401) onSessionLost?.()

  const data = await parse(res)
  if (!res.ok) {
    const d = data as any
    const msg = (d && (d.detail || d.message || d.title || d.error)) || `HTTP ${res.status}`
    throw new ApiError(res.status, String(msg), data)
  }
  return data as T
}

/** Đường dẫn qua gateway: /api/<service>/... */
export const identity = (path: string) => `/api/identity${path}`
export const farm = (path: string) => `/api/farm${path}`

/** Trang Spring Data trả về (PagedModel). */
export type Page<T> = { content: T[]; page: { size: number; number: number; totalElements: number; totalPages: number } }

/** Bắt đầu đăng nhập: gateway chuyển sang trang login của identity (OIDC + PKCE). */
export function startLogin() { window.location.href = '/oauth2/authorization/farmverse' }

/** Đăng xuất: POST /logout (có CSRF) → gateway xóa session rồi đăng xuất ở identity. */
export function logout() {
  const form = document.createElement('form')
  form.method = 'POST'
  form.action = '/logout'
  const input = document.createElement('input')
  input.type = 'hidden'
  input.name = '_csrf'
  input.value = readCookie('XSRF-TOKEN') ?? ''
  form.appendChild(input)
  document.body.appendChild(form)
  form.submit()
}
