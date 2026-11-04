import { describe, expect, it } from 'vitest'
import { act, render, screen } from '@testing-library/react'
import { AuthProvider, hasPermission, useAuth, type Me } from './auth'
import { api } from './api'
import { mockFetch, on } from '../test/http'

const ME: Me = { userId: 'u1', name: 'An', tenantCode: 'demo', roles: ['FARM_MANAGER'], permissions: ['farm:farm:read'] }

function Probe() {
  const { status, me } = useAuth()
  return <p data-testid="auth">{status}:{me?.name ?? '-'}</p>
}

describe('hasPermission()', () => {
  it('đúng khi có quyền, sai khi chưa đăng nhập hoặc thiếu quyền', () => {
    expect(hasPermission(ME, 'farm:farm:read')).toBe(true)
    expect(hasPermission(ME, 'farm:farm:write')).toBe(false)
    expect(hasPermission(null, 'farm:farm:read')).toBe(false)
  })

  it('SUPER_ADMIN qua mọi kiểm tra phía giao diện', () => {
    expect(hasPermission({ ...ME, roles: ['SUPER_ADMIN'], permissions: [] }, 'identity:user:write')).toBe(true)
  })
})

describe('AuthProvider', () => {
  it('đang tải, rồi đã đăng nhập khi /bff/me trả 200', async () => {
    mockFetch(on('GET', '/bff/me', { body: ME }))
    render(<AuthProvider><Probe /></AuthProvider>)

    expect(screen.getByTestId('auth')).toHaveTextContent('loading:-')
    expect(await screen.findByText('authenticated:An')).toBeInTheDocument()
  })

  it('chưa đăng nhập khi /bff/me trả 401', async () => {
    mockFetch(on('GET', '/bff/me', { status: 401, body: { status: 401, code: 'SF-AUTH-001' } }))
    render(<AuthProvider><Probe /></AuthProvider>)

    expect(await screen.findByText('anonymous:-')).toBeInTheDocument()
  })

  it('một request bất kỳ bị 401 thì chuyển về chưa đăng nhập', async () => {
    mockFetch(on('GET', '/bff/me', { body: ME }), on('GET', '/api/farm/farms', { status: 401 }))
    render(<AuthProvider><Probe /></AuthProvider>)
    await screen.findByText('authenticated:An')

    await act(() => api('/api/farm/farms').catch(() => undefined))

    expect(screen.getByTestId('auth')).toHaveTextContent('anonymous:-')
  })
})
