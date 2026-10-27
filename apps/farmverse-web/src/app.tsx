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

export default function App() {
  const { status, login } = useAuth()
  if (status === 'loading') return <div className="p-10 text-stone-500">Đang tải…</div>

  // Chưa có session ở gateway: đăng nhập diễn ra trên trang của identity-service (OIDC).
  if (status === 'anonymous') {
    return (
      <AuthShell title="Đăng nhập FarmVerse" sub="Bạn sẽ được chuyển sang trang đăng nhập của hệ thống.">
        <Button onClick={login}>Đăng nhập</Button>
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
