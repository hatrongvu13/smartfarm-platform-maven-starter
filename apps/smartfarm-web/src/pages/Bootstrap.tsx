import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api } from '../lib/api'
import { useAuth } from '../lib/auth'
import { Button, ErrorMsg, Field, Input } from '../components/ui'
import AuthShell from './AuthShell'

// POST /api/v1/auth/bootstrap-superadmin — one-shot, 409 khi đã có super-admin.
export default function Bootstrap() {
  const { completeLogin } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [err, setErr] = useState<unknown>()
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault(); setBusy(true); setErr(undefined)
    try {
      const r = await api('/api/v1/auth/bootstrap-superadmin', { method: 'POST', body: { email, password }, auth: false })
      await completeLogin(r)
      // Cách 1: đưa super-admin mới sang /profile để bật TOTP ngay (idempotent nếu đã bật).
      navigate('/profile?setup=totp', { replace: true })
    } catch (x) { setErr(x) } finally { setBusy(false) }
  }

  return (
    <AuthShell title="Khởi tạo hệ thống" sub="Tạo tài khoản super-admin đầu tiên (chỉ làm được một lần).">
      <form onSubmit={submit} className="space-y-4">
        <Field label="Email"><Input type="email" required value={email} onChange={e => setEmail(e.target.value)} /></Field>
        <Field label="Mật khẩu" hint="Dùng mật khẩu dài, ngẫu nhiên."><Input type="password" required minLength={12} value={password} onChange={e => setPassword(e.target.value)} /></Field>
        <ErrorMsg error={err} />
        <Button disabled={busy} className="w-full">{busy ? 'Đang tạo…' : 'Tạo super-admin'}</Button>
        <p className="text-center text-sm"><Link className="text-brand-700 underline" to="/">Về đăng nhập</Link></p>
      </form>
    </AuthShell>
  )
}
