import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react'
import { api, setTokenReader, type Me, type Tokens } from './api'
import { useQueryClient } from '@tanstack/react-query'
type Session = { token: string | null; me: Me | null; login: (tenantId: string, email: string, password: string) => Promise<void>; logout: () => Promise<void> }
const C = createContext<Session | null>(null)
export function AuthProvider({ children }: { children: ReactNode }) {
    const access = useRef<string | null>(null), refresh = useRef<string | null>(null)
    const [token, setToken] = useState<string | null>(null), [me, setMe] = useState<Me | null>(null)
    const cache = useQueryClient()
    useEffect(() => { setTokenReader(() => access.current); return () => setTokenReader(() => null) }, [])
    const clear = useCallback(() => { access.current = null; refresh.current = null; setToken(null); setMe(null); cache.clear() }, [cache])
    const login = useCallback(async (tenantId: string, email: string, password: string) => {
        const t = await api<Tokens>('/api/v1/auth/login', { method: 'POST', body: JSON.stringify({ tenantId, email, password }) }, false)
        if (!t.accessToken) throw new Error('Không nhận được accessToken')
        access.current = t.accessToken; refresh.current = t.refreshToken || null
        try { const user = await api<Me>('/api/v1/auth/me'); setMe(user); setToken(t.accessToken) } catch (error) { clear(); throw error }
    }, [clear])
    const logout = useCallback(async () => { const r = refresh.current; try { if (r) await api('/api/v1/auth/logout', { method: 'POST', body: JSON.stringify({ refreshToken: r }) }) } finally { clear() } }, [clear])
    // Access token remains in memory; refresh rotation is kept in memory too. Never put either in storage.
    useEffect(() => {
        if (!token || !refresh.current) return
        const timer = window.setInterval(async () => {
            const old = refresh.current; if (!old) return
            try {
                const t = await api<Tokens>('/api/v1/auth/refresh', { method: 'POST', body: JSON.stringify({ refreshToken: old }) }, false)
                if (!t.accessToken || !t.refreshToken) throw new Error('Refresh token không hợp lệ')
                access.current = t.accessToken; refresh.current = t.refreshToken; setToken(t.accessToken)
            } catch { clear() }
        }, 8 * 60 * 1000)
        return () => window.clearInterval(timer)
    }, [token, clear])
    return <C.Provider value={{ token, me, login, logout }}>{children}</C.Provider>
}
export function useAuth() { const ctx = useContext(C); if (!ctx) throw new Error('Missing AuthProvider'); return ctx }
export function can(me: Me | null, scope: string) { return Boolean(me?.permissions?.includes('*') || me?.permissions?.includes(scope)) }
