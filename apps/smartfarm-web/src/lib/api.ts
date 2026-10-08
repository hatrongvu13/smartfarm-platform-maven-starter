// API client cho gateway. Token access giữ trong memory, refresh token trong localStorage.
// Tự refresh 1 lần khi gặp 401 (single-flight), thất bại -> đăng xuất.

const REFRESH_KEY = 'sf.refreshToken'

let accessToken: string | null = null
let onSessionLost: (() => void) | null = null

export const tokens = {
  get access() { return accessToken },
  get refresh() { try { return localStorage.getItem(REFRESH_KEY) } catch { return null } },
  set(access: string | null, refresh?: string | null) {
    accessToken = access
    try {
      if (refresh === null) localStorage.removeItem(REFRESH_KEY)
      else if (refresh) localStorage.setItem(REFRESH_KEY, refresh)
    } catch { /* ignore */ }
  },
  clear() { this.set(null, null) },
}

export function setSessionLostHandler(fn: () => void) { onSessionLost = fn }

export class ApiError extends Error {
  status: number
  body: unknown
  constructor(status: number, message: string, body?: unknown) {
    super(message)
    this.status = status
    this.body = body
  }
}

export const newIdempotencyKey = () =>
  (crypto.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`)

type Opts = {
  method?: string
  body?: unknown
  query?: Record<string, string | number | undefined | null>
  idempotent?: boolean      // thêm header Idempotency-Key (bắt buộc cho ghi: orders/drafts, inventory, tasks, reports)
  auth?: boolean            // mặc định true
}

function buildUrl(path: string, query?: Opts['query']) {
  if (!query) return path
  const qs = new URLSearchParams()
  Object.entries(query).forEach(([k, v]) => { if (v !== undefined && v !== null && v !== '') qs.set(k, String(v)) })
  const s = qs.toString()
  return s ? `${path}?${s}` : path
}

let refreshing: Promise<boolean> | null = null
async function tryRefresh(): Promise<boolean> {
  const rt = tokens.refresh
  if (!rt) return false
  refreshing ??= (async () => {
    try {
      const r = await fetch('/api/v1/auth/refresh', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken: rt }),
      })
      if (!r.ok) return false
      const b = await r.json()
      tokens.set(b.accessToken, b.refreshToken)
      return true
    } catch { return false } finally { setTimeout(() => { refreshing = null }, 0) }
  })()
  return refreshing
}

async function parse(res: Response) {
  if (res.status === 204) return undefined
  const text = await res.text()
  if (!text) return undefined
  try { return JSON.parse(text) } catch { return text }
}

export async function api<T = any>(path: string, opts: Opts = {}): Promise<T> {
  const { method = 'GET', body, query, idempotent, auth = true } = opts
  const run = () => {
    const headers: Record<string, string> = { Accept: 'application/json' }
    if (body !== undefined) headers['Content-Type'] = 'application/json'
    if (auth && accessToken) headers.Authorization = `Bearer ${accessToken}`
    if (idempotent) headers['Idempotency-Key'] = newIdempotencyKey()
    return fetch(buildUrl(path, query), {
      method, headers, body: body !== undefined ? JSON.stringify(body) : undefined,
    })
  }

  let res = await run()
  if (res.status === 401 && auth && (await tryRefresh())) res = await run()
  if (res.status === 401 && auth) { tokens.clear(); onSessionLost?.() }

  const data = await parse(res)
  if (!res.ok) {
    const d = data as any
    const msg = (d && (d.message || d.detail || d.error || d.title)) || `HTTP ${res.status}`
    throw new ApiError(res.status, String(msg), data)
  }
  return data as T
}

// GraphQL: lỗi nghiệp vụ trả HTTP 200 + errors[] (xem Bruno README)
function graphqlStatus(code: string): number {
  switch (code) {
    case 'UNAUTHENTICATED': return 401
    case 'FORBIDDEN':
    case 'PERMISSION_DENIED': return 403
    case 'NOT_FOUND': return 404
    case 'CONFLICT':
    case 'ALREADY_EXISTS':
    case 'ABORTED': return 409
    case 'FAILED_PRECONDITION': return 412
    case 'RESOURCE_EXHAUSTED': return 429
    case 'UNAVAILABLE':
    case 'DOWNSTREAM_UNAVAILABLE': return 502
    case 'DEADLINE_EXCEEDED': return 504
    case 'BAD_USER_INPUT':
    case 'INVALID_ARGUMENT':
    case 'GRAPHQL_VALIDATION_FAILED': return 400
    default: return 422
  }
}

export async function gql<T = any>(query: string, variables?: Record<string, unknown>): Promise<T> {
  const r = await api<{ data?: T; errors?: { message: string }[] }>('/graphql', {
    method: 'POST', body: { query, variables },
  })
  if (r.errors?.length) {
    const errors = r.errors as Array<{ message?: string; extensions?: Record<string, unknown> }>
    const first = errors[0]
    const extensionStatus = Number(first?.extensions?.status ?? first?.extensions?.httpStatus)
    const code = typeof first?.extensions?.code === 'string'
      ? first.extensions.code
      : 'GRAPHQL_ERROR'
    const status = Number.isInteger(extensionStatus) && extensionStatus >= 400
      ? extensionStatus
      : graphqlStatus(code)
    const message = errors.map(item => item.message || code).join('; ')
    throw new ApiError(status, message, { code, errors })
  }return r.data as T
}
