import { useState, type ReactNode, type FormEvent } from 'react'
export function Page({ title, subtitle, children, action }: { title: string; subtitle?: string; children: ReactNode; action?: ReactNode }) { return <section className="page"><div className="page-head"><div><div className="eyebrow">SMARTFARM / VẬN HÀNH</div><h1>{title}</h1>{subtitle && <p>{subtitle}</p>}</div>{action}</div>{children}</section> }
export function Card({ title, children }: { title: string; children: ReactNode }) { return <section className="card"><h2>{title}</h2>{children}</section> }
export function ErrorText({ error }: { error: unknown }) { if (!error) return null; return <p className="error" role="alert">{error instanceof Error ? error.message : String(error)}</p> }
export function Empty({ text = 'Chưa có dữ liệu' }: { text?: string }) { return <div className="empty">{text}</div> }
export function JsonView({ value }: { value: unknown }) { return <pre className="json">{JSON.stringify(value, null, 2)}</pre> }
export function Field({ label, children }: { label: string; children: ReactNode }) { return <label className="field"><span>{label}</span>{children}</label> }
export function useFormError() { const [error, setError] = useState<unknown>(null); return { error, setError } }
export function FarmPicker({ farmId, setFarmId, onSubmit }: { farmId: string; setFarmId: (v: string) => void; onSubmit: (e: FormEvent) => void }) { return <form className="filters" onSubmit={onSubmit}><Field label="Mã trang trại"><input required value={farmId} onChange={e => setFarmId(e.target.value)} /></Field><button>Tải dữ liệu</button></form> }
export function Status({ value }: { value?: string }) { return <span className={'status ' + (value?.includes('FAILED') || value?.includes('OVERDUE') ? 'danger' : value?.includes('COMPLETED') ? 'success' : '')}>{value || '—'}</span> }
