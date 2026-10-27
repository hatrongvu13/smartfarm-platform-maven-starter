import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { gql } from '../../lib/api'
import { useAuth, hasScope } from '../../lib/auth'
import { Badge, Button, Card, ErrorMsg, Field, Input, PageHeader, Select, statusTone } from '../../components/ui'

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
const ROLES = `query($page: PageInput) {
  roles(page: $page) { nodes { roleId code name type } nextPageToken }
}`

const USER_PRINCIPAL = `query($subjectId: ID!) {
  userPrincipal(subjectId: $subjectId) {
    subjectId version
    profile { displayName email firstName lastName phoneNumber avatarUrl locale timeZone emailVerified phoneVerified }
  }
}`
const UPDATE_USER_PROFILE = `mutation($input: AdminUpdateUserProfileInput!) {
  updateUserProfile(input: $input) { subjectId version profile { displayName email firstName lastName phoneNumber avatarUrl locale timeZone } }
}`
const DISABLE_MEMBERSHIP = `mutation($subjectId: ID!, $reason: String) { disableUserMembership(subjectId: $subjectId, reason: $reason) { subjectId accountStatus membershipStatus } }`
const ENABLE_MEMBERSHIP = `mutation($subjectId: ID!) { enableUserMembership(subjectId: $subjectId) { subjectId accountStatus membershipStatus } }`
const SUSPEND_MEMBERSHIP = `mutation($subjectId: ID!, $reason: String) { suspendUserMembership(subjectId: $subjectId, reason: $reason) { subjectId accountStatus membershipStatus } }`
const DISABLE_ACCOUNT = `mutation($subjectId: ID!, $reason: String) { disableUserAccount(subjectId: $subjectId, reason: $reason) { subjectId accountStatus membershipStatus } }`
const ENABLE_ACCOUNT = `mutation($subjectId: ID!) { enableUserAccount(subjectId: $subjectId) { subjectId accountStatus membershipStatus } }`
const UNLOCK_ACCOUNT = `mutation($subjectId: ID!) { unlockUserAccount(subjectId: $subjectId) { subjectId accountStatus membershipStatus } }`

const CREATE_USER = `mutation($input: CreateUserInput!) {
  createUser(input: $input) {
    subjectId membershipId tenantId membershipStatus existingAccount
  }
}`

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
  const canCreateUser = hasScope(me, 'identity:user:create')
  const canManageMembership = hasScope(me, 'identity:user:disable')
  const canManageAccount = hasScope(me, 'identity:user:account:manage')
  const canManageProfile = hasScope(me, 'identity:user:profile:manage')
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
      {canCreateUser && <CreateUserCard />}
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

        <UserDetail subjectId={selected} currentSubjectId={me?.sub ?? me?.subjectId} canResetMfa={canResetMfa} canManageMembership={canManageMembership} canManageAccount={canManageAccount} canManageProfile={canManageProfile} />
      </div>
    </>
  )
}


type CreateUserResult = {
  subjectId: string
  membershipId: string
  tenantId: string
  membershipStatus: string
  existingAccount: boolean
}

function CreateUserCard() {
  const qc = useQueryClient()
  const [email, setEmail] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [initialPassword, setInitialPassword] = useState('')
  const [phoneNumber, setPhoneNumber] = useState('')
  const [roleCode, setRoleCode] = useState('')
  const [activateImmediately, setActivateImmediately] = useState(true)

  const roles = useQuery({
    queryKey: ['adminRolesForCreateUser'],
    queryFn: () => gql<{ roles: { nodes: { roleId: string; code: string; name: string; type: string }[] } }>(
      ROLES,
      { page: { size: 100 } },
    ).then(r => r.roles.nodes.filter(role => role.type !== 'PLATFORM' && !['SUPERADMIN', 'PLATFORM_ADMIN'].includes(role.code))),
  })

  const create = useMutation({
    mutationFn: () => gql<{ createUser: CreateUserResult }>(CREATE_USER, {
      input: {
        email: email.trim(),
        initialPassword,
        displayName: displayName.trim(),
        ...(phoneNumber.trim() ? { phoneNumber: phoneNumber.trim() } : {}),
        locale: 'vi-VN',
        timeZone: 'Asia/Ho_Chi_Minh',
        initialRoleCodes: [roleCode],
        activateImmediately,
      },
    }).then(r => r.createUser),
    onSuccess: async () => {
      setEmail('')
      setDisplayName('')
      setInitialPassword('')
      setPhoneNumber('')
      setRoleCode('')
      setActivateImmediately(true)
      await qc.invalidateQueries({ queryKey: ['adminUsers'] })
    },
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    if (initialPassword.length < 12 || initialPassword.length > 128) return
    create.mutate()
  }

  return (
    <Card title="Tạo tài khoản" className="mb-4">
      <form className="grid gap-3 md:grid-cols-2" onSubmit={submit}>
        <Field label="Email"><Input type="email" required value={email} onChange={e => setEmail(e.target.value)} /></Field>
        <Field label="Tên hiển thị"><Input required value={displayName} onChange={e => setDisplayName(e.target.value)} /></Field>
        <Field label="Mật khẩu ban đầu" hint="Từ 12 đến 128 ký tự.">
          <Input type="password" required minLength={12} maxLength={128} autoComplete="new-password" value={initialPassword} onChange={e => setInitialPassword(e.target.value)} />
        </Field>
        <Field label="Số điện thoại"><Input value={phoneNumber} onChange={e => setPhoneNumber(e.target.value)} /></Field>
        <Field label="Vai trò ban đầu">
          <Select required value={roleCode} onChange={e => setRoleCode(e.target.value)}>
            <option value="">Chọn vai trò từ hệ thống</option>
            {roles.data?.map(role => <option key={role.roleId} value={role.code}>{role.name} ({role.code})</option>)}
          </Select>
        </Field>
        <label className="flex items-center gap-2 self-end pb-2 text-sm text-stone-700">
          <input type="checkbox" checked={activateImmediately} onChange={e => setActivateImmediately(e.target.checked)} />
          Kích hoạt ngay
        </label>
        <div className="md:col-span-2">
          <ErrorMsg error={roles.error || create.error} />
          {create.isSuccess && (
            <p className="mb-2 rounded-lg bg-green-50 px-3 py-2 text-sm text-green-700">
              Đã tạo {create.data.existingAccount ? 'membership cho tài khoản hiện có' : 'tài khoản mới'} ở trạng thái {create.data.membershipStatus}.
            </p>
          )}
          <Button disabled={create.isPending || roles.isLoading || !roleCode || initialPassword.length < 12 || initialPassword.length > 128}>
            {create.isPending ? 'Đang tạo…' : 'Tạo tài khoản'}
          </Button>
        </div>
      </form>
    </Card>
  )
}

function UserDetail({
  subjectId,
  currentSubjectId,
  canResetMfa,
  canManageMembership,
  canManageAccount,
  canManageProfile,
}: {
  subjectId: string | null
  currentSubjectId?: string
  canResetMfa: boolean
  canManageMembership: boolean
  canManageAccount: boolean
  canManageProfile: boolean
}) {
  const qc = useQueryClient()
  const [editing, setEditing] = useState(false)
  const [profile, setProfile] = useState({ displayName: '', firstName: '', lastName: '', phoneNumber: '', avatarUrl: '', locale: 'vi-VN', timeZone: 'Asia/Ho_Chi_Minh' })

  const q = useQuery({
    queryKey: ['userAuthz', subjectId],
    enabled: !!subjectId,
    queryFn: () => gql<{ userAuthorization: UserAuthorization }>(AUTHZ, { subjectId }).then(r => r.userAuthorization),
  })
  const principal = useQuery({
    queryKey: ['adminUserPrincipal', subjectId],
    enabled: !!subjectId && canManageProfile,
    queryFn: () => gql<{ userPrincipal: any }>(USER_PRINCIPAL, { subjectId }).then(r => r.userPrincipal),
  })

  const refresh = async () => {
    await Promise.all([
      qc.invalidateQueries({ queryKey: ['adminUsers'] }),
      qc.invalidateQueries({ queryKey: ['userAuthz', subjectId] }),
      qc.invalidateQueries({ queryKey: ['adminUserPrincipal', subjectId] }),
    ])
  }
  const operation = useMutation({
    mutationFn: ({ query, variables }: { query: string; variables: Record<string, unknown> }) => gql(query, variables),
    onSuccess: refresh,
  })
  const resetMfa = useMutation({
    mutationFn: () => gql<{ resetUserMfa: boolean }>(RESET_MFA, { subjectId }),
    onSuccess: refresh,
  })
  const updateProfile = useMutation({
    mutationFn: () => gql(UPDATE_USER_PROFILE, {
      input: {
        subjectId,
        expectedVersion: principal.data?.version,
        displayName: profile.displayName.trim(),
        firstName: profile.firstName.trim(),
        lastName: profile.lastName.trim(),
        phoneNumber: profile.phoneNumber.trim(),
        avatarUrl: profile.avatarUrl.trim(),
        locale: profile.locale.trim(),
        timeZone: profile.timeZone.trim(),
      },
    }),
    onSuccess: async () => { setEditing(false); await refresh() },
  })

  function beginEdit() {
    const p = principal.data?.profile
    if (!p) return
    setProfile({
      displayName: p.displayName ?? '', firstName: p.firstName ?? '', lastName: p.lastName ?? '',
      phoneNumber: p.phoneNumber ?? '', avatarUrl: p.avatarUrl ?? '', locale: p.locale ?? 'vi-VN',
      timeZone: p.timeZone ?? 'Asia/Ho_Chi_Minh',
    })
    setEditing(true)
  }
  function reason(label: string) {
    const value = window.prompt(`Nhập lý do ${label}:`)
    return value === null ? null : value.trim()
  }
  function run(query: string, variables: Record<string, unknown>, message: string) {
    if (window.confirm(message)) operation.mutate({ query, variables })
  }

  if (!subjectId) return <Card title="Chi tiết"><p className="text-sm text-stone-500">Chọn một người dùng để xem chi tiết.</p></Card>
  const isSelf = subjectId === currentSubjectId

  return (
    <Card title="Chi tiết người dùng">
      <ErrorMsg error={q.error || principal.error || operation.error || resetMfa.error || updateProfile.error} />
      {(q.isLoading || principal.isLoading) && <p className="text-sm text-stone-500">Đang tải…</p>}
      {q.data && (
        <div className="space-y-4 text-sm">
          <div className="flex flex-wrap gap-2">
            <Badge tone={statusTone(q.data.accountStatus)}>Tài khoản: {q.data.accountStatus ?? '—'}</Badge>
            <Badge tone={statusTone(q.data.membershipStatus)}>Membership: {q.data.membershipStatus ?? '—'}</Badge>
          </div>

          {canManageProfile && principal.data && (
            <div className="rounded-lg border border-stone-200 p-3">
              <div className="mb-2 flex items-center justify-between">
                <div className="text-xs font-semibold uppercase text-stone-500">Hồ sơ</div>
                {!editing && <Button variant="ghost" onClick={beginEdit}>Chỉnh sửa</Button>}
              </div>
              {!editing ? (
                <div className="grid gap-1 text-xs md:grid-cols-2">
                  <div>Tên: {principal.data.profile.displayName || '—'}</div><div>Email: {principal.data.profile.email || '—'}</div>
                  <div>Điện thoại: {principal.data.profile.phoneNumber || '—'}</div><div>Locale: {principal.data.profile.locale || '—'}</div>
                  <div>Múi giờ: {principal.data.profile.timeZone || '—'}</div><div>Version: {principal.data.version}</div>
                </div>
              ) : (
                <form className="grid gap-2 md:grid-cols-2" onSubmit={e => { e.preventDefault(); updateProfile.mutate() }}>
                  <Field label="Tên hiển thị"><Input required value={profile.displayName} onChange={e => setProfile(v => ({ ...v, displayName: e.target.value }))} /></Field>
                  <Field label="Tên"><Input value={profile.firstName} onChange={e => setProfile(v => ({ ...v, firstName: e.target.value }))} /></Field>
                  <Field label="Họ"><Input value={profile.lastName} onChange={e => setProfile(v => ({ ...v, lastName: e.target.value }))} /></Field>
                  <Field label="Điện thoại"><Input value={profile.phoneNumber} onChange={e => setProfile(v => ({ ...v, phoneNumber: e.target.value }))} /></Field>
                  <Field label="Locale"><Input required value={profile.locale} onChange={e => setProfile(v => ({ ...v, locale: e.target.value }))} /></Field>
                  <Field label="Múi giờ"><Input required value={profile.timeZone} onChange={e => setProfile(v => ({ ...v, timeZone: e.target.value }))} /></Field>
                  <div className="flex gap-2 md:col-span-2"><Button disabled={updateProfile.isPending}>Lưu hồ sơ</Button><Button type="button" variant="ghost" onClick={() => setEditing(false)}>Hủy</Button></div>
                </form>
              )}
            </div>
          )}

          <div><div className="mb-1 text-xs uppercase text-stone-500">Vai trò</div><div className="flex flex-wrap gap-1">{q.data.roles.length ? q.data.roles.map(r => <Badge key={r.roleId} tone="info">{r.code}</Badge>) : <span className="text-stone-500">—</span>}</div></div>
          <div><div className="mb-1 text-xs uppercase text-stone-500">Quyền hiệu lực ({q.data.effectivePermissions.length})</div><div className="max-h-44 overflow-auto rounded-lg border border-stone-200 p-2">{q.data.effectivePermissions.map(p => <div key={p.code} className="border-b py-1 font-mono text-xs last:border-0">{p.code}</div>)}</div></div>

          {canManageMembership && !isSelf && (
            <div className="border-t pt-3"><div className="mb-2 text-xs font-semibold uppercase text-stone-500">Membership tenant</div><div className="flex flex-wrap gap-2">
              {q.data.membershipStatus !== 'ACTIVE' && <Button onClick={() => run(ENABLE_MEMBERSHIP, { subjectId }, 'Kích hoạt membership này?')}>Kích hoạt</Button>}
              {q.data.membershipStatus === 'ACTIVE' && <Button variant="ghost" onClick={() => { const r=reason('tạm ngưng membership'); if(r!==null) operation.mutate({query:SUSPEND_MEMBERSHIP,variables:{subjectId,reason:r}}) }}>Tạm ngưng</Button>}
              {q.data.membershipStatus !== 'DISABLED' && <Button variant="danger" onClick={() => { const r=reason('vô hiệu hóa membership'); if(r!==null) operation.mutate({query:DISABLE_MEMBERSHIP,variables:{subjectId,reason:r}}) }}>Inactive</Button>}
            </div></div>
          )}

          {canManageAccount && (
            <div className="border-t pt-3"><div className="mb-2 text-xs font-semibold uppercase text-stone-500">Tài khoản toàn cục, chỉ SUPERADMIN</div><div className="flex flex-wrap gap-2">
              {q.data.accountStatus === 'DISABLED' ? <Button onClick={() => run(ENABLE_ACCOUNT, { subjectId }, 'Mở lại account trên toàn hệ thống?')}>Enable account</Button> : <Button variant="danger" disabled={isSelf} onClick={() => { const r=reason('khóa account'); if(r!==null) operation.mutate({query:DISABLE_ACCOUNT,variables:{subjectId,reason:r}}) }}>Disable account</Button>}
              {q.data.accountStatus === 'LOCKED' && <Button variant="ghost" onClick={() => run(UNLOCK_ACCOUNT, { subjectId }, 'Mở khóa đăng nhập cho account này?')}>Unlock account</Button>}
              {isSelf && <span className="self-center text-xs text-amber-700">Không thể tự disable account đang đăng nhập.</span>}
            </div></div>
          )}

          {canResetMfa && !isSelf && <div className="border-t pt-3"><Button variant="danger" disabled={resetMfa.isPending} onClick={() => { if (confirm('Reset MFA cho người dùng này?')) resetMfa.mutate() }}>Reset MFA</Button></div>}
        </div>
      )}
    </Card>
  )
}

