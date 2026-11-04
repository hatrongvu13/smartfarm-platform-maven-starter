import { Navigate, Route, Routes } from 'react-router-dom'
import Layout from './components/Layout'
import RequirePermission from './components/RequirePermission'
import { useAuth } from './lib/auth'
import AuthShell from './pages/AuthShell'
import Dashboard from './pages/Dashboard'
import Farms from './pages/Farms'
import Profile from './pages/Profile'
import Users from './pages/admin/Users'
import Authorization from './pages/admin/Authorization'
import { Button } from './components/ui'
import { vi } from './i18n/vi'

export default function App() {
  const { status, login } = useAuth()
  if (status === 'loading') return <div role="status" className="p-10 text-stone-600">{vi.app.loading}</div>

  // Chưa có session ở gateway: đăng nhập diễn ra trên trang của identity-service (OIDC).
  if (status === 'anonymous') {
    return (
      <AuthShell title={vi.auth.loginTitle} sub={vi.auth.loginSub}>
        <Button onClick={login}>{vi.auth.login}</Button>
      </AuthShell>
    )
  }

  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<Dashboard />} />
        <Route path="farms" element={<RequirePermission permission="farm:farm:read"><Farms /></RequirePermission>} />
        <Route path="profile" element={<Profile />} />
        <Route path="admin/users" element={<RequirePermission permission="identity:user:read"><Users /></RequirePermission>} />
        <Route path="admin/authorization" element={<RequirePermission permission="identity:role:read"><Authorization /></RequirePermission>} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}
