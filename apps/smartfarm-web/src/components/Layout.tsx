import { NavLink, Link, Outlet } from 'react-router-dom'
import { useAuth, hasScope } from '../lib/auth'
import { useFarm } from '../lib/farm'
import { Button, Input } from './ui'
import { NotificationBell, NotificationProvider } from './notifications'

const nav = [
  { to: '/', label: 'Tổng quan', end: true },
  { to: '/orders', label: 'Đơn hàng' },
  { to: '/inventory', label: 'Kho' },
  { to: '/livestock', label: 'Chăn nuôi' },
  { to: '/reports', label: 'Báo cáo' },
]

function LayoutContent() {
  const { me, logout } = useAuth()
  const { farmId, setFarmId } = useFarm()
  // Ẩn mục nav mà user không có scope.
  const items = [...nav, ...(hasScope(me, 'identity:user:read') ? [{ to: '/admin/users', label: 'Người dùng' }] : []), ...(hasScope(me, 'identity:role:read') ? [{ to: '/admin/authorization', label: 'Vai trò & quyền' }] : [])]
  return (
    <div className="min-h-dvh min-w-0 md:flex">
      <aside className="border-b border-stone-200 bg-brand-900 text-stone-100 md:min-h-screen md:w-56 md:border-b-0">
        <div className="flex items-center gap-2 px-5 py-4 text-lg font-semibold">🌾 SmartFarm</div>
        <nav className="flex gap-1 overflow-x-auto px-3 pb-3 md:flex-col md:pb-0">
          {items.map(n => (
            <NavLink key={n.to} to={n.to} end={'end' in n ? n.end : undefined}
              className={({ isActive }) => `whitespace-nowrap rounded-lg px-3 py-2 text-sm ${isActive ? 'bg-white/15 font-medium' : 'text-stone-300 hover:bg-white/10'}`}>
              {n.label}
            </NavLink>
          ))}
        </nav>
      </aside>
      <div className="min-w-0 flex-1">
        <header className="flex min-w-0 flex-wrap items-center justify-end gap-2 border-b border-stone-200 bg-white px-3 py-3 sm:gap-3 sm:px-6">
          <div className="flex items-center gap-2 text-sm">
            <span className="text-stone-500">Farm</span>
            <Input value={farmId} onChange={e => setFarmId(e.target.value)} className="!w-32 !py-1" />
          </div>
          <NotificationBell />
          <Link to="/profile" className="text-sm text-stone-600 underline-offset-2 hover:text-brand-700 hover:underline">{me?.email ?? me?.sub}</Link>
          <Button variant="ghost" onClick={logout}>Đăng xuất</Button>
        </header>
        <main className="mx-auto min-w-0 max-w-7xl p-3 sm:p-4 lg:p-6"><Outlet /></main>
      </div>
    </div>
  )
}

export default function Layout() {
  return <NotificationProvider><LayoutContent /></NotificationProvider>
}
