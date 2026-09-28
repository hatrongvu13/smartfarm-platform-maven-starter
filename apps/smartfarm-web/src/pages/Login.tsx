import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router'
import { useAuth } from '../auth'
import { ErrorText, Field } from '../ui'
export default function Login() {
    const { login } = useAuth(), navigate = useNavigate(); const [tenant, setTenant] = useState(''), [email, setEmail] = useState(''), [password, setPassword] = useState(''), [busy, setBusy] = useState(false), [error, setError] = useState<unknown>(null)
    async function submit(e: FormEvent) { e.preventDefault(); setBusy(true); setError(null); try { await login(tenant, email, password); navigate('/', { replace: true }) } catch (e) { setError(e) } finally { setBusy(false) } }
    return <main className="login-bg"><section className="login"><div className="logo">✳ <span>SmartFarm</span></div><div className="eyebrow">NỀN TẢNG QUẢN LÝ TRANG TRẠI</div><h1>Chào mừng trở lại</h1><p>Đăng nhập bằng tài khoản Identity qua Gateway.</p><form onSubmit={submit}><Field label="Tenant ID"><input required value={tenant} onChange={e => setTenant(e.target.value)} autoComplete="organization" /></Field><Field label="Email"><input type="email" required value={email} onChange={e => setEmail(e.target.value)} autoComplete="username" /></Field><Field label="Mật khẩu"><input type="password" required value={password} onChange={e => setPassword(e.target.value)} autoComplete="current-password" /></Field><ErrorText error={error} /><button disabled={busy}>{busy ? 'Đang đăng nhập...' : 'Đăng nhập'}</button></form><small>Token chỉ lưu trong bộ nhớ. Tải lại trang sẽ cần đăng nhập lại.</small></section></main>
}
