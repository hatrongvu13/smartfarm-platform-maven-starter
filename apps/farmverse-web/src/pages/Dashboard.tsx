import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { gql } from '../lib/api'
import { scopesOf, useAuth } from '../lib/auth'
import { Badge, Card, ErrorMsg, Json, PageHeader } from '../components/ui'
import FarmWorldScene from '../components/FarmWorldSceneLoader'

export default function Dashboard() {
  const { me } = useAuth()
  const scopes = scopesOf(me)
  // GraphQL platformStatus cần scope farm:read (super-admin "*" qua được)
  const status = useQuery({
    queryKey: ['platformStatus'],
    queryFn: () => gql<{ platformStatus: { name: string; status: string; tenantId: string } }>('query { platformStatus { name status tenantId } }'),
  })

  const links = [
    ['/orders', 'Con đường thương hồ', 'Gửi nông sản theo những chuyến hàng', '🛖', 'realm-card--amber'],
    ['/inventory', 'Kho báu mùa màng', 'Hạt giống, vật tư và chiến lợi phẩm', '🧺', 'realm-card--green'],
    ['/livestock', 'Linh thú đồng xanh', 'Chăm sóc đàn vật nuôi của bạn', '🐾', 'realm-card--blue'],
    ['/reports', 'Biên niên sử', 'Những câu chuyện của mùa vụ', '📜', 'realm-card--violet'],
  ]

  return (
    <>
      <PageHeader title="Lãnh địa Mộc Linh" sub={`Mùa màng bình an nhé, ${me?.email ?? me?.sub ?? 'lữ khách'}!`} />
      <section className="realm-hero">
        <FarmWorldScene />
        <div className="realm-hero-shade" />
        <div className="realm-hero-content">
          <div className="realm-season"><span className="realm-season-dot" /> THUNG LŨNG MÙA XANH <span>✦</span> NGÀY 08</div>
          <h2>Nơi phép màu<br /><em>đơm hoa kết trái</em></h2>
          <p>Chăm chút từng mầm non, xây dựng nông trại trong miền đất huyền diệu.</p>
          <Link to="/inventory" className="realm-hero-cta">Khám phá nông trại <span>↗</span></Link>
        </div>
        <div className="realm-hero-caption"><span>✧</span> Hồ Ánh Trăng <i /> Rừng Thì Thầm <i /> Đồi Pha Lê</div>
        <div className="realm-level"><span className="realm-level-icon">✹</span><span><small>NGƯỜI GIEO MẦM</small><b>Cấp độ 01</b></span><span className="realm-level-xp">✦ 120 XP</span></div>
      </section>

      <section className="realm-quick-stats" aria-label="Tình hình nông trại">
        <div><span className="realm-stat-icon">🌱</span><span><small>Đất lành</small><b>{status.data?.platformStatus.status ?? (status.isLoading ? 'Đang thức giấc…' : 'Sẵn sàng')}</b></span></div>
        <div><span className="realm-stat-icon">🏡</span><span><small>Lãnh địa</small><b>{status.data?.platformStatus.tenantId ?? 'Nông trại của bạn'}</b></span></div>
        <div><span className="realm-stat-icon">✨</span><span><small>Khế ước phép thuật</small><b>{scopes.length} năng lực</b></span></div>
        <div className="realm-status-detail">{status.error && <ErrorMsg error={status.error} />}{status.data && <Badge tone="ok">Kết nối bình an</Badge>}</div>
      </section>

      <section className="realm-section">
        <div className="realm-section-heading"><div><span className="realm-eyebrow">CÁNH CỬA ĐẾN MIỀN ĐẤT</span><h2>Hành trình của bạn</h2></div><span className="realm-section-sparkle">✧</span></div>
        <div className="realm-portal-grid">
          {links.map(([to, title, description, icon, color]) => (
            <Link key={to} to={to} className={`realm-portal ${color}`}>
              <span className="realm-portal-icon">{icon}</span><span className="realm-portal-arrow">↗</span>
              <span className="realm-portal-title">{title}</span><span className="realm-portal-description">{description}</span>
              <span className="realm-portal-enter">Bước vào <span>→</span></span>
            </Link>
          ))}
        </div>
      </section>

      <div className="grid gap-4 lg:grid-cols-2">
        <Card title="Tín hiệu từ linh mạch" className="realm-data-card">
          {status.isLoading && <p className="text-sm text-stone-500">Đang lắng nghe mạch đất…</p>}
          {status.data && <p className="text-sm text-stone-600">Nông trại <strong>{status.data.platformStatus.name}</strong> đang vận hành tốt. Dòng linh khí đã thông suốt.</p>}
          {status.error && <ErrorMsg error={status.error} />}
        </Card>
        <Card title="Hồ sơ lữ khách" className="realm-data-card">
          <div className="mb-3 flex flex-wrap gap-1.5">
            {scopes.length ? scopes.slice(0, 6).map(s => <Badge key={s} tone={s === '*' ? 'warn' : 'info'}>{s}</Badge>) : <span className="text-sm text-stone-500">Chưa có khế ước năng lực</span>}
            {scopes.length > 6 && <Badge tone="neutral">+{scopes.length - 6}</Badge>}
          </div>
          <details><summary className="cursor-pointer text-sm text-stone-600">Xem chi tiết hồ sơ</summary><div className="mt-2"><Json data={me} /></div></details>
        </Card>
      </div>
    </>
  )
}
