import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, identity } from '../lib/api'
import { Button, Card, ErrorMsg, Field, Input, PageHeader } from '../components/ui'

type Me = { userId: string; email: string; displayName: string; locale: string; timeZone: string; roles: string[]; permissions: string[] }

// GET/PATCH /api/identity/me · POST /api/identity/me/password
// MFA (TOTP) tạm dừng: Spring Security 7.1.2 chưa có factor TOTP — chờ chọn hướng.
export default function Profile() {
  const qc = useQueryClient()
  const me = useQuery({ queryKey: ['identity-me'], queryFn: () => api<Me>(identity('/me')) })
  const [profile, setProfile] = useState<{ displayName?: string; locale?: string; timeZone?: string }>({})
  const [password, setPassword] = useState({ currentPassword: '', newPassword: '' })

  const save = useMutation({
    mutationFn: () => api<Me>(identity('/me'), { method: 'PATCH', body: profile }),
    onSuccess: data => { qc.setQueryData(['identity-me'], data); setProfile({}) },
  })
  const changePassword = useMutation({
    mutationFn: () => api(identity('/me/password'), { method: 'POST', body: password }),
    onSuccess: () => setPassword({ currentPassword: '', newPassword: '' }),
  })

  const value = (k: keyof typeof profile) => profile[k] ?? me.data?.[k] ?? ''

  return (
    <>
      <PageHeader title="Hồ sơ của tôi" sub={me.data?.email} />
      <ErrorMsg error={me.error} />
      <div className="grid gap-4 lg:grid-cols-2">
        <Card title="Thông tin">
          <form className="space-y-3" onSubmit={(e: FormEvent) => { e.preventDefault(); save.mutate() }}>
            <Field label="Tên hiển thị"><Input required value={value('displayName')} onChange={e => setProfile({ ...profile, displayName: e.target.value })} /></Field>
            <Field label="Ngôn ngữ"><Input value={value('locale')} onChange={e => setProfile({ ...profile, locale: e.target.value })} /></Field>
            <Field label="Múi giờ"><Input value={value('timeZone')} onChange={e => setProfile({ ...profile, timeZone: e.target.value })} /></Field>
            <ErrorMsg error={save.error} />
            <Button disabled={save.isPending}>Lưu</Button>
          </form>
        </Card>
        <Card title="Đổi mật khẩu">
          <form className="space-y-3" onSubmit={(e: FormEvent) => { e.preventDefault(); changePassword.mutate() }}>
            <Field label="Mật khẩu hiện tại"><Input type="password" required autoComplete="current-password" value={password.currentPassword} onChange={e => setPassword({ ...password, currentPassword: e.target.value })} /></Field>
            <Field label="Mật khẩu mới" hint="Ít nhất 12 ký tự"><Input type="password" required minLength={12} autoComplete="new-password" value={password.newPassword} onChange={e => setPassword({ ...password, newPassword: e.target.value })} /></Field>
            <ErrorMsg error={changePassword.error} />
            {changePassword.isSuccess && <p className="text-sm text-emerald-700">Đã đổi mật khẩu.</p>}
            <Button disabled={changePassword.isPending}>Đổi mật khẩu</Button>
          </form>
        </Card>
      </div>
    </>
  )
}
