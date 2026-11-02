import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, identity } from '../lib/api'
import { Badge, Button, Card, ErrorMsg, Field, Input } from './ui'

type Enrollment = { secret: string; otpauthUri: string }
type MfaStatus = { totpEnabled: boolean; recoveryCodesRemaining: number }
type RecoveryCodes = { codes: string[] }

// /api/identity/me/mfa[...]: bật/tắt TOTP và mã khôi phục. Bật xong, lần đăng nhập sau identity sẽ hỏi mã 6 số
// (hoặc một mã khôi phục nếu mất điện thoại). Mã khôi phục chỉ hiện đúng một lần.
export default function TotpSettings() {
  const qc = useQueryClient()
  const status = useQuery({ queryKey: ['mfa'], queryFn: () => api<MfaStatus>(identity('/me/mfa')) })
  const [enrollment, setEnrollment] = useState<Enrollment | null>(null)
  const [recovery, setRecovery] = useState<string[] | null>(null)
  const [code, setCode] = useState('')
  const refresh = () => { setCode(''); qc.invalidateQueries({ queryKey: ['mfa'] }) }

  const begin = useMutation({
    mutationFn: () => api<Enrollment>(identity('/me/mfa/totp'), { method: 'POST' }),
    onSuccess: setEnrollment,
  })
  const confirm = useMutation({
    mutationFn: () => api<RecoveryCodes>(identity('/me/mfa/totp/confirm'), { method: 'POST', body: { code } }),
    onSuccess: result => { setEnrollment(null); setRecovery(result.codes); refresh() },
  })
  const regenerate = useMutation({
    mutationFn: () => api<RecoveryCodes>(identity('/me/mfa/recovery-codes'), { method: 'POST', body: { code } }),
    onSuccess: result => { setRecovery(result.codes); refresh() },
  })
  const disable = useMutation({
    mutationFn: () => api(identity('/me/mfa/totp'), { method: 'DELETE', body: { code } }),
    onSuccess: () => { setRecovery(null); refresh() },
  })

  const codeInput = (
    <Field label="Mã 6 số từ ứng dụng">
      <Input required inputMode="numeric" pattern="\d{6}" maxLength={6} autoComplete="one-time-code"
        value={code} onChange={e => setCode(e.target.value)} />
    </Field>
  )

  return (
    <Card title="Xác thực hai bước (TOTP)" actions={status.data && <Badge tone={status.data.totpEnabled ? 'ok' : 'neutral'}>{status.data.totpEnabled ? 'Đang bật' : 'Đang tắt'}</Badge>}>
      <ErrorMsg error={status.error ?? begin.error ?? confirm.error ?? regenerate.error ?? disable.error} />

      {recovery && (
        <div className="space-y-2 text-sm" role="status">
          <p className="font-medium">Lưu các mã khôi phục này ở nơi an toàn. Sẽ không hiện lại.</p>
          <p>Mỗi mã dùng được một lần, để đăng nhập khi mất điện thoại.</p>
          <ul className="grid grid-cols-2 gap-1 rounded bg-stone-100 p-2 font-mono text-xs">
            {recovery.map(c => <li key={c}>{c}</li>)}
          </ul>
          <Button variant="ghost" onClick={() => setRecovery(null)}>Tôi đã lưu</Button>
        </div>
      )}

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
          <p>Còn {status.data.recoveryCodesRemaining} mã khôi phục chưa dùng.</p>
          <p>Nhập mã hiện tại để sinh lại mã khôi phục (bộ cũ mất hiệu lực) hoặc để tắt xác thực hai bước.</p>
          {codeInput}
          <div className="flex gap-2">
            <Button type="button" variant="ghost" disabled={regenerate.isPending || code.length !== 6}
              onClick={() => regenerate.mutate()}>Sinh lại mã khôi phục</Button>
            <Button variant="danger" disabled={disable.isPending}>Tắt xác thực hai bước</Button>
          </div>
        </form>
      )}
    </Card>
  )
}
