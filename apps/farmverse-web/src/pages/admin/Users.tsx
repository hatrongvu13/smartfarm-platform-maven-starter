import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, identity, type Page } from '../../lib/api'
import { hasPermission, useAuth } from '../../lib/auth'
import { useConfirm } from '../../components/dialog'
import { Badge, Button, Card, ErrorMsg, Field, Input, PageHeader, QueryState, Select, statusTone } from '../../components/ui'
import { vi, statusLabel } from '../../i18n/vi'

type User = { userId: string; email: string; displayName: string; status: 'ACTIVE' | 'SUSPENDED' | 'DISABLED'; roles: string[]; locked: boolean; lastLoginAt?: string }
type Role = { code: string; name: string }

// /api/identity/users (identity:user:read / identity:user:manage), tenant lấy từ token.
export default function Users() {
  const { me } = useAuth()
  const canManage = hasPermission(me, 'identity:user:manage')
  const qc = useQueryClient()
  const confirm = useConfirm()
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
  const resetMfa = useMutation({
    mutationFn: (userId: string) => api(identity(`/users/${userId}/mfa/reset`), { method: 'POST' }),
  })
  const unlock = useMutation({
    mutationFn: (userId: string) => api(identity(`/users/${userId}/unlock`), { method: 'POST' }),
    onSuccess: refresh,
  })

  const rows = users.data?.content ?? []
  const totalPages = users.data?.page.totalPages ?? 1

  return (
    <>
      <PageHeader title={vi.users.title} sub={vi.users.sub(me?.tenantCode ?? '', users.data?.page.totalElements)} />
      <div className="grid gap-4 xl:grid-cols-3">
        <Card title={vi.users.list} className="xl:col-span-2">
          <ErrorMsg error={setStatus.error ?? setRoles.error ?? unlock.error ?? resetMfa.error} />
          {roles.isError && canManage && <ErrorMsg error={roles.error} onRetry={() => roles.refetch()} />}
          <QueryState query={users} loadingLabel={vi.users.loading} empty={vi.users.empty}>
            {() => (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="border-b text-xs uppercase text-stone-600">
                <tr><th scope="col" className="py-2 pr-3">{vi.users.user}</th><th scope="col" className="py-2 pr-3">{vi.users.status}</th><th scope="col" className="py-2 pr-3">{vi.users.role}</th><th scope="col" className="py-2"><span className="sr-only">{vi.common.actions}</span></th></tr>
              </thead>
              <tbody>
                {rows.map(u => (
                  <tr key={u.userId} className="border-b align-top last:border-0">
                    <td className="py-2 pr-3"><div className="font-medium">{u.displayName}</div><div className="text-stone-600">{u.email}</div></td>
                    <td className="py-2 pr-3"><Badge tone={statusTone(u.status)}>{statusLabel(u.status)}</Badge>{u.locked && <Badge tone="bad">{vi.users.locked}</Badge>}</td>
                    <td className="py-2 pr-3">
                      {canManage ? (
                        <Select aria-label={vi.users.roleOf(u.email)} value={u.roles[0] ?? ''}
                          onChange={e => setRoles.mutate({ userId: u.userId, roles: e.target.value ? [e.target.value] : [] })}>
                          <option value="">{vi.users.noRole}</option>
                          {roles.data?.map(r => <option key={r.code} value={r.code}>{r.code}</option>)}
                        </Select>
                      ) : u.roles.join(', ')}
                    </td>
                    <td className="space-x-1 whitespace-nowrap py-2 text-right">
                      {canManage && u.userId !== me?.userId && (u.status === 'ACTIVE'
                        ? <Button variant="ghost" aria-label={vi.users.suspendLabel(u.email)} onClick={async () => {
                            if (await confirm({ ...vi.users.confirmSuspend(u.email), danger: true })) setStatus.mutate({ userId: u.userId, status: 'SUSPENDED' })
                          }}>{vi.users.suspend}</Button>
                        : <Button variant="ghost" onClick={() => setStatus.mutate({ userId: u.userId, status: 'ACTIVE' })}>{vi.users.reactivate}</Button>)}
                      {canManage && u.locked && <Button variant="ghost" onClick={() => unlock.mutate(u.userId)}>{vi.users.unlock}</Button>}
                      {canManage && u.userId !== me?.userId && <Button variant="ghost" aria-label={vi.users.resetMfaLabel(u.email)} onClick={async () => {
                        if (await confirm({ ...vi.users.confirmResetMfa(u.email), danger: true })) resetMfa.mutate(u.userId)
                      }}>{vi.users.resetMfa}</Button>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
            )}
          </QueryState>
          <div className="mt-3 flex items-center gap-2 text-sm">
            <Button variant="ghost" disabled={page === 0} onClick={() => setPage(page - 1)}>{vi.users.previous}</Button>
            <span>{vi.users.page(page + 1, Math.max(totalPages, 1))}</span>
            <Button variant="ghost" disabled={page + 1 >= totalPages} onClick={() => setPage(page + 1)}>{vi.users.next}</Button>
          </div>
        </Card>

        {canManage && (
          <Card title={vi.users.add}>
            <form className="space-y-3" onSubmit={(e: FormEvent) => { e.preventDefault(); add.mutate() }}>
              <Field label={vi.common.email}><Input type="email" required value={draft.email} onChange={e => setDraft({ ...draft, email: e.target.value })} /></Field>
              <Field label={vi.users.displayName}><Input required value={draft.displayName} onChange={e => setDraft({ ...draft, displayName: e.target.value })} /></Field>
              <Field label={vi.users.password} hint={vi.users.passwordHint}>
                <Input type="password" minLength={12} autoComplete="new-password" value={draft.password} onChange={e => setDraft({ ...draft, password: e.target.value })} />
              </Field>
              <Field label={vi.users.role}>
                <Select value={draft.role} onChange={e => setDraft({ ...draft, role: e.target.value })}>
                  <option value="">{vi.users.noRole}</option>
                  {roles.data?.map(r => <option key={r.code} value={r.code}>{r.code} — {r.name}</option>)}
                </Select>
              </Field>
              <ErrorMsg error={add.error} />
              <Button disabled={add.isPending}>{vi.common.add}</Button>
            </form>
          </Card>
        )}
      </div>
    </>
  )
}
