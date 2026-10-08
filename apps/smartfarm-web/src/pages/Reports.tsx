import { useState, type FormEvent } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { api } from '../lib/api'
import { useFarm } from '../lib/farm'
import { Badge, Button, Card, DevOnly, ErrorMsg, Field, Input, Json, PageHeader, statusTone } from '../components/ui'

// DEV-only: POST /api/v1/reports {farmId,type,format} -> {jobId} · GET /{id} · GET /{id}/download -> {url,contentType,expiresAt?}
export default function Reports() {
  const { farmId } = useFarm()
  const [form, setForm] = useState({ type: 'LIVESTOCK_INVENTORY', format: 'CSV' })
  const [jobId, setJobId] = useState('')

  const request = useMutation({
    mutationFn: () => api('/api/v1/reports', { method: 'POST', idempotent: true, body: { farmId, ...form } }),
    onSuccess: r => setJobId(r.jobId),
  })
  const job = useQuery({
    queryKey: ['report', jobId],
    enabled: !!jobId,
    queryFn: () => api<any>(`/api/v1/reports/${jobId}`),
    refetchInterval: q => (/COMPLET|FAIL/.test(String(q.state.data?.status ?? '')) ? false : 2000),
  })
  const done = /COMPLET/.test(String(job.data?.status ?? ''))
  const dl = useQuery({ queryKey: ['reportDl', jobId], enabled: done, queryFn: () => api<{ url: string; contentType: string; expiresAt?: string }>(`/api/v1/reports/${jobId}/download`) })

  return (
    <>
      <PageHeader title="Báo cáo" sub={<>Export bất đồng bộ <DevOnly /> — chỉ loại LIVESTOCK_* hoạt động đầy đủ.</>} />
      <div className="grid gap-4 lg:grid-cols-2">
        <Card title="Yêu cầu export">
          <form className="space-y-3" onSubmit={(e: FormEvent) => { e.preventDefault(); request.mutate() }}>
            <Field label="Loại"><Input value={form.type} onChange={e => setForm({ ...form, type: e.target.value })} /></Field>
            <Field label="Định dạng"><Input value={form.format} onChange={e => setForm({ ...form, format: e.target.value })} /></Field>
            <ErrorMsg error={request.error} />
            <Button disabled={request.isPending}>Gửi yêu cầu</Button>
          </form>
        </Card>
        <Card title="Tiến trình job">
          {!jobId && <p className="text-sm text-stone-500">Chưa có job.</p>}
          {jobId && (
            <div className="space-y-3">
              <div className="font-mono text-xs text-stone-500">{jobId}</div>
              <ErrorMsg error={job.error} />
              {job.data && <Badge tone={statusTone(job.data.status)}>{job.data.status}</Badge>}
              {dl.data && <a href={dl.data.url} target="_blank" rel="noreferrer"><Button>Tải xuống ({dl.data.contentType})</Button></a>}
              <ErrorMsg error={dl.error} />
              <Json data={job.data} />
            </div>
          )}
        </Card>
      </div>
    </>
  )
}
