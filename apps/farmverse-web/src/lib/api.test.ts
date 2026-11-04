import { describe, expect, it, vi } from 'vitest'
import { api, ApiError, farm, identity, setSessionLostHandler } from './api'
import { mockFetch, on } from '../test/http'

const headersOf = (fn: ReturnType<typeof mockFetch>, i = 0) =>
  (fn.mock.calls[i][1] as RequestInit).headers as Record<string, string>

describe('api()', () => {
  it('GET: gửi cookie cùng origin, không gửi CSRF và không có body', async () => {
    document.cookie = 'XSRF-TOKEN=abc'
    const fetch = mockFetch(on('GET', '/api/farm/farms', { body: [{ id: '1' }] }))

    await expect(api(farm('/farms'))).resolves.toEqual([{ id: '1' }])

    const init = fetch.mock.calls[0][1] as RequestInit
    expect(init.credentials).toBe('same-origin')
    expect(init.body).toBeUndefined()
    expect(headersOf(fetch)).toEqual({ Accept: 'application/json' })
  })

  it('POST: gửi JSON và header X-XSRF-TOKEN lấy từ cookie', async () => {
    document.cookie = 'XSRF-TOKEN=' + encodeURIComponent('tok/en=')
    const fetch = mockFetch(on('POST', '/api/farm/farms', { status: 201, body: { id: '1' } }))

    await api(farm('/farms'), { method: 'POST', body: { code: 'a' } })

    expect(headersOf(fetch)).toMatchObject({ 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'tok/en=' })
    expect((fetch.mock.calls[0][1] as RequestInit).body).toBe('{"code":"a"}')
  })

  it('idempotent: mỗi lần gọi có Idempotency-Key mới', async () => {
    const fetch = mockFetch(on('POST', '/api/farm/x', { status: 204 }))

    await api(farm('/x'), { method: 'POST', idempotent: true })
    await api(farm('/x'), { method: 'POST', idempotent: true })

    const first = headersOf(fetch, 0)['Idempotency-Key']
    expect(first).toBeTruthy()
    expect(headersOf(fetch, 1)['Idempotency-Key']).not.toBe(first)
  })

  it('query: bỏ giá trị rỗng, null, undefined', async () => {
    const fetch = mockFetch(on('GET', '/api/identity/users', { body: [] }))

    await api(identity('/users'), { query: { page: 0, q: '', role: null, size: 20, x: undefined } })

    expect(String(fetch.mock.calls[0][0])).toBe('/api/identity/users?page=0&size=20')
  })

  it('204: trả về undefined', async () => {
    mockFetch(on('DELETE', '/api/farm/x', { status: 204 }))
    await expect(api(farm('/x'), { method: 'DELETE' })).resolves.toBeUndefined()
  })

  it('lỗi: ném ApiError mang status, code và detail của ProblemDetail', async () => {
    mockFetch(on('POST', '/api/farm/farms', { status: 409, body: { status: 409, code: 'SF-FARM-002', detail: 'trùng mã' } }))

    const error = await api(farm('/farms'), { method: 'POST', body: {} }).catch(e => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 409, code: 'SF-FARM-002', message: 'trùng mã' })
  })

  it('401: báo mất phiên cho AuthProvider', async () => {
    const lost = vi.fn()
    setSessionLostHandler(lost)
    mockFetch(on('GET', '/bff/me', { status: 401, body: { status: 401, code: 'SF-AUTH-001' } }))

    await expect(api('/bff/me')).rejects.toBeInstanceOf(ApiError)
    expect(lost).toHaveBeenCalledOnce()
  })
})
