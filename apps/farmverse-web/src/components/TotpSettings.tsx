import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, identity } from '../lib/api'
import { Badge, Button, Card, ErrorMsg, Field, Input, Loading } from './ui'
import { useConfirm } from './dialog'
import { vi } from '../i18n/vi'

type Enrollment = { secret: string; otpauthUri: string }
type MfaStatus = { totpEnabled: boolean; recoveryCodesRemaining: number }
type RecoveryCodes = { codes: string[] }

// /api/identity/me/mfa[...]: bật/tắt TOTP và mã khôi phục. Bật xong, lần đăng nhập sau identity sẽ hỏi mã 6 số
// (hoặc một mã khôi phục nếu mất điện thoại). Mã khôi phục chỉ hiện đúng một lần.
export default function TotpSettings() {
  const qc = useQueryClient()
  const ask = useConfirm()
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
    <Field label={vi.totp.codeLabel}>
      <Input required inputMode="numeric" pattern="\d{6}" maxLength={6} autoComplete="one-time-code"
        value={code} onChange={e => setCode(e.target.value)} />
    </Field>
  )

  return (
    <Card title={vi.totp.title} actions={status.data && <Badge tone={status.data.totpEnabled ? 'ok' : 'neutral'}>{status.data.totpEnabled ? vi.totp.on : vi.totp.off}</Badge>}>
      {status.isPending && <Loading label={vi.totp.loading} />}
      {status.isError && <ErrorMsg error={status.error} onRetry={() => status.refetch()} />}
      <ErrorMsg error={begin.error ?? confirm.error ?? regenerate.error ?? disable.error} />

      {recovery && (
        <div className="space-y-2 text-sm" role="status">
          <p className="font-medium">{vi.totp.recoveryTitle}</p>
          <p>{vi.totp.recoveryHelp}</p>
          <ul className="grid grid-cols-2 gap-1 rounded bg-stone-100 p-2 font-mono text-xs">
            {recovery.map(c => <li key={c}>{c}</li>)}
          </ul>
          <Button variant="ghost" onClick={() => setRecovery(null)}>{vi.totp.recoverySaved}</Button>
        </div>
      )}

      {status.data && !status.data.totpEnabled && !enrollment && (
        <div className="space-y-2 text-sm">
          <p>{vi.totp.apps}</p>
          <Button onClick={() => begin.mutate()} disabled={begin.isPending}>{vi.totp.enable}</Button>
        </div>
      )}

      {enrollment && (
        <form className="space-y-3 text-sm" onSubmit={(e: FormEvent) => { e.preventDefault(); confirm.mutate() }}>
          <p>{vi.totp.step1}</p>
          <code className="block break-all rounded bg-stone-100 p-2 text-xs">{enrollment.secret}</code>
          <a className="block break-all text-xs underline" href={enrollment.otpauthUri}>{enrollment.otpauthUri}</a>
          <p>{vi.totp.step2}</p>
          {codeInput}
          <Button disabled={confirm.isPending}>{vi.totp.confirmEnable}</Button>
        </form>
      )}

      {status.data?.totpEnabled && (
        <form className="space-y-3 text-sm" onSubmit={async (e: FormEvent) => {
          e.preventDefault()
          if (await ask({ ...vi.totp.confirmDisable, danger: true })) disable.mutate()
        }}>
          <p>{vi.totp.remaining(status.data.recoveryCodesRemaining)}</p>
          <p>{vi.totp.manageHelp}</p>
          {codeInput}
          <div className="flex gap-2">
            <Button type="button" variant="ghost" disabled={regenerate.isPending || code.length !== 6}
              onClick={async () => {
                if (await ask({ ...vi.totp.confirmRegenerate, danger: true })) regenerate.mutate()
              }}>{vi.totp.regenerate}</Button>
            <Button variant="danger" disabled={disable.isPending}>{vi.totp.disable}</Button>
          </div>
        </form>
      )}
    </Card>
  )
}
