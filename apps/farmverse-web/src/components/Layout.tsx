import { NavLink, Link, Outlet } from 'react-router-dom'
import { useAuth, hasPermission } from '../lib/auth'
import { useFarm } from '../lib/farm'
import { Button, Input } from './ui'
import { NotificationBell, NotificationProvider } from './notifications'
import { ConfirmProvider } from './dialog'

// Miền nghiệp vụ mới chỉ thêm khi nền tảng đạt 10/10 (content/thiet-ke-farmverse.md).
const nav: { to: string; label: string; end?: boolean; permission?: string }[] = [
  { to: '/', label: 'Tổng quan', end: true },
  { to: '/farms', label: 'Trang trại' },
  { to: '/admin/users', label: 'Người dùng', permission: 'identity:user:read' },
  { to: '/admin/authorization', label: 'Phân quyền', permission: 'identity:role:read' },
]

function LayoutContent() {
  const { me, logout } = useAuth()
  const { farmId, setFarmId } = useFarm()
  // Chỉ hiện mục mà người dùng có quyền (W7); quyền thật vẫn do service kiểm.
  const items = nav.filter(n => !n.permission || hasPermission(me, n.permission))
  return (
    <div className="min-h-dvh min-w-0 md:flex">
      {/* WCAG 2.4.1: người dùng bàn phím nhảy qua menu, vào thẳng nội dung */}
      <a href="#main" className="sr-only z-50 rounded-lg bg-white px-4 py-2 font-medium text-brand-900 focus:not-sr-only focus:fixed focus:left-3 focus:top-3">Bỏ qua đến nội dung chính</a>
      <aside className="farm-sidebar bg-brand-900 text-white">
        <Link to="/" className="flex items-center gap-3 px-5 py-5 text-lg font-bold">
          <span className="farm-brand-mark" aria-hidden="true">✿</span>
          <span>FarmVerse</span>
        </Link>
        <nav aria-label="Điều hướng chính" className="flex gap-1 overflow-x-auto px-3 pb-3 md:flex-col md:pb-0">
          {items.map(n => (
            <NavLink key={n.to} to={n.to} end={n.end}
              className={({ isActive }) => `whitespace-nowrap rounded-lg px-3 py-2 text-sm focus-visible:outline-2 focus-visible:outline-white ${isActive ? 'bg-white/15 font-semibold' : 'text-white/80 hover:bg-white/10 hover:text-white'}`}>
              {n.label}
            </NavLink>
          ))}
        </nav>
      </aside>
      <div className="min-w-0 flex-1 farm-main">
        <header className="farm-topbar flex min-w-0 flex-wrap items-center justify-end gap-2 border-b border-stone-200 bg-white/90 px-3 py-3 backdrop-blur sm:gap-3 sm:px-6">
          <label className="mr-auto flex items-center gap-2 text-sm">
            <span className="hidden text-stone-600 sm:inline">Trang trại</span>
            <Input aria-label="Mã trang trại" value={farmId} onChange={e => setFarmId(e.target.value)} className="!w-32 !py-1" />
          </label>
          <NotificationBell />
          <Link to="/profile" className="max-w-48 truncate rounded text-sm text-stone-700 underline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-brand-700">{me?.email ?? me?.userId}</Link>
          <Button variant="ghost" onClick={logout}>Đăng xuất</Button>
        </header>
        <main id="main" tabIndex={-1} className="mx-auto min-w-0 max-w-[1600px] p-3 outline-none sm:p-4 lg:p-7"><Outlet /></main>
      </div>
    </div>
  )
}

export default function Layout() {
  return <NotificationProvider><ConfirmProvider><LayoutContent /></ConfirmProvider></NotificationProvider>
}
