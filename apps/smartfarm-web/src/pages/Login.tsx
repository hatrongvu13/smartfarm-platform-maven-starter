import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { QRCodeSVG } from 'qrcode.react'
import { api } from '../lib/api'
import { useAuth, type AuthResponse } from '../lib/auth'
import { Button, ErrorMsg, Field, Input } from '../components/ui'
import AuthShell from './AuthShell'

type Step =
  | { name: 'credentials' }
  | { name: 'tenant'; tenants: NonNullable<AuthResponse['tenants']> }
  | { name: 'mfa'; challengeToken: string }
  | { name: 'enroll'; challengeToken: string }

type Deployment = { initialized: boolean; superAdminExists: boolean; bootstrapRequired: boolean }

/**
 * Luồng: login {email,password,tenantId?} -> authenticationStatus:
 *  COMPLETED | MFA_REQUIRED | MFA_ENROLLMENT_REQUIRED | TENANT_SELECTION_REQUIRED
 */
export default function Login() {
  const { completeLogin } = useAuth()
  const [step, setStep] = useState<Step>({ name: 'credentials' })
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [err, setErr] = useState<unknown>()
  const [busy, setBusy] = useState(false)
  const [dep, setDep] = useState<Deployment | null>(null)

  useEffect(() => {
    api<Deployment>('/api/v1/platform/deployment-state', { auth: false }).then(setDep).catch(() => setDep(null))
  }, [])

  async function handle(r: AuthResponse) {
    switch (r.authenticationStatus) {
      case 'COMPLETED': return completeLogin(r)
      case 'MFA_REQUIRED': return setStep({ name: 'mfa', challengeToken: r.challengeToken! })
      case 'MFA_ENROLLMENT_REQUIRED': return setStep({ name: 'enroll', challengeToken: r.challengeToken! })
      case 'TENANT_SELECTION_REQUIRED': return setStep({ name: 'tenant', tenants: r.tenants ?? [] })
    }
  }

  async function login(tenantId?: string) {
    setBusy(true); setErr(undefined)
    try {
      const r = await api<AuthResponse>('/api/v1/auth/login', { method: 'POST', auth: false, body: { email, password, ...(tenantId ? { tenantId } : {}) } })
      await handle(r)
    } catch (x) { setErr(x) } finally { setBusy(false) }
  }

  if (step.name === 'tenant') return (
    <AuthShell title="Chọn tenant" sub="Email này thuộc nhiều tenant.">
      <div className="space-y-2">
        {step.tenants.map(t => (
          <button key={t.tenantId} disabled={busy} onClick={() => login(t.tenantId)}
            className="w-full rounded-lg border border-stone-300 px-4 py-3 text-left hover:border-brand-500 hover:bg-brand-50">
            <div className="font-medium">{t.tenantName}</div>
            <div className="text-xs text-stone-500">{t.tenantCode}</div>
          </button>
        ))}
        <ErrorMsg error={err} />
        <Button variant="ghost" className="w-full" onClick={() => setStep({ name: 'credentials' })}>Quay lại</Button>
      </div>
    </AuthShell>
  )

  if (step.name === 'mfa') return <MfaVerify challengeToken={step.challengeToken} onDone={completeLogin} onBack={() => setStep({ name: 'credentials' })} />
  if (step.name === 'enroll') return <MfaEnroll challengeToken={step.challengeToken} onDone={completeLogin} onBack={() => setStep({ name: 'credentials' })} />

  return (
    <AuthShell title="Đăng nhập" sub="Đăng nhập bằng email và mật khẩu.">
      <form onSubmit={(e: FormEvent) => { e.preventDefault(); login() }} className="space-y-4">
        {dep?.bootstrapRequired && (
          <div className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-900">
            Hệ thống chưa có super-admin. <Link to="/bootstrap" className="font-medium underline">Khởi tạo ngay</Link>
          </div>
        )}
        <Field label="Email"><Input type="email" required autoFocus value={email} onChange={e => setEmail(e.target.value)} /></Field>
        <Field label="Mật khẩu"><Input type="password" required value={password} onChange={e => setPassword(e.target.value)} /></Field>
        <ErrorMsg error={err} />
        <Button disabled={busy} className="w-full">{busy ? 'Đang đăng nhập…' : 'Đăng nhập'}</Button>
      </form>
    </AuthShell>
  )
}

function MfaVerify({ challengeToken, onDone, onBack }: { challengeToken: string; onDone: (r: AuthResponse) => Promise<void>; onBack: () => void }) {
  const [code, setCode] = useState('')
  const [err, setErr] = useState<unknown>()
  const [busy, setBusy] = useState(false)
  async function submit(e: FormEvent) {
    e.preventDefault(); setBusy(true); setErr(undefined)
    try {
      const r = await api<AuthResponse>('/api/v1/auth/mfa/verify', { method: 'POST', auth: false, body: { challengeToken, method: 'TOTP', code } })
      await onDone(r)
    } catch (x) { setErr(x) } finally { setBusy(false) }
  }
  return (
    <AuthShell title="Xác thực 2 lớp" sub="Nhập mã 6 số từ ứng dụng authenticator.">
      <form onSubmit={submit} className="space-y-4">
        <Field label="Mã TOTP"><Input inputMode="numeric" pattern="\d{6}" maxLength={6} required autoFocus value={code} onChange={e => setCode(e.target.value)} className="text-center text-lg tracking-widest" /></Field>
        <ErrorMsg error={err} />
        <Button disabled={busy} className="w-full">Xác nhận</Button>
        <Button type="button" variant="ghost" className="w-full" onClick={onBack}>Quay lại</Button>
      </form>
    </AuthShell>
  )
}

function MfaEnroll({ challengeToken, onDone, onBack }: { challengeToken: string; onDone: (r: AuthResponse) => Promise<void>; onBack: () => void }) {
  const [info, setInfo] = useState<{ authenticatorId: string; otpauthUri: string } | null>(null)
  const [codes, setCodes] = useState<string[] | null>(null)
  const [pending, setPending] = useState<AuthResponse | null>(null)
  const [code, setCode] = useState('')
  const [err, setErr] = useState<unknown>()
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    api('/api/v1/auth/mfa/enrollment/begin', { method: 'POST', auth: false, body: { challengeToken, displayName: 'SmartFarm' } })
      .then(setInfo).catch(setErr)
  }, [challengeToken])

  async function confirm(e: FormEvent) {
    e.preventDefault(); setBusy(true); setErr(undefined)
    try {
      const r = await api('/api/v1/auth/mfa/enrollment/confirm', { method: 'POST', auth: false, body: { challengeToken, authenticatorId: info!.authenticatorId, code } })
      setCodes(r.recoveryCodes ?? []); setPending(r.authentication)
    } catch (x) { setErr(x) } finally { setBusy(false) }
  }

  if (codes && pending) return (
    <AuthShell title="Lưu mã khôi phục" sub="Các mã này chỉ hiển thị một lần.">
      <pre className="mb-4 rounded-lg bg-stone-100 p-3 text-sm">{codes.join('\n')}</pre>
      <Button className="w-full" onClick={() => onDone(pending)}>Tôi đã lưu — vào hệ thống</Button>
    </AuthShell>
  )

  return (
    <AuthShell title="Bật xác thực 2 lớp" sub="Quét mã QR bằng Google Authenticator / 1Password / Authy.">
      <form onSubmit={confirm} className="space-y-4">
        {info && <div className="flex justify-center rounded-lg border border-stone-200 p-4"><QRCodeSVG value={info.otpauthUri} size={168} /></div>}
        <Field label="Mã 6 số hiện tại"><Input inputMode="numeric" maxLength={6} required value={code} onChange={e => setCode(e.target.value)} className="text-center text-lg tracking-widest" /></Field>
        <ErrorMsg error={err} />
        <Button disabled={busy || !info} className="w-full">Xác nhận</Button>
        <Button type="button" variant="ghost" className="w-full" onClick={onBack}>Quay lại</Button>
      </form>
    </AuthShell>
  )
}
