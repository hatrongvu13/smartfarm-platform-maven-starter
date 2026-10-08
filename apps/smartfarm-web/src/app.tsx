import { Navigate, Route, Routes } from 'react-router-dom'
import Layout from './components/Layout'
import { useAuth } from './lib/auth'
import Login from './pages/Login'
import Bootstrap from './pages/Bootstrap'
import Dashboard from './pages/Dashboard'
import Orders from './pages/Orders'
import OrderNew from './pages/OrderNew'
import OrderDetail from './pages/OrderDetail'
import Inventory from './pages/Inventory'
import Livestock from './pages/Livestock'
import Reports from './pages/Reports'
import Profile from './pages/Profile'
import Users from './pages/admin/Users'
import RequireScope from './components/RequireScope'

export default function App() {
  const { status } = useAuth()
  if (status === 'loading') return <div className="p-10 text-stone-500">Đang tải…</div>

  if (status === 'anonymous') {
    return (
      <Routes>
        <Route path="/bootstrap" element={<Bootstrap />} />
        <Route path="*" element={<Login />} />
      </Routes>
    )
  }

  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<Dashboard />} />
        <Route path="orders" element={<Orders />} />
        <Route path="orders/new" element={<OrderNew />} />
        <Route path="orders/:id" element={<OrderDetail />} />
        <Route path="inventory" element={<Inventory />} />
        <Route path="livestock" element={<Livestock />} />
        <Route path="reports" element={<Reports />} />
        <Route path="profile" element={<Profile />} />
        <Route path="admin/users" element={<RequireScope scope="identity:user:read"><Users /></RequireScope>} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}
