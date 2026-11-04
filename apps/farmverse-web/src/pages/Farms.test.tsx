import { describe, expect, it } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import Farms from './Farms'
import { AuthProvider, type Me } from '../lib/auth'
import { FarmProvider } from '../lib/farm'
import { ConfirmProvider } from '../components/dialog'
import { calls, mockFetch, on } from '../test/http'
import { vi as text } from '../i18n/vi'

const WRITER: Me = { userId: 'u1', name: 'An', roles: ['FARM_MANAGER'], permissions: ['farm:farm:read', 'farm:farm:write'] }
const READER: Me = { ...WRITER, permissions: ['farm:farm:read'] }
const GREEN = { id: 'f1', code: 'green', name: 'Green Valley', status: 'ACTIVE', totalCapacity: 10, barns: [{ code: 'B-1', name: 'Chuồng một', capacity: 10 }] }

function renderFarms() {
  localStorage.setItem('sf.farmId', 'f1')
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <AuthProvider><FarmProvider><ConfirmProvider><Farms /></ConfirmProvider></FarmProvider></AuthProvider>
    </QueryClientProvider>,
  )
  return userEvent.setup()
}

describe('Trang trại', () => {
  it('chỉ đọc: không có form tạo và không có nút xóa', async () => {
    mockFetch(on('GET', '/bff/me', { body: READER }), on('GET', '/api/farm/farms', { body: [GREEN] }))
    renderFarms()

    expect(await screen.findByText(text.farms.barnsOf('Green Valley'))).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: text.common.create })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: text.farms.deleteBarnLabel('B-1') })).not.toBeInTheDocument()
  })

  it('tạo trang trại: gửi đúng body; lỗi 400 hiện theo từng ô', async () => {
    let posted: unknown
    mockFetch(
      on('GET', '/bff/me', { body: WRITER }),
      on('GET', '/api/farm/farms', { body: [] }),
      on('POST', '/api/farm/farms', init => {
        posted = JSON.parse(String(init.body))
        return { status: 400, body: { status: 400, code: 'SF-VALIDATION-001', errors: [{ field: 'code', message: 'đã có người dùng' }] } }
      }),
    )
    const user = renderFarms()
    await screen.findByText(text.farms.emptyCanCreate)

    await user.type(screen.getByLabelText(text.common.code), 'green-valley')
    await user.type(screen.getByLabelText(text.common.name), 'Green Valley')
    await user.click(screen.getByRole('button', { name: text.common.create }))

    const alert = await screen.findByRole('alert')
    expect(posted).toEqual({ code: 'green-valley', name: 'Green Valley', address: '' })
    expect(alert).toHaveTextContent(text.errors.validation.title)
    expect(alert).toHaveTextContent('code: đã có người dùng')
    expect(alert).toHaveTextContent('SF-VALIDATION-001')
  })

  it('ô Mã có gợi ý nối bằng aria-describedby', async () => {
    mockFetch(on('GET', '/bff/me', { body: WRITER }), on('GET', '/api/farm/farms', { body: [] }))
    renderFarms()

    expect(await screen.findByLabelText(text.common.code)).toHaveAccessibleDescription(text.farms.codeHint)
  })

  it('xóa chuồng: Hủy thì không gửi gì, xác nhận thì gửi DELETE', async () => {
    const fetch = mockFetch(
      on('GET', '/bff/me', { body: WRITER }),
      on('GET', '/api/farm/farms', { body: [GREEN] }),
      on('DELETE', '/api/farm/farms/f1/barns/B-1', { status: 204 }),
    )
    const user = renderFarms()
    const remove = await screen.findByRole('button', { name: text.farms.deleteBarnLabel('B-1') })
    const deletes = () => calls(fetch).filter(c => c.method === 'DELETE')

    await user.click(remove)
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: text.common.cancel }))
    expect(deletes()).toHaveLength(0)

    await user.click(remove)
    const confirmLabel = text.farms.confirmDeleteBarn('B-1', 'Chuồng một', 'Green Valley').confirmLabel
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: confirmLabel }))
    await waitFor(() => expect(deletes()).toEqual([{ method: 'DELETE', url: '/api/farm/farms/f1/barns/B-1' }]))
  })
})
