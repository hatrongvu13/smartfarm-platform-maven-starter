import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, identity, type Page } from '../../lib/api'
import { hasPermission, useAuth } from '../../lib/auth'
import { Badge, Button, Card, ErrorMsg, Field, Input, PageHeader, Select, statusTone } from '../../components/ui'

type User = { userId: string; email: string; displayName: string; status: 'ACTIVE' | 'SUSPENDED' | 'DISABLED'; roles: string[]; locked: boolean; lastLoginAt?: string }
type Role = { code: string; name: string }

// /api/identity/users (identity:user:read / identity:user:manage), tenant lấy từ token.
export default function Users() {
  const { me } = useAuth()
  const canManage = hasPermission(me, 'identity:user:manage')
  const qc = useQueryClient()
  const [page, setPage] = useState(0)
  const [draft, setDraft] = useState({ email: '', displayName: '', password: '', role: '' })

  const users = useQuery({
    queryKey: ['users', page],
    queryFn: () => api<Page<User>>(identity('/users'), { query: { page, size: 20, sort: 'createdAt,desc' } }),
  })
  const roles = useQuery({ queryKey: ['roles'], queryFn: () => api<Role[]>(identity('/roles')) })
  const refresh = () => qc.invalidateQueries({ queryKey: ['users'] })

  const add = useMutation({
    mutationFn: () => api(identity('/users'), {
      method: 'POST',
      body: { email: draft.email, displayName: draft.displayName, password: draft.password || undefined, roles: draft.role ? [draft.role] : [] },
    }),
    onSuccess: () => { setDraft({ email: '', displayName: '', password: '', role: '' }); refresh() },
  })
  const setStatus = useMutation({
    mutationFn: ({ userId, status }: { userId: string; status: User['status'] }) =>
      api(identity(`/users/${userId}/status`), { method: 'PUT', body: { status } }),
    onSuccess: refresh,
  })
  const setRoles = useMutation({
    mutationFn: ({ userId, roles }: { userId: string; roles: string[] }) =>
      api(identity(`/users/${userId}/roles`), { method: 'PUT', body: { roles } }),
    onSuccess: refresh,
  })
  const unlock = useMutation({
    mutationFn: (userId: string) => api(identity(`/users/${userId}/unlock`), { method: 'POST' }),
    onSuccess: refresh,
  })

  const rows = users.data?.content ?? []
  const totalPages = users.data?.page.totalPages ?? 1

  return (
    <>
      <PageHeader title="Người dùng" sub={`Tổ chức ${me?.tenantCode ?? ''} · ${users.data?.page.totalElements ?? 0} người`} />
      <div className="grid gap-4 xl:grid-cols-3">
        <Card title="Danh sách" className="xl:col-span-2">
          <ErrorMsg error={users.error ?? setStatus.error ?? setRoles.error ?? unlock.error} />
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="border-b text-xs uppercase text-stone-500">
                <tr><th className="py-2 pr-3">Người dùng</th><th className="py-2 pr-3">Trạng thái</th><th className="py-2 pr-3">Role</th><th className="py-2" /></tr>
              </thead>
              <tbody>
                {rows.map(u => (
                  <tr key={u.userId} className="border-b align-top last:border-0">
                    <td className="py-2 pr-3"><div className="font-medium">{u.displayName}</div><div className="text-stone-500">{u.email}</div></td>
                    <td className="py-2 pr-3"><Badge tone={statusTone(u.status)}>{u.status}</Badge>{u.locked && <Badge tone="bad">khóa tạm</Badge>}</td>
                    <td className="py-2 pr-3">
                      {canManage ? (
                        <Select aria-label={`Role của ${u.email}`} value={u.roles[0] ?? ''}
                          onChange={e => setRoles.mutate({ userId: u.userId, roles: e.target.value ? [e.target.value] : [] })}>
                          <option value="">(không role)</option>
                          {roles.data?.map(r => <option key={r.code} value={r.code}>{r.code}</option>)}
                        </Select>
                      ) : u.roles.join(', ')}
                    </td>
                    <td className="space-x-1 whitespace-nowrap py-2 text-right">
                      {canManage && u.userId !== me?.userId && (u.status === 'ACTIVE'
                        ? <Button variant="ghost" onClick={() => setStatus.mutate({ userId: u.userId, status: 'SUSPENDED' })}>Tạm khóa</Button>
                        : <Button variant="ghost" onClick={() => setStatus.mutate({ userId: u.userId, status: 'ACTIVE' })}>Mở lại</Button>)}
                      {canManage && u.locked && <Button variant="ghost" onClick={() => unlock.mutate(u.userId)}>Mở khóa</Button>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="mt-3 flex items-center gap-2 text-sm">
            <Button variant="ghost" disabled={page === 0} onClick={() => setPage(page - 1)}>‹ Trước</Button>
            <span>Trang {page + 1} / {Math.max(totalPages, 1)}</span>
            <Button variant="ghost" disabled={page + 1 >= totalPages} onClick={() => setPage(page + 1)}>Sau ›</Button>
          </div>
        </Card>

        {canManage && (
          <Card title="Thêm người dùng">
            <form className="space-y-3" onSubmit={(e: FormEvent) => { e.preventDefault(); add.mutate() }}>
              <Field label="Email"><Input type="email" required value={draft.email} onChange={e => setDraft({ ...draft, email: e.target.value })} /></Field>
              <Field label="Tên hiển thị"><Input required value={draft.displayName} onChange={e => setDraft({ ...draft, displayName: e.target.value })} /></Field>
              <Field label="Mật khẩu" hint="Chỉ cần khi email chưa có tài khoản (≥ 12 ký tự)">
                <Input type="password" minLength={12} autoComplete="new-password" value={draft.password} onChange={e => setDraft({ ...draft, password: e.target.value })} />
              </Field>
              <Field label="Role">
                <Select value={draft.role} onChange={e => setDraft({ ...draft, role: e.target.value })}>
                  <option value="">(không role)</option>
                  {roles.data?.map(r => <option key={r.code} value={r.code}>{r.code} — {r.name}</option>)}
                </Select>
              </Field>
              <ErrorMsg error={add.error} />
              <Button disabled={add.isPending}>Thêm</Button>
            </form>
          </Card>
        )}
      </div>
    </>
  )
}
