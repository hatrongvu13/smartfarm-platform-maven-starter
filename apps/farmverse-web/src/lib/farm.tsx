import { createContext, useContext, useState, type ReactNode } from 'react'

const KEY = 'sf.farmId'
const DEFAULT = import.meta.env.VITE_DEFAULT_FARM_ID || 'farm-1'

const Ctx = createContext<{ farmId: string; setFarmId: (v: string) => void }>(null as never)
export const useFarm = () => useContext(Ctx)

export function FarmProvider({ children }: { children: ReactNode }) {
  const [farmId, set] = useState(() => { try { return localStorage.getItem(KEY) || DEFAULT } catch { return DEFAULT } })
  const setFarmId = (v: string) => { set(v); try { localStorage.setItem(KEY, v) } catch { /* ignore */ } }
  return <Ctx.Provider value={{ farmId, setFarmId }}>{children}</Ctx.Provider>
}
