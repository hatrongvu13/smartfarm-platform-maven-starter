import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { gql } from '../../lib/api'
import { useAuth, hasScope } from '../../lib/auth'
import { CONFIGURED_PERMISSIONS } from '../../generated/authorizationCatalog'
import { Badge, Button, Card, ErrorMsg, Field, Input, PageHeader } from '../../components/ui'
import { useNotifications } from '../../components/notifications'

const CATALOG = `query {
  roles(page:{size:100}) { nodes { roleId code name type permissions { code resourceType action description } } }
  permissions(page:{size:100}) { nodes { code resourceType action description } }
}`
const CREATE_ROLE = `mutation($input:AuthorizationRoleInput!){createAuthorizationRole(input:$input){code name type}}`
const UPDATE_ROLE = `mutation($input:AuthorizationRoleInput!){updateAuthorizationRole(input:$input){code name type}}`
const DELETE_ROLE = `mutation($code:String!){deleteAuthorizationRole(code:$code)}`
const CREATE_PERMISSION = `mutation($input:AuthorizationPermissionInput!){createAuthorizationPermission(input:$input){code resourceType action description}}`
const UPDATE_PERMISSION = `mutation($input:AuthorizationPermissionInput!){updateAuthorizationPermission(input:$input){code resourceType action description}}`
const DELETE_PERMISSION = `mutation($code:String!){deleteAuthorizationPermission(code:$code)}`
const GRANT = `mutation($r:String!,$p:String!){grantPermissionToAuthorizationRole(roleCode:$r,permissionCode:$p){code permissions{code}}}`
const REVOKE = `mutation($r:String!,$p:String!){revokePermissionFromAuthorizationRole(roleCode:$r,permissionCode:$p){code permissions{code}}}`

type Permission = { code:string; resourceType:string; action:string; description?:string }
type Role = { roleId:string; code:string; name:string; type:string; permissions:Permission[] }
type Catalog = { roles:{nodes:Role[]}; permissions:{nodes:Permission[]} }
type PermissionForm = { code:string; resourceType:string; action:string; description:string }

const emptyPermission: PermissionForm = { code:'', resourceType:'', action:'', description:'' }
const protectedRoles = new Set(['USER', 'ADMIN', 'SUPERADMIN', 'PLATFORM_ADMIN'])

export default function Authorization() {
  const { me } = useAuth()
  const queryClient = useQueryClient()
  const { notify } = useNotifications()
  const publishedFingerprint = useRef('')
  const canRole = hasScope(me, 'identity:role:manage')
  const canPermission = hasScope(me, 'identity:permission:manage')
  const [roleCode, setRoleCode] = useState('')
  const [roleName, setRoleName] = useState('')
  const [editingRole, setEditingRole] = useState<Role | null>(null)
  const [permission, setPermission] = useState<PermissionForm>(emptyPermission)
  const [editingPermission, setEditingPermission] = useState<Permission | null>(null)

  const catalog = useQuery({ queryKey:['authorizationCatalog'], queryFn:() => gql<Catalog>(CATALOG) })
  const refresh = async () => { await queryClient.invalidateQueries({ queryKey:['authorizationCatalog'] }) }
  const mutation = useMutation({
    mutationFn: ({ query, variables }: { query:string; variables:Record<string,unknown> }) => gql(query, variables),
    onSuccess: async () => { notify({ title:'Thao tác thành công', message:'Dữ liệu vai trò và quyền đã được cập nhật.', tone:'success' }); await refresh() },
    onError: error => notify({ title:'Không thể cập nhật phân quyền', message:error instanceof Error ? error.message : 'Đã xảy ra lỗi không xác định.', tone:'error' }),
  })

  const consistency = useMemo(() => {
    const db = new Map((catalog.data?.permissions.nodes ?? []).map(value => [value.code, value]))
    const configured = new Map(CONFIGURED_PERMISSIONS.map(value => [value.code, value]))
    const missingInDatabase = CONFIGURED_PERMISSIONS.filter(value => !db.has(value.code))
    const metadataMismatch = CONFIGURED_PERMISSIONS.flatMap(expected => {
      const actual = db.get(expected.code)
      if (!actual) return []
      const differences:string[] = []
      if (actual.resourceType !== expected.resourceType) differences.push(`resourceType DB=${actual.resourceType}, YAML=${expected.resourceType}`)
      if (actual.action !== expected.action) differences.push(`action DB=${actual.action}, YAML=${expected.action}`)
      if ((actual.description ?? '') !== (expected.description ?? '')) differences.push('tên hiển thị/mô tả khác YAML')
      return differences.length ? [{ code:expected.code, differences }] : []
    })
    const manual = (catalog.data?.permissions.nodes ?? []).filter(value => !configured.has(value.code))
    const invalidCase = (catalog.data?.permissions.nodes ?? []).filter(value => value.code !== value.code.toLowerCase())
    const pairGroups = new Map<string,string[]>()
    for (const value of catalog.data?.permissions.nodes ?? []) {
      const key = `${value.resourceType}:${value.action}`
      pairGroups.set(key, [...(pairGroups.get(key) ?? []), value.code])
    }
    const duplicatePairs = [...pairGroups.entries()].filter(([,codes]) => codes.length > 1)
    return { missingInDatabase, metadataMismatch, manual, invalidCase, duplicatePairs }
  }, [catalog.data])

  useEffect(() => {
    if (catalog.isLoading || !catalog.data) return
    const details = [
      ...consistency.missingInDatabase.map(value => `Thiếu trong DB: ${value.code}`),
      ...consistency.metadataMismatch.map(value => `${value.code}: ${value.differences.join('; ')}`),
      ...consistency.invalidCase.map(value => `Sai lowercase: ${value.code}`),
      ...consistency.duplicatePairs.map(([pair,codes]) => `Trùng ${pair}: ${codes.join(', ')}`),
    ]
    const fingerprint = JSON.stringify(details)
    if (publishedFingerprint.current === fingerprint) return
    publishedFingerprint.current = fingerprint
    if (details.length) notify({ key:'authorization-sync', title:'Catalog quyền chưa đồng bộ', message:`Phát hiện ${details.length} vấn đề giữa YAML và dữ liệu runtime.`, tone:'warning', details })
    else notify({ key:'authorization-sync', title:'Catalog quyền đã đồng bộ', message:'Permission cấu hình đang khớp với dữ liệu Identity.', tone:'success' })
  }, [catalog.data, catalog.isLoading, consistency, notify])

  function resetRole() { setRoleCode(''); setRoleName(''); setEditingRole(null) }
  function resetPermission() { setPermission(emptyPermission); setEditingPermission(null) }
  function submitRole(event:FormEvent) {
    event.preventDefault()
    mutation.mutate({ query:editingRole ? UPDATE_ROLE : CREATE_ROLE, variables:{ input:{ code:roleCode.trim().toUpperCase(), name:roleName.trim() } } })
    resetRole()
  }
  function submitPermission(event:FormEvent) {
    event.preventDefault()
    mutation.mutate({
      query:editingPermission ? UPDATE_PERMISSION : CREATE_PERMISSION,
      variables:{ input:{ code:permission.code.trim().toLowerCase(), resourceType:permission.resourceType.trim().toLowerCase(), action:permission.action.trim().toLowerCase(), description:permission.description.trim() } },
    })
    resetPermission()
  }
  function editRole(value:Role) { setEditingRole(value); setRoleCode(value.code); setRoleName(value.name) }
  function editPermission(value:Permission) {
    setEditingPermission(value)
    setPermission({ code:value.code, resourceType:value.resourceType, action:value.action, description:value.description ?? '' })
  }
  function removeRole(value:Role) {
    if (confirm(`Xóa role ${value.code}? Role đang được gán cho user sẽ bị backend từ chối.`)) mutation.mutate({ query:DELETE_ROLE, variables:{ code:value.code } })
  }
  function removePermission(value:Permission) {
    if (confirm(`Xóa permission ${value.code}? Hãy thu hồi khỏi tất cả role trước.`)) mutation.mutate({ query:DELETE_PERMISSION, variables:{ code:value.code } })
  }

  const hasWarnings = consistency.missingInDatabase.length || consistency.metadataMismatch.length || consistency.invalidCase.length || consistency.duplicatePairs.length

  return <>
    <PageHeader title="Vai trò và quyền" sub="Catalog YAML được đồng bộ cộng dồn. Thao tác sửa, thu hồi và xóa được thực hiện thủ công có kiểm soát." />
    <ErrorMsg error={catalog.error || mutation.error} />

    <div className="mb-4 flex min-w-0 flex-wrap items-center justify-between gap-2 rounded-xl border border-stone-200 bg-white p-3 text-sm shadow-sm">
      <span className="min-w-0 break-words text-stone-600">Trạng thái đồng bộ quyền được gửi vào chuông thông báo.</span>
      <Badge tone={hasWarnings ? 'warn' : 'success'}>{hasWarnings ? 'Có cảnh báo' : 'Đã đồng bộ'}</Badge>
    </div>

    <div className="grid min-w-0 gap-4 lg:grid-cols-[repeat(2,minmax(0,1fr))]">
      {canRole && <Card title={editingRole ? `Sửa tên role ${editingRole.code}` : 'Tạo vai trò'}>
        <form className="space-y-3" onSubmit={submitRole}>
          <Field label="Mã vai trò"><Input required disabled={!!editingRole} value={roleCode} onChange={e => setRoleCode(e.target.value.toUpperCase())}/></Field>
          <Field label="Tên hiển thị"><Input required value={roleName} onChange={e => setRoleName(e.target.value)}/></Field>
          <div className="flex gap-2"><Button disabled={mutation.isPending}>{editingRole ? 'Lưu tên vai trò' : 'Tạo vai trò'}</Button>{editingRole && <Button type="button" variant="ghost" onClick={resetRole}>Hủy</Button>}</div>
        </form>
      </Card>}

      {canPermission && <Card title={editingPermission ? `Sửa permission ${editingPermission.code}` : 'Tạo permission'}>
        <form className="space-y-3" onSubmit={submitPermission}>
          <Field label="Code"><Input required disabled={!!editingPermission} value={permission.code} onChange={e => setPermission(v => ({...v,code:e.target.value.toLowerCase()}))}/></Field>
          <Field label="Resource type"><Input required value={permission.resourceType} onChange={e => setPermission(v => ({...v,resourceType:e.target.value.toLowerCase()}))}/></Field>
          <Field label="Action"><Input required value={permission.action} onChange={e => setPermission(v => ({...v,action:e.target.value.toLowerCase()}))}/></Field>
          <Field label="Tên hiển thị / mô tả"><Input required value={permission.description} onChange={e => setPermission(v => ({...v,description:e.target.value}))}/></Field>
          <div className="flex gap-2"><Button disabled={mutation.isPending}>{editingPermission ? 'Lưu permission' : 'Tạo permission'}</Button>{editingPermission && <Button type="button" variant="ghost" onClick={resetPermission}>Hủy</Button>}</div>
        </form>
      </Card>}
    </div>

    <Card title="Permission catalog" className="mt-4">
      <div className="max-h-[min(62dvh,42rem)] overflow-auto overscroll-contain rounded-lg border border-stone-200"><table className="w-full min-w-[46rem] text-left text-sm"><thead className="sticky top-0 z-10 border-b bg-white text-xs uppercase text-stone-500 shadow-sm"><tr><th className="py-2">Tên hiển thị</th><th>Code</th><th>Resource / Action</th><th>Nguồn</th><th></th></tr></thead><tbody>
        {catalog.data?.permissions.nodes.map(value => {
          const configured = CONFIGURED_PERMISSIONS.some(x => x.code === value.code)
          return <tr key={value.code} className="border-b last:border-0"><td className="py-2">{value.description || '—'}</td><td><code className="text-xs">{value.code}</code></td><td>{value.resourceType} / {value.action}</td><td><Badge tone={configured?'warn':'info'}>{configured?'YAML':'MANUAL'}</Badge></td><td><div className="flex flex-wrap justify-end gap-2">{canPermission && <Button variant="ghost" onClick={() => editPermission(value)}>Sửa</Button>}{canPermission && <Button variant="danger" disabled={configured} onClick={() => removePermission(value)}>{configured?'Bỏ YAML trước':'Xóa'}</Button>}</div></td></tr>
        })}
      </tbody></table></div>
    </Card>

    <div className="mt-4 max-h-[min(72dvh,56rem)] min-w-0 space-y-4 overflow-y-auto overscroll-contain rounded-xl border border-stone-200 bg-stone-50 p-3">{catalog.data?.roles.nodes.map(role => {
      const protectedRole = protectedRoles.has(role.code) || role.type !== 'TENANT'
      return <Card key={role.roleId} title={`${role.name} (${role.code})`} actions={<div className="flex gap-2"><Badge tone={role.type==='SYSTEM'?'warn':'info'}>{role.type}</Badge>{canRole && !protectedRole && <Button variant="ghost" onClick={() => editRole(role)}>Sửa tên</Button>}{canRole && !protectedRole && <Button variant="danger" onClick={() => removeRole(role)}>Xóa role</Button>}</div>}>
        <div className="grid max-h-[min(48dvh,30rem)] min-w-0 gap-2 overflow-y-auto overscroll-contain rounded-lg border border-stone-200 bg-white p-2 md:grid-cols-2 xl:grid-cols-3">{catalog.data!.permissions.nodes.map(permission => {
          const checked=role.permissions.some(value => value.code===permission.code)
          return <label key={permission.code} className="flex min-w-0 items-start gap-2 rounded border p-2 text-sm"><input type="checkbox" checked={checked} disabled={!canPermission || role.type==='PLATFORM'} onChange={() => mutation.mutate({ query:checked?REVOKE:GRANT, variables:{r:role.code,p:permission.code} })}/><span className="min-w-0"><span className="block break-all font-mono text-xs">{permission.code}</span>{permission.description && <span className="mt-1 block break-words text-xs text-stone-500">{permission.description}</span>}</span></label>
        })}</div>
      </Card>
    })}</div>
  </>
}
