import type { ReactNode } from 'react'
import FarmWorldScene from '../components/FarmWorldSceneLoader'
export default function AuthShell({ title, sub, children }: { title: string; sub?: string; children: ReactNode }) {
  return (
    <div className="farm-auth-shell">
      <FarmWorldScene variant="compact" className="farm-auth-world" />
      <div className="farm-auth-vignette" />
      <div className="farm-auth-card">
        <div className="farm-auth-brand"><span>✦</span><div>Mộc Linh<small>FARM REALM</small></div></div>
        <div className="mb-6">
          <h1 className="text-xl font-semibold">{title}</h1>
          {sub && <p className="mt-1 text-sm text-stone-500">{sub}</p>}
        </div>
        {children}
      </div>
    </div>
  )
}
