import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api, ApiError } from '../lib/api'
import { useAuth } from '../lib/auth'
import { Button, ErrorMsg, Field, Input } from '../components/ui'
import AuthShell from './AuthShell'

// POST /api/v1/auth/bootstrap-superadmin — one-shot, 409 khi đã có super-admin.
export default function Bootstrap() {
  const { completeLogin } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [bootstrapKey, setBootstrapKey] = useState('')
  const [err, setErr] = useState<unknown>()
  const [busy, setBusy] = useState(false)
  const bootstrapKeyRejected = err instanceof ApiError && err.status === 403

  async function submit(e: FormEvent) {
    e.preventDefault(); setBusy(true); setErr(undefined)
    try {
      const r = await api('/api/v1/auth/bootstrap-superadmin', {
        method: 'POST',
        body: { email, password },
        auth: false,
        headers: { 'X-SmartFarm-Bootstrap-Token': bootstrapKey.trim() },
      })
      await completeLogin(r)
      setBootstrapKey('')
      // Cách 1: đưa super-admin mới sang /profile để bật TOTP ngay (idempotent nếu đã bật).
      navigate('/profile?setup=totp', { replace: true })
    } catch (x) { setErr(x) } finally { setBusy(false) }
  }

  return (
    <AuthShell title="Chào mừng đến với SmartFarm" sub="Identity vừa được khởi tạo. Tạo tài khoản quản trị đầu tiên để bắt đầu sử dụng hệ thống.">
      <form onSubmit={submit} className="space-y-4">
        <div className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-900">
          Nhập khóa khởi tạo một lần được ghi trong log của <code>smartfarm-identity-service</code>. Khóa này không phải mật khẩu tài khoản.
        </div>
        <Field label="Khóa khởi tạo"><Input type="password" required autoComplete="off" value={bootstrapKey} onChange={e => setBootstrapKey(e.target.value)} /></Field>
        <Field label="Email"><Input type="email" required value={email} onChange={e => setEmail(e.target.value)} /></Field>
        <Field label="Mật khẩu" hint="Dùng mật khẩu dài, ngẫu nhiên."><Input type="password" required minLength={12} value={password} onChange={e => setPassword(e.target.value)} /></Field>
        <ErrorMsg
          error={err}
          hint={bootstrapKeyRejected
            ? ' — Identity không chấp nhận khóa này. Kiểm tra log của đúng tiến trình smartfarm-identity-service; nếu IDENTITY_SUPERADMIN_BOOTSTRAP_TOKEN được cấu hình thì dùng giá trị đó thay khóa tự sinh. Khởi động lại Identity sau khi đổi code/cấu hình.'
            : undefined}
        />
        <Button disabled={busy} className="w-full">{busy ? 'Đang tạo…' : 'Tạo super-admin'}</Button>
        <p className="text-center text-sm"><Link className="text-brand-700 underline" to="/">Về đăng nhập</Link></p>
      </form>
    </AuthShell>
  )
}
