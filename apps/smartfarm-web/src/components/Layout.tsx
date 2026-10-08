import { NavLink, Link, Outlet } from 'react-router-dom'
import { useAuth, hasScope } from '../lib/auth'
import { useFarm } from '../lib/farm'
import { Button, Input } from './ui'

const nav = [
  { to: '/', label: 'Tổng quan', end: true },
  { to: '/orders', label: 'Đơn hàng' },
  { to: '/inventory', label: 'Kho' },
  { to: '/livestock', label: 'Chăn nuôi' },
  { to: '/reports', label: 'Báo cáo' },
]

export default function Layout() {
  const { me, logout } = useAuth()
  const { farmId, setFarmId } = useFarm()
  // Ẩn mục nav mà user không có scope.
  const items = [...nav, ...(hasScope(me, 'identity:user:read') ? [{ to: '/admin/users', label: 'Người dùng' }] : []), ...(hasScope(me, 'identity:role:read') ? [{ to: '/admin/authorization', label: 'Vai trò & quyền' }] : [])]
  return (
    <div className="min-h-screen md:flex">
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
      <div className="flex-1">
        <header className="flex flex-wrap items-center justify-end gap-3 border-b border-stone-200 bg-white px-6 py-3">
          <div className="flex items-center gap-2 text-sm">
            <span className="text-stone-500">Farm</span>
            <Input value={farmId} onChange={e => setFarmId(e.target.value)} className="!w-32 !py-1" />
          </div>
          <Link to="/profile" className="text-sm text-stone-600 underline-offset-2 hover:text-brand-700 hover:underline">{me?.email ?? me?.sub}</Link>
          <Button variant="ghost" onClick={logout}>Đăng xuất</Button>
        </header>
        <main className="mx-auto max-w-6xl p-6"><Outlet /></main>
      </div>
    </div>
  )
}
