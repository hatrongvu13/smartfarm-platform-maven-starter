import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, identity } from '../lib/api'
import { Button, Card, ErrorMsg, Field, Input, PageHeader, QueryState } from '../components/ui'
import TotpSettings from '../components/TotpSettings'
import { vi } from '../i18n/vi'

type Me = { userId: string; email: string; displayName: string; locale: string; timeZone: string; roles: string[]; permissions: string[] }

// GET/PATCH /api/identity/me · POST /api/identity/me/password
// MFA: xem TotpSettings (factor FACTOR_TOTP ở identity-service).
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
      <PageHeader title={vi.profile.title} sub={me.data?.email} />
      <div className="grid gap-4 lg:grid-cols-2">
        <Card title={vi.profile.info}>
          {/* Chưa tải xong thì không hiện form: tránh người dùng bấm Lưu với ô trống và ghi đè dữ liệu thật */}
          <QueryState query={me} loadingLabel={vi.profile.loading}>
            {() => (
          <form className="space-y-3" onSubmit={(e: FormEvent) => { e.preventDefault(); save.mutate() }}>
            <Field label={vi.profile.displayName}><Input required value={value('displayName')} onChange={e => setProfile({ ...profile, displayName: e.target.value })} /></Field>
            <Field label={vi.profile.locale}><Input value={value('locale')} onChange={e => setProfile({ ...profile, locale: e.target.value })} /></Field>
            <Field label={vi.profile.timeZone}><Input value={value('timeZone')} onChange={e => setProfile({ ...profile, timeZone: e.target.value })} /></Field>
            <ErrorMsg error={save.error} />
            {save.isSuccess && <p role="status" className="text-sm text-emerald-700">{vi.common.saved}</p>}
            <Button disabled={save.isPending}>{vi.common.save}</Button>
          </form>
            )}
          </QueryState>
        </Card>
        <Card title={vi.profile.changePassword}>
          <form className="space-y-3" onSubmit={(e: FormEvent) => { e.preventDefault(); changePassword.mutate() }}>
            <Field label={vi.profile.currentPassword}><Input type="password" required autoComplete="current-password" value={password.currentPassword} onChange={e => setPassword({ ...password, currentPassword: e.target.value })} /></Field>
            <Field label={vi.profile.newPassword} hint={vi.profile.newPasswordHint}><Input type="password" required minLength={12} autoComplete="new-password" value={password.newPassword} onChange={e => setPassword({ ...password, newPassword: e.target.value })} /></Field>
            <ErrorMsg error={changePassword.error} />
            {changePassword.isSuccess && <p role="status" className="text-sm text-emerald-700">{vi.profile.passwordChanged}</p>}
            <Button disabled={changePassword.isPending}>{vi.profile.changePassword}</Button>
          </form>
        </Card>
        <TotpSettings />
      </div>
    </>
  )
}
