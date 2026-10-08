import { useEffect, useState, type FormEvent } from 'react'
import { QRCodeSVG } from 'qrcode.react'
import { gql } from '../lib/api'
import { Button, ErrorMsg, Field, Input } from './ui'

type Enrollment = { authenticatorId: string; otpauthUri: string; expiresAt?: string }

const BEGIN = `mutation($displayName: String) { beginTotpEnrollment(displayName: $displayName) { authenticatorId otpauthUri expiresAt } }`
const CONFIRM = `mutation($authenticatorId: ID!, $code: String!) { confirmTotpEnrollment(authenticatorId: $authenticatorId, code: $code) { authenticatorId recoveryCodes } }`

/**
 * Luồng bật TOTP (đã đăng nhập, dùng GraphQL): beginTotpEnrollment -> QR -> nhập mã 6 số
 * -> confirmTotpEnrollment -> hiển thị recovery codes một lần.
 * `autoStart` để tự mở QR ngay (dùng cho /profile?setup=totp sau bootstrap).
 */
export default function TotpEnroll({ autoStart = false, onDone, onCancel }: {
  autoStart?: boolean
  onDone: () => void
  onCancel?: () => void
}) {
  const [info, setInfo] = useState<Enrollment | null>(null)
  const [codes, setCodes] = useState<string[] | null>(null)
  const [code, setCode] = useState('')
  const [err, setErr] = useState<unknown>()
  const [busy, setBusy] = useState(false)
  const [started, setStarted] = useState(false)

  async function begin() {
    setBusy(true); setErr(undefined)
    try {
      const r = await gql<{ beginTotpEnrollment: Enrollment }>(BEGIN, { displayName: 'SmartFarm' })
      setInfo(r.beginTotpEnrollment); setStarted(true)
    } catch (x) { setErr(x) } finally { setBusy(false) }
  }

  useEffect(() => {
    if (autoStart && !started) void begin()
  }, [autoStart, started])

  async function confirm(e: FormEvent) {
    e.preventDefault(); setBusy(true); setErr(undefined)
    try {
      const r = await gql<{ confirmTotpEnrollment: { authenticatorId: string; recoveryCodes: string[] } }>(
        CONFIRM, { authenticatorId: info!.authenticatorId, code },
      )
      setCodes(r.confirmTotpEnrollment.recoveryCodes ?? [])
    } catch (x) { setErr(x) } finally { setBusy(false) }
  }

  if (codes) return (
    <div className="space-y-3">
      <p className="text-sm text-stone-600">Đã bật TOTP. Lưu các mã khôi phục dưới đây — chúng chỉ hiển thị một lần.</p>
      <pre className="rounded-lg bg-stone-100 p-3 text-sm">{codes.join('\n')}</pre>
      <Button className="w-full" onClick={onDone}>Tôi đã lưu — hoàn tất</Button>
    </div>
  )

  if (!started) return (
    <div className="space-y-3">
      <p className="text-sm text-stone-600">Bật xác thực 2 lớp (TOTP) để tăng bảo mật tài khoản.</p>
      <ErrorMsg error={err} />
      <div className="flex gap-2">
        <Button disabled={busy} onClick={begin}>{busy ? 'Đang khởi tạo…' : 'Bật TOTP'}</Button>
        {onCancel && <Button variant="ghost" onClick={onCancel}>Hủy</Button>}
      </div>
    </div>
  )

  return (
    <form onSubmit={confirm} className="space-y-4">
      <p className="text-sm text-stone-600">Quét mã QR bằng Google Authenticator / 1Password / Authy, rồi nhập mã 6 số.</p>
      {info && <div className="flex justify-center rounded-lg border border-stone-200 p-4"><QRCodeSVG value={info.otpauthUri} size={168} /></div>}
      <Field label="Mã 6 số hiện tại">
        <Input inputMode="numeric" pattern="\d{6}" maxLength={6} required value={code} onChange={e => setCode(e.target.value)} className="text-center text-lg tracking-widest" />
      </Field>
      <ErrorMsg error={err} />
      <div className="flex gap-2">
        <Button disabled={busy || !info}>Xác nhận</Button>
        {onCancel && <Button type="button" variant="ghost" onClick={onCancel}>Hủy</Button>}
      </div>
    </form>
  )
}
