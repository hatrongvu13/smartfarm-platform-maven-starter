import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useAuth } from './auth'
import { useFarm } from './app'
type Event = { metadata?: { eventId?: string; tenantId?: string; farmId?: string }; taskChanged?: unknown; taskDeadlineBreached?: unknown; orderChanged?: unknown; type?: string }
type Feed = { connected: boolean; events: Event[] }
const C = createContext<Feed>({ connected: false, events: [] })
export function EventProvider({ children }: { children: ReactNode }) {
    const { token, me } = useAuth(), { farmId } = useFarm(), cache = useQueryClient(); const [connected, setConnected] = useState(false), [events, setEvents] = useState<Event[]>([]), seen = useRef(new Set<string>())
    useEffect(() => {
        setEvents([]); seen.current.clear(); if (!token) return
        let ws: WebSocket | null = null, closed = false, retry: number | undefined, delay = 1000
        const connect = () => {
            if (closed) return; const url = new URL('/ws/events', window.location.href); url.protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'; url.searchParams.set('token', token); if (farmId) url.searchParams.set('farmId', farmId)
            ws = new WebSocket(url); ws.onopen = () => { delay = 1000 }; ws.onmessage = msg => { try { const e = JSON.parse(msg.data) as Event; if (e.type === 'connected') { setConnected(true); return } if (e.metadata?.tenantId !== me?.tenantId) return; if (farmId && e.metadata?.farmId !== farmId) return; const id = e.metadata?.eventId; if (id) { if (seen.current.has(id)) return; seen.current.add(id); if (seen.current.size > 300) seen.current.clear() } setEvents(old => [e, ...old].slice(0, 30)); void cache.invalidateQueries({ queryKey: ['dashboard'] }); void cache.invalidateQueries({ queryKey: ['tasks'] }); void cache.invalidateQueries({ queryKey: ['orders'] }) } catch {/* ignore invalid frames */ } }
            ws.onerror = () => ws?.close(); ws.onclose = () => { setConnected(false); if (!closed) { retry = window.setTimeout(connect, delay); delay = Math.min(delay * 2, 30000) } }
        }
        connect(); return () => { closed = true; setConnected(false); if (retry) window.clearTimeout(retry); ws?.close() }
    }, [token, me?.tenantId, farmId, cache]); return <C.Provider value={{ connected, events }}>{children}</C.Provider>
}
export function useEvents() { return useContext(C) }
