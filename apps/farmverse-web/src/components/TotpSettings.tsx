import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, identity } from '../lib/api'
import { Badge, Button, Card, ErrorMsg, Field, Input } from './ui'

type Enrollment = { secret: string; otpauthUri: string }

// /api/identity/me/mfa[...]: bật/tắt TOTP. Bật xong, lần đăng nhập sau identity sẽ hỏi mã 6 số.
export default function TotpSettings() {
  const qc = useQueryClient()
  const status = useQuery({ queryKey: ['mfa'], queryFn: () => api<{ totpEnabled: boolean }>(identity('/me/mfa')) })
  const [enrollment, setEnrollment] = useState<Enrollment | null>(null)
  const [code, setCode] = useState('')
  const done = () => { setCode(''); setEnrollment(null); qc.invalidateQueries({ queryKey: ['mfa'] }) }

  const begin = useMutation({
    mutationFn: () => api<Enrollment>(identity('/me/mfa/totp'), { method: 'POST' }),
    onSuccess: setEnrollment,
  })
  const confirm = useMutation({
    mutationFn: () => api(identity('/me/mfa/totp/confirm'), { method: 'POST', body: { code } }),
    onSuccess: done,
  })
  const disable = useMutation({
    mutationFn: () => api(identity('/me/mfa/totp'), { method: 'DELETE', body: { code } }),
    onSuccess: done,
  })

  const codeInput = (
    <Field label="Mã 6 số từ ứng dụng">
      <Input required inputMode="numeric" pattern="\d{6}" maxLength={6} autoComplete="one-time-code"
        value={code} onChange={e => setCode(e.target.value)} />
    </Field>
  )

  return (
    <Card title="Xác thực hai bước (TOTP)" actions={status.data && <Badge tone={status.data.totpEnabled ? 'ok' : 'neutral'}>{status.data.totpEnabled ? 'Đang bật' : 'Đang tắt'}</Badge>}>
      <ErrorMsg error={status.error ?? begin.error ?? confirm.error ?? disable.error} />

      {status.data && !status.data.totpEnabled && !enrollment && (
        <div className="space-y-2 text-sm">
          <p>Dùng Google Authenticator, Microsoft Authenticator, 1Password… để sinh mã đăng nhập.</p>
          <Button onClick={() => begin.mutate()} disabled={begin.isPending}>Bật xác thực hai bước</Button>
        </div>
      )}

      {enrollment && (
        <form className="space-y-3 text-sm" onSubmit={(e: FormEvent) => { e.preventDefault(); confirm.mutate() }}>
          <p>1. Trong ứng dụng, chọn "Thêm tài khoản" → "Nhập khóa", dán khóa sau (hoặc mở liên kết trên điện thoại):</p>
          <code className="block break-all rounded bg-stone-100 p-2 text-xs">{enrollment.secret}</code>
          <a className="block break-all text-xs underline" href={enrollment.otpauthUri}>{enrollment.otpauthUri}</a>
          <p>2. Nhập mã ứng dụng hiện ra:</p>
          {codeInput}
          <Button disabled={confirm.isPending}>Xác nhận và bật</Button>
        </form>
      )}

      {status.data?.totpEnabled && (
        <form className="space-y-3 text-sm" onSubmit={(e: FormEvent) => { e.preventDefault(); disable.mutate() }}>
          <p>Muốn tắt, nhập mã hiện tại để xác nhận bạn vẫn giữ thiết bị.</p>
          {codeInput}
          <Button variant="danger" disabled={disable.isPending}>Tắt xác thực hai bước</Button>
        </form>
      )}
    </Card>
  )
}
