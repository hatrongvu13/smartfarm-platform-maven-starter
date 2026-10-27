import { NavLink, Link, Outlet } from 'react-router-dom'
import { useAuth, hasPermission } from '../lib/auth'
import { useFarm } from '../lib/farm'
import { Button, Input } from './ui'
import { NotificationBell, NotificationProvider } from './notifications'

// Kho, đơn hàng, báo cáo quay lại khi service tương ứng có REST (cấp 2–3).
const nav = [
  { to: '/', label: 'Tổng quan', icon: '🌿', end: true },
  { to: '/livestock', label: 'Vật nuôi', icon: '🐾' },
]

function LayoutContent() {
  const { me, logout } = useAuth()
  const { farmId, setFarmId } = useFarm()
  // Ẩn mục nav mà user không có scope.
  const items = [...nav, ...(hasPermission(me, 'identity:user:read') ? [{ to: '/admin/users', label: 'Người dùng', icon: '👥' }] : []), ...(hasPermission(me, 'identity:role:read') ? [{ to: '/admin/authorization', label: 'Phân quyền', icon: '🔑' }] : [])]
  return (
    <div className="farm-shell min-h-dvh min-w-0 md:flex">
      <aside className="farm-sidebar border-b border-stone-200 bg-brand-900 text-stone-100 md:min-h-screen md:w-56 md:border-b-0">
        <Link to="/" className="farm-brand flex items-center gap-3 px-5 py-5 text-lg font-semibold">
          <span className="farm-brand-mark">✦</span>
          <span><span className="block">Mộc Linh</span><small className="block text-[10px] font-medium uppercase tracking-[0.2em] text-emerald-100/65">Farm Realm</small></span>
        </Link>
        <div className="farm-nav-caption px-5 pb-2 pt-4">Lãnh địa của bạn</div>
        <nav className="farm-nav flex gap-1 overflow-x-auto px-3 pb-3 md:flex-col md:pb-0">
          {items.map(n => (
            <NavLink key={n.to} to={n.to} end={'end' in n ? n.end : undefined}
              className={({ isActive }) => `farm-nav-link whitespace-nowrap rounded-lg px-3 py-2 text-sm ${isActive ? 'is-active bg-white/15 font-medium' : 'text-stone-300 hover:bg-white/10'}`}>
              <span className="farm-nav-icon">{n.icon}</span><span>{n.label}</span>
            </NavLink>
          ))}
        </nav>
        <div className="farm-sidebar-note">
          <span>☼</span><p>Chăm sóc đất lành,<br />ươm mầm phép màu.</p>
        </div>
      </aside>
      <div className="min-w-0 flex-1">
        <header className="farm-topbar flex min-w-0 flex-wrap items-center justify-end gap-2 px-3 py-3 sm:gap-3 sm:px-6">
          <div className="farm-land-selector flex items-center gap-2 text-sm">
            <span className="farm-land-icon">⌂</span><span className="hidden text-stone-500 sm:inline">Nông trại</span>
            <Input aria-label="Mã nông trại" value={farmId} onChange={e => setFarmId(e.target.value)} className="!w-32 !py-1" />
          </div>
          <NotificationBell />
          <Link to="/profile" className="farm-user-pill text-sm underline-offset-2 hover:underline"><span className="farm-avatar">✧</span><span className="max-w-40 truncate">{me?.email ?? me?.userId}</span></Link>
          <Button variant="ghost" className="farm-logout" onClick={logout}>Đăng xuất</Button>
        </header>
        <main className="farm-main mx-auto min-w-0 max-w-[1600px] p-3 sm:p-4 lg:p-7"><Outlet /></main>
      </div>
    </div>
  )
}

export default function Layout() {
  return <NotificationProvider><LayoutContent /></NotificationProvider>
}
