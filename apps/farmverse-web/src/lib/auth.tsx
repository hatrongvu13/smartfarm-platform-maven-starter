import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, logout as bffLogout, setSessionLostHandler, startLogin } from './api'

/** Trả về từ GET /bff/me của gateway (lấy từ token, không có token). */
export type Me = {
  userId: string
  email?: string
  name?: string
  tenantId?: string
  tenantCode?: string
  roles: string[]
  permissions: string[]
}

type AuthState = {
  status: 'loading' | 'anonymous' | 'authenticated'
  me: Me | null
  login: () => void
  logout: () => void
  reload: () => Promise<void>
}

const Ctx = createContext<AuthState>(null as never)
export const useAuth = () => useContext(Ctx)

/** Super admin qua mọi kiểm tra quyền phía giao diện (service vẫn kiểm lại). */
export const hasPermission = (me: Me | null, permission: string) =>
  !!me && (me.roles.includes('SUPER_ADMIN') || me.permissions.includes(permission))

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthState['status']>('loading')
  const [me, setMe] = useState<Me | null>(null)

  const reload = useCallback(async () => {
    try {
      setMe(await api<Me>('/bff/me'))
      setStatus('authenticated')
    } catch {
      setMe(null)
      setStatus('anonymous')
    }
  }, [])

  useEffect(() => {
    setSessionLostHandler(() => { setMe(null); setStatus('anonymous') })
    reload()
  }, [reload])

  const value = useMemo(() => ({ status, me, login: startLogin, logout: bffLogout, reload }), [status, me, reload])
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>
}
