import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { gql } from '../lib/api'
import { Badge, Button, Card, ErrorMsg, Field, Input, PageHeader } from '../components/ui'
import TotpEnroll from '../components/TotpEnroll'

// ---- GraphQL ----
const ME = `query { me {
  subjectId version
  profile { displayName email firstName lastName phoneNumber avatarUrl locale timeZone }
} }`
const UPDATE = `mutation($input: UpdateProfileInput!) { updateMyProfile(input: $input) { subjectId version profile { displayName email firstName lastName phoneNumber avatarUrl locale timeZone } } }`
const CHANGE_PW = `mutation($current: String!, $next: String!) { changeMyPassword(currentPassword: $current, newPassword: $next) }`
const SECURITY = `query { mySecurityProfile {
  subjectId totpRequired totpEnabled
  authenticators { authenticatorId type status displayName }
} }`
const DISABLE = `mutation($id: ID!) { disableMyMfa(authenticatorId: $id) }`
const REGEN = `mutation($id: ID!) { regenerateRecoveryCodes(authenticatorId: $id) { recoveryCodes } }`

type Principal = {
  subjectId: string
  version: number
  profile: {
    displayName?: string; email?: string; firstName?: string; lastName?: string
    phoneNumber?: string; avatarUrl?: string; locale?: string; timeZone?: string
  }
}
type Authenticator = { authenticatorId: string; type: string; status: string; displayName?: string }
type SecurityProfile = { subjectId: string; totpRequired: boolean; totpEnabled: boolean; authenticators: Authenticator[] }

const EDITABLE = ['displayName', 'firstName', 'lastName', 'phoneNumber', 'avatarUrl', 'locale', 'timeZone'] as const
type EditableKey = typeof EDITABLE[number]

export default function Profile() {
  const [params, setParams] = useSearchParams()
  const setupTotp = params.get('setup') === 'totp'

  return (
    <>
      <PageHeader title="Hồ sơ" sub="Thông tin cá nhân và bảo mật tài khoản." />
      <div className="grid gap-4 lg:grid-cols-2">
        <ProfileCard />
        <PasswordCard />
      </div>
      <div className="mt-4">
        <SecurityCard setupTotp={setupTotp} clearSetup={() => { const n = new URLSearchParams(params); n.delete('setup'); setParams(n, { replace: true }) }} />
      </div>
    </>
  )
}

function ProfileCard() {
  const qc = useQueryClient()
  const q = useQuery({ queryKey: ['me', 'profile'], queryFn: () => gql<{ me: Principal }>(ME).then(r => r.me) })
  const [form, setForm] = useState<Record<EditableKey, string>>(() => blankForm())
  const [ok, setOk] = useState(false)

  useEffect(() => {
    if (q.data) {
      const p = q.data.profile
      setForm({
        displayName: p.displayName ?? '', firstName: p.firstName ?? '', lastName: p.lastName ?? '',
        phoneNumber: p.phoneNumber ?? '', avatarUrl: p.avatarUrl ?? '', locale: p.locale ?? '', timeZone: p.timeZone ?? '',
      })
    }
  }, [q.data])

  const save = useMutation({
    mutationFn: () => {
      const p = q.data!
      // chỉ gửi field đã đổi + expectedVersion của Principal vừa đọc
      const changed: Record<string, unknown> = { expectedVersion: p.version }
      for (const k of EDITABLE) {
        const cur = (p.profile[k] ?? '') as string
        if (form[k] !== cur) changed[k] = form[k]
      }
      return gql(UPDATE, { input: changed })
    },
    onSuccess: () => { setOk(true); qc.invalidateQueries({ queryKey: ['me'] }) },
  })

  return (
    <Card title="Thông tin cá nhân">
      {q.isLoading && <p className="text-sm text-stone-500">Đang tải…</p>}
      <ErrorMsg error={q.error} />
      {q.data && (
        <form className="space-y-3" onSubmit={(e: FormEvent) => { e.preventDefault(); setOk(false); save.mutate() }}>
          <div className="text-sm text-stone-500">Email: <span className="font-medium text-stone-700">{q.data.profile.email}</span></div>
          <Field label="Tên hiển thị"><Input value={form.displayName} onChange={e => setForm({ ...form, displayName: e.target.value })} /></Field>
          <div className="grid grid-cols-2 gap-3">
            <Field label="Họ"><Input value={form.lastName} onChange={e => setForm({ ...form, lastName: e.target.value })} /></Field>
            <Field label="Tên"><Input value={form.firstName} onChange={e => setForm({ ...form, firstName: e.target.value })} /></Field>
          </div>
          <Field label="Số điện thoại"><Input value={form.phoneNumber} onChange={e => setForm({ ...form, phoneNumber: e.target.value })} /></Field>
          <Field label="Avatar URL"><Input value={form.avatarUrl} onChange={e => setForm({ ...form, avatarUrl: e.target.value })} /></Field>
          <div className="grid grid-cols-2 gap-3">
            <Field label="Ngôn ngữ (locale)" hint="vd: vi-VN"><Input value={form.locale} onChange={e => setForm({ ...form, locale: e.target.value })} /></Field>
            <Field label="Múi giờ" hint="vd: Asia/Ho_Chi_Minh"><Input value={form.timeZone} onChange={e => setForm({ ...form, timeZone: e.target.value })} /></Field>
          </div>
          <ErrorMsg error={save.error} />
          {ok && !save.isPending && <p className="text-sm text-green-700">Đã lưu hồ sơ.</p>}
          <Button disabled={save.isPending}>{save.isPending ? 'Đang lưu…' : 'Lưu thay đổi'}</Button>
        </form>
      )}
    </Card>
  )
}

function PasswordCard() {
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [confirm, setConfirm] = useState('')
  const [ok, setOk] = useState(false)
  const [localErr, setLocalErr] = useState<string | null>(null)

  const change = useMutation({
    mutationFn: () => gql<{ changeMyPassword: boolean }>(CHANGE_PW, { current, next }),
    onSuccess: () => { setOk(true); setCurrent(''); setNext(''); setConfirm('') },
  })

  function submit(e: FormEvent) {
    e.preventDefault(); setOk(false); setLocalErr(null)
    if (next.length < 12) { setLocalErr('Mật khẩu mới phải có ít nhất 12 ký tự.'); return }
    if (next !== confirm) { setLocalErr('Xác nhận mật khẩu không khớp.'); return }
    change.mutate()
  }

  return (
    <Card title="Đổi mật khẩu">
      <form className="space-y-3" onSubmit={submit}>
        <Field label="Mật khẩu hiện tại"><Input type="password" required value={current} onChange={e => setCurrent(e.target.value)} /></Field>
        <Field label="Mật khẩu mới" hint="Tối thiểu 12 ký tự."><Input type="password" required minLength={12} value={next} onChange={e => setNext(e.target.value)} /></Field>
        <Field label="Xác nhận mật khẩu mới"><Input type="password" required value={confirm} onChange={e => setConfirm(e.target.value)} /></Field>
        {localErr && <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800">{localErr}</div>}
        <ErrorMsg error={change.error} />
        {ok && <p className="text-sm text-green-700">Đã đổi mật khẩu.</p>}
        <Button disabled={change.isPending}>{change.isPending ? 'Đang đổi…' : 'Đổi mật khẩu'}</Button>
      </form>
    </Card>
  )
}

function SecurityCard({ setupTotp, clearSetup }: { setupTotp: boolean; clearSetup: () => void }) {
  const qc = useQueryClient()
  const q = useQuery({ queryKey: ['security'], queryFn: () => gql<{ mySecurityProfile: SecurityProfile }>(SECURITY).then(r => r.mySecurityProfile) })
  const [enrolling, setEnrolling] = useState(false)
  const [regenCodes, setRegenCodes] = useState<string[] | null>(null)

  // Cách 1: sau bootstrap -> /profile?setup=totp. Tự mở enroll NẾU chưa bật TOTP (idempotent).
  const autoStart = useMemo(() => setupTotp && q.data != null && !q.data.totpEnabled, [setupTotp, q.data])
  useEffect(() => { if (autoStart) setEnrolling(true) }, [autoStart])
  // Nếu đã bật rồi mà có setup=totp thì dọn param (idempotent, không mở gì).
  useEffect(() => { if (setupTotp && q.data?.totpEnabled) clearSetup() }, [setupTotp, q.data?.totpEnabled]) // eslint-disable-line

  const disable = useMutation({
    mutationFn: (id: string) => gql<{ disableMyMfa: boolean }>(DISABLE, { id }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['security'] }),
  })
  const regen = useMutation({
    mutationFn: (id: string) => gql<{ regenerateRecoveryCodes: { recoveryCodes: string[] } }>(REGEN, { id }),
    onSuccess: r => setRegenCodes(r.regenerateRecoveryCodes.recoveryCodes ?? []),
  })

  function finishEnroll() {
    setEnrolling(false); clearSetup()
    qc.invalidateQueries({ queryKey: ['security'] })
  }

  return (
    <Card title="Bảo mật (xác thực 2 lớp)">
      {q.isLoading && <p className="text-sm text-stone-500">Đang tải…</p>}
      <ErrorMsg error={q.error} />
      {q.data && (
        <div className="space-y-4">
          <div className="flex flex-wrap items-center gap-2 text-sm">
            <span>Trạng thái TOTP:</span>
            <Badge tone={q.data.totpEnabled ? 'ok' : 'warn'}>{q.data.totpEnabled ? 'Đã bật' : 'Chưa bật'}</Badge>
            {q.data.totpRequired && <Badge tone="info">Bắt buộc</Badge>}
          </div>

          {q.data.authenticators.length > 0 && (
            <table className="w-full text-left text-sm">
              <thead className="border-b text-xs uppercase text-stone-500"><tr><th className="py-2">Tên</th><th>Loại</th><th>Trạng thái</th><th className="text-right">Thao tác</th></tr></thead>
              <tbody>
                {q.data.authenticators.map(a => (
                  <tr key={a.authenticatorId} className="border-b last:border-0">
                    <td className="py-2">{a.displayName ?? a.authenticatorId}</td>
                    <td>{a.type}</td>
                    <td><Badge tone={/ACTIVE|ENABL/i.test(a.status) ? 'ok' : 'neutral'}>{a.status}</Badge></td>
                    <td className="space-x-2 text-right">
                      <Button variant="ghost" disabled={regen.isPending} onClick={() => regen.mutate(a.authenticatorId)}>Tạo lại recovery codes</Button>
                      <Button variant="danger" disabled={disable.isPending} onClick={() => { if (confirm('Tắt TOTP cho authenticator này?')) disable.mutate(a.authenticatorId) }}>Tắt</Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
          <ErrorMsg error={disable.error} />
          <ErrorMsg error={regen.error} />

          {regenCodes && (
            <div className="space-y-2">
              <p className="text-sm text-stone-600">Mã khôi phục mới (chỉ hiển thị một lần):</p>
              <pre className="rounded-lg bg-stone-100 p-3 text-sm">{regenCodes.join('\n')}</pre>
              <Button variant="ghost" onClick={() => setRegenCodes(null)}>Đóng</Button>
            </div>
          )}

          {enrolling
            ? <TotpEnroll autoStart={autoStart} onDone={finishEnroll} onCancel={() => { setEnrolling(false); clearSetup() }} />
            : !q.data.totpEnabled && <Button onClick={() => setEnrolling(true)}>Bật TOTP</Button>}
        </div>
      )}
    </Card>
  )
}

function blankForm(): Record<EditableKey, string> {
  return { displayName: '', firstName: '', lastName: '', phoneNumber: '', avatarUrl: '', locale: '', timeZone: '' }
}
