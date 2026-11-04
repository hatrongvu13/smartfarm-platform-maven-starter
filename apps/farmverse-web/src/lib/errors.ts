import { ApiError } from './api'

/**
 * Đổi lỗi thành nội dung hiển thị cho người dùng. Một chỗ duy nhất, để mọi trang nói cùng một kiểu.
 *
 * Nguồn: ProblemDetail (RFC 9457) của gateway và các service, có `status`, `detail`, `code` dạng SF-<MIEN>-<SO>
 * và với lỗi 400 thì `errors: [{field, message}]`.
 *  - Lỗi nghiệp vụ (SF-FARM-*, SF-IDENTITY-*, ...): dùng `detail` của server, server đã viết bằng tiếng Việt.
 *  - Lỗi hạ tầng / xác thực (401, 403, 429, 5xx, mất mạng): câu cố định ở đây, không hiện thông điệp kỹ thuật.
 */
export type ErrorView = {
  title: string
  detail?: string
  fields: { field: string; message: string }[]
  /** Mã để báo hỗ trợ, ví dụ SF-FARM-010. Không có với lỗi mạng. */
  code?: string
  /** Thử lại cùng thao tác có thể thành công (lỗi tạm thời). */
  retryable: boolean
  kind: 'network' | 'validation' | 'auth' | 'forbidden' | 'not-found' | 'conflict' | 'rule' | 'rate-limit' | 'unavailable' | 'server'
}

const BUSINESS = /^SF-(?!AUTH-|GATEWAY-|HTTP-|VALIDATION-)[A-Z]+-\d+$/

export function describeError(error: unknown): ErrorView {
  if (!(error instanceof ApiError)) {
    // fetch() ném TypeError khi không tới được gateway (mất mạng, gateway tắt)
    return { kind: 'network', title: 'Không kết nối được máy chủ', detail: 'Kiểm tra kết nối mạng rồi thử lại.', fields: [], retryable: true }
  }
  const body = (error.body ?? {}) as { detail?: string; errors?: { field?: string; message?: string }[] }
  const code = error.code
  const serverDetail = typeof body.detail === 'string' && body.detail ? body.detail : undefined
  const base = { code, fields: [] as ErrorView['fields'] }

  if (code === 'SF-VALIDATION-001' || error.status === 400) {
    const fields = (body.errors ?? [])
      .filter(e => e && e.message)
      .map(e => ({ field: e.field && e.field !== '-' ? e.field : '', message: String(e.message) }))
    return { ...base, kind: 'validation', title: 'Dữ liệu chưa hợp lệ', detail: fields.length ? undefined : serverDetail, fields, retryable: false }
  }
  if (error.status === 401) {
    return { ...base, kind: 'auth', title: 'Phiên đăng nhập đã hết', detail: 'Hãy đăng nhập lại để tiếp tục.', retryable: false }
  }
  if (error.status === 403) {
    return { ...base, kind: 'forbidden', title: 'Bạn không có quyền làm thao tác này', detail: 'Quyền có thể vừa được thay đổi. Liên hệ quản trị viên nếu bạn cần quyền này.', retryable: false }
  }
  if (error.status === 404) {
    return { ...base, kind: 'not-found', title: 'Không tìm thấy', detail: serverDetail, retryable: false }
  }
  if (error.status === 409) {
    return { ...base, kind: 'conflict', title: 'Dữ liệu vừa thay đổi', detail: serverDetail ?? 'Hãy tải lại rồi thử lại.', retryable: true }
  }
  if (error.status === 422 || (code && BUSINESS.test(code) && error.status < 500)) {
    return { ...base, kind: 'rule', title: serverDetail ?? 'Không thực hiện được thao tác', retryable: false }
  }
  if (error.status === 429) {
    return { ...base, kind: 'rate-limit', title: 'Bạn thao tác quá nhanh', detail: 'Đợi vài giây rồi thử lại.', retryable: true }
  }
  if (error.status === 502 || error.status === 503 || error.status === 504) {
    return { ...base, kind: 'unavailable', title: 'Hệ thống tạm thời không phản hồi', detail: 'Thường tự hết sau ít phút. Hãy thử lại.', retryable: true }
  }
  return { ...base, kind: 'server', title: 'Đã có lỗi phía máy chủ', detail: 'Dữ liệu của bạn chưa bị thay đổi. Hãy thử lại sau.', retryable: error.status >= 500 }
}
