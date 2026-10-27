import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, gql, setSessionLostHandler, tokens } from './api'

export type Me = Record<string, any> & { sub?: string; email?: string; scope?: string; scopes?: string[] | string; roles?: string[] }

/** Giải mã payload (segment giữa) của JWT — base64url, không dùng thư viện ngoài. */
export function decodeJwt(token: string | null): Record<string, any> | null {
  if (!token) return null
  const parts = token.split('.')
  if (parts.length < 2) return null
  try {
    const b64 = parts[1].replace(/-/g, '+').replace(/_/g, '/')
    const pad = b64.length % 4 ? '='.repeat(4 - (b64.length % 4)) : ''
    const json = atob(b64 + pad)
    // atob trả chuỗi latin1; decode UTF-8 để giữ ký tự đa byte
    const utf8 = decodeURIComponent(json.split('').map(c => '%' + c.charCodeAt(0).toString(16).padStart(2, '0')).join(''))
    return JSON.parse(utf8)
  } catch { return null }
}

/** Đọc scopes từ claim JWT: `scope` (chuỗi phân tách bởi khoảng trắng) hoặc `scopes` (mảng/chuỗi). */
function scopesFromJwt(claims: Record<string, any> | null): string[] {
  if (!claims) return []
  const s = claims.scopes ?? claims.scope
  if (Array.isArray(s)) return s.filter(Boolean)
  if (typeof s === 'string') return s.split(/\s+/).filter(Boolean)
  return []
}

const ME_QUERY = `query { me {
  subjectId active version
  profile { displayName email firstName lastName phoneNumber avatarUrl locale timeZone emailVerified phoneVerified }
  membership { membershipId tenantId status roles farmIds }
  createdAt updatedAt
} }`

export type AuthResponse = {
  authenticationStatus: 'COMPLETED' | 'MFA_REQUIRED' | 'MFA_ENROLLMENT_REQUIRED' | 'TENANT_SELECTION_REQUIRED'
  accessToken?: string
  refreshToken?: string
  challengeToken?: string
  tenants?: { tenantId: string; tenantCode: string; tenantName: string }[]
}

type AuthState = {
  status: 'loading' | 'anonymous' | 'authenticated'
  me: Me | null
  completeLogin: (r: { accessToken?: string; refreshToken?: string }) => Promise<void>
  logout: () => Promise<void>
}

const Ctx = createContext<AuthState>(null as never)
export const useAuth = () => useContext(Ctx)

export function scopesOf(me: Me | null): string[] {
  if (!me) return []
  const s = me.scopes ?? me.scope
  if (Array.isArray(s)) return s
  if (typeof s === 'string') return s.split(/\s+/).filter(Boolean)
  return []
}
/** Super-admin mang scope "*" -> qua mọi kiểm tra. */
export const hasScope = (me: Me | null, scope: string) => {
  const s = scopesOf(me)
  return s.includes('*') || s.includes(scope)
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthState['status']>('loading')
  const [me, setMe] = useState<Me | null>(null)

  const loadMe = useCallback(async () => {
    // 1) scopes LẤY TỪ JWT (Principal không có trường scopes) — đây là nguồn driving hasScope.
    const claims = decodeJwt(tokens.access)
    const jwtScopes = scopesFromJwt(claims)
    try {
      // 2) Lấy Principal qua GraphQL `me` cho hồ sơ/membership.
      const { me: p } = await gql<{ me: any }>(ME_QUERY)
      const merged: Me = {
        ...p,
        sub: p?.subjectId,
        email: p?.profile?.email,
        scopes: jwtScopes,
        roles: p?.membership?.roles ?? [],
      }
      setMe(merged); setStatus('authenticated')
    } catch {
      // 4) gql `me` lỗi (tạm thời) KHÔNG được đăng xuất — vẫn authenticated bằng claim JWT.
      setMe({
        sub: claims?.sub,
        email: claims?.email,
        scopes: jwtScopes,
        roles: Array.isArray(claims?.roles) ? claims!.roles : [],
      })
      setStatus('authenticated')
    }
  }, [])

  const reset = useCallback(() => { tokens.clear(); setMe(null); setStatus('anonymous') }, [])

  useEffect(() => {
    setSessionLostHandler(reset)
    // Khôi phục phiên từ refresh token (access token chỉ nằm trong memory)
    ;(async () => {
      const rt = tokens.refresh
      if (!rt) return setStatus('anonymous')
      try {
        const b = await api<{ accessToken: string; refreshToken: string }>('/api/v1/auth/refresh', {
          method: 'POST', body: { refreshToken: rt }, auth: false,
        })
        tokens.set(b.accessToken, b.refreshToken)
        await loadMe()
      } catch { reset() }
    })()
  }, [loadMe, reset])

  const completeLogin: AuthState['completeLogin'] = async r => {
    tokens.set(r.accessToken ?? null, r.refreshToken)
    await loadMe()
  }

  const logout = async () => {
    const rt = tokens.refresh
    try { if (rt) await api('/api/v1/auth/logout', { method: 'POST', body: { refreshToken: rt }, auth: false }) } catch { /* ignore */ }
    reset()
  }

  const value = useMemo(() => ({ status, me, completeLogin, logout }), [status, me]) // eslint-disable-line
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>
}
