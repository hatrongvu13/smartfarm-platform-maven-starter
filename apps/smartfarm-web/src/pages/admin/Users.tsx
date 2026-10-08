import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { gql } from '../../lib/api'
import { useAuth, hasScope } from '../../lib/auth'
import { Badge, Button, Card, ErrorMsg, Field, Input, PageHeader, statusTone } from '../../components/ui'

const USERS = `query($filter: UserFilter, $page: PageInput) {
  users(filter: $filter, page: $page) {
    nodes { subjectId membershipId tenantId email displayName accountStatus membershipStatus roles createdAt updatedAt }
    nextPageToken
  }
}`
const AUTHZ = `query($subjectId: ID!) {
  userAuthorization(subjectId: $subjectId) {
    subjectId membershipId tenantId accountStatus membershipStatus farmIds
    roles { roleId code name type }
    effectivePermissions { code resourceType action description }
  }
}`
const RESET_MFA = `mutation($subjectId: ID!) { resetUserMfa(subjectId: $subjectId) }`

type UserSummary = {
  subjectId: string; membershipId: string; tenantId: string; email?: string; displayName?: string
  accountStatus?: string; membershipStatus?: string; roles: string[]; createdAt?: string; updatedAt?: string
}
type UserAuthorization = {
  subjectId: string; membershipId: string; tenantId: string; accountStatus?: string; membershipStatus?: string
  farmIds: string[]
  roles: { roleId: string; code: string; name: string; type: string }[]
  effectivePermissions: { code: string; resourceType: string; action: string; description?: string }[]
}

export default function Users() {
  const { me } = useAuth()
  const canResetMfa = hasScope(me, 'identity:user:mfa:reset')
  const [searchText, setSearchText] = useState('')
  const [roleCode, setRoleCode] = useState('')
  const [applied, setApplied] = useState<{ searchText: string; roleCode: string }>({ searchText: '', roleCode: '' })
  const [tokenStack, setTokenStack] = useState<string[]>([]) // lịch sử pageToken để quay lại
  const [selected, setSelected] = useState<string | null>(null)

  const pageToken = tokenStack[tokenStack.length - 1]
  const filter = {
    ...(applied.searchText ? { searchText: applied.searchText } : {}),
    ...(applied.roleCode ? { roleCode: applied.roleCode } : {}),
  }
  const q = useQuery({
    queryKey: ['adminUsers', applied, pageToken ?? ''],
    queryFn: () => gql<{ users: { nodes: UserSummary[]; nextPageToken?: string } }>(USERS, {
      filter: Object.keys(filter).length ? filter : undefined,
      page: { size: 20, ...(pageToken ? { token: pageToken } : {}) },
    }).then(r => r.users),
  })

  function applyFilter(e: FormEvent) {
    e.preventDefault()
    setApplied({ searchText, roleCode }); setTokenStack([]); setSelected(null)
  }

  return (
    <>
      <PageHeader title="Người dùng" sub="Quản trị tài khoản, vai trò và MFA." />
      <Card className="mb-4">
        <form className="flex flex-wrap items-end gap-3" onSubmit={applyFilter}>
          <div className="min-w-48 flex-1"><Field label="Tìm kiếm" hint="Email hoặc tên"><Input value={searchText} onChange={e => setSearchText(e.target.value)} /></Field></div>
          <div className="w-48"><Field label="Mã vai trò"><Input value={roleCode} onChange={e => setRoleCode(e.target.value)} placeholder="vd: ADMIN" /></Field></div>
          <Button>Lọc</Button>
        </form>
      </Card>

      <div className="grid gap-4 lg:grid-cols-2">
        <Card title="Danh sách">
          <ErrorMsg error={q.error} />
          {q.isLoading && <p className="text-sm text-stone-500">Đang tải…</p>}
          {!q.isLoading && !q.data?.nodes.length && !q.error && <p className="text-sm text-stone-500">Không có người dùng.</p>}
          {!!q.data?.nodes.length && (
            <table className="w-full text-left text-sm">
              <thead className="border-b text-xs uppercase text-stone-500"><tr><th className="py-2">Người dùng</th><th>Vai trò</th><th>Trạng thái</th></tr></thead>
              <tbody>
                {q.data.nodes.map(u => (
                  <tr key={u.subjectId} onClick={() => setSelected(u.subjectId)}
                    className={`cursor-pointer border-b last:border-0 hover:bg-stone-50 ${selected === u.subjectId ? 'bg-brand-50' : ''}`}>
                    <td className="py-2">
                      <div className="font-medium">{u.displayName || u.email || u.subjectId}</div>
                      <div className="text-xs text-stone-500">{u.email}</div>
                    </td>
                    <td className="text-xs">{u.roles.join(', ') || '—'}</td>
                    <td><Badge tone={statusTone(u.accountStatus)}>{u.accountStatus ?? '—'}</Badge></td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
          <div className="mt-4 flex gap-2">
            <Button variant="ghost" disabled={!tokenStack.length} onClick={() => setTokenStack(s => s.slice(0, -1))}>Trang trước</Button>
            <Button variant="ghost" disabled={!q.data?.nextPageToken} onClick={() => setTokenStack(s => [...s, q.data!.nextPageToken!])}>Trang sau</Button>
          </div>
        </Card>

        <UserDetail subjectId={selected} canResetMfa={canResetMfa} />
      </div>
    </>
  )
}

function UserDetail({ subjectId, canResetMfa }: { subjectId: string | null; canResetMfa: boolean }) {
  const qc = useQueryClient()
  const q = useQuery({
    queryKey: ['userAuthz', subjectId],
    enabled: !!subjectId,
    queryFn: () => gql<{ userAuthorization: UserAuthorization }>(AUTHZ, { subjectId }).then(r => r.userAuthorization),
  })
  const resetMfa = useMutation({
    mutationFn: () => gql<{ resetUserMfa: boolean }>(RESET_MFA, { subjectId }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['userAuthz', subjectId] }),
  })

  if (!subjectId) return <Card title="Chi tiết"><p className="text-sm text-stone-500">Chọn một người dùng để xem chi tiết.</p></Card>

  return (
    <Card title="Chi tiết phân quyền">
      <ErrorMsg error={q.error} />
      {q.isLoading && <p className="text-sm text-stone-500">Đang tải…</p>}
      {q.data && (
        <div className="space-y-4 text-sm">
          <div className="flex flex-wrap gap-2">
            <Badge tone={statusTone(q.data.accountStatus)}>Tài khoản: {q.data.accountStatus ?? '—'}</Badge>
            <Badge tone={statusTone(q.data.membershipStatus)}>Membership: {q.data.membershipStatus ?? '—'}</Badge>
          </div>
          <div>
            <div className="mb-1 text-xs uppercase text-stone-500">Vai trò</div>
            <div className="flex flex-wrap gap-1">
              {q.data.roles.length ? q.data.roles.map(r => <Badge key={r.roleId} tone="info">{r.code}</Badge>) : <span className="text-stone-500">—</span>}
            </div>
          </div>
          <div>
            <div className="mb-1 text-xs uppercase text-stone-500">Farm</div>
            <div className="text-stone-700">{q.data.farmIds.length ? q.data.farmIds.join(', ') : '—'}</div>
          </div>
          <div>
            <div className="mb-1 text-xs uppercase text-stone-500">Quyền hiệu lực ({q.data.effectivePermissions.length})</div>
            <div className="max-h-56 overflow-auto rounded-lg border border-stone-200 p-2">
              {q.data.effectivePermissions.length
                ? q.data.effectivePermissions.map(p => (
                  <div key={p.code} className="border-b py-1 last:border-0">
                    <span className="font-mono text-xs">{p.code}</span>
                    {p.description && <span className="ml-2 text-xs text-stone-500">{p.description}</span>}
                  </div>
                ))
                : <span className="text-stone-500">—</span>}
            </div>
          </div>
          {canResetMfa && (
            <div className="border-t pt-3">
              <ErrorMsg error={resetMfa.error} />
              {resetMfa.isSuccess && <p className="mb-2 text-sm text-green-700">Đã reset MFA cho người dùng.</p>}
              <Button variant="danger" disabled={resetMfa.isPending}
                onClick={() => { if (confirm('Reset MFA cho người dùng này? Họ sẽ phải đăng ký lại TOTP.')) resetMfa.mutate() }}>
                {resetMfa.isPending ? 'Đang reset…' : 'Reset MFA'}
              </Button>
            </div>
          )}
        </div>
      )}
    </Card>
  )
}
