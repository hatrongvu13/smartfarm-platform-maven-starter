import { describe, expect, it } from 'vitest'
import { ApiError } from './api'
import { describeError } from './errors'
import { vi as text } from '../i18n/vi'

const problem = (status: number, extra: Record<string, unknown> = {}) =>
  new ApiError(status, 'x', { status, ...extra })

describe('describeError()', () => {
  it('lỗi mạng (fetch ném TypeError): cho thử lại, không có mã', () => {
    const view = describeError(new TypeError('Failed to fetch'))
    expect(view).toMatchObject({ kind: 'network', title: text.errors.network.title, retryable: true })
    expect(view.code).toBeUndefined()
  })

  it('400: lỗi theo từng ô, bỏ tên ô "-"', () => {
    const view = describeError(problem(400, {
      code: 'SF-VALIDATION-001',
      errors: [{ field: 'code', message: 'sai định dạng' }, { field: '-', message: 'thiếu dữ liệu' }, { field: 'x' }],
    }))
    expect(view.kind).toBe('validation')
    expect(view.fields).toEqual([{ field: 'code', message: 'sai định dạng' }, { field: '', message: 'thiếu dữ liệu' }])
    expect(view.detail).toBeUndefined()
  })

  it('401 / 403: câu cố định, không lộ thông điệp kỹ thuật của server', () => {
    expect(describeError(problem(401, { detail: 'JWT expired' }))).toMatchObject({ kind: 'auth', detail: text.errors.auth.detail })
    expect(describeError(problem(403, { detail: 'insufficient_scope' }))).toMatchObject({ kind: 'forbidden', detail: text.errors.forbidden.detail })
  })

  it('409: dùng detail của server và cho thử lại', () => {
    expect(describeError(problem(409, { code: 'SF-FARM-002', detail: 'Mã đã tồn tại' })))
      .toMatchObject({ kind: 'conflict', detail: 'Mã đã tồn tại', code: 'SF-FARM-002', retryable: true })
  })

  it('lỗi nghiệp vụ SF-FARM-*: detail của server thành tiêu đề', () => {
    expect(describeError(problem(422, { code: 'SF-FARM-010', detail: 'Trang trại đã ngừng hoạt động' })))
      .toMatchObject({ kind: 'rule', title: 'Trang trại đã ngừng hoạt động', retryable: false })
  })

  it('429 và 503: tạm thời, cho thử lại', () => {
    expect(describeError(problem(429, { code: 'SF-GATEWAY-429' }))).toMatchObject({ kind: 'rate-limit', retryable: true })
    expect(describeError(problem(503, { code: 'SF-GATEWAY-503' }))).toMatchObject({ kind: 'unavailable', retryable: true })
  })

  it('500: câu chung, vẫn giữ mã để báo hỗ trợ', () => {
    expect(describeError(problem(500, { code: 'SF-HTTP-500', detail: 'NullPointerException' })))
      .toMatchObject({ kind: 'server', title: text.errors.server.title, code: 'SF-HTTP-500', retryable: true })
  })
})
