import { vi } from 'vitest'

type Reply = { status?: number; body?: unknown }
type Route = (url: string, init: RequestInit) => Reply | undefined

/**
 * Thay fetch bằng một hàm trả lời theo route. Route đầu tiên trả về khác undefined sẽ thắng;
 * không route nào khớp thì trả 404 để test hỏng rõ ràng thay vì treo.
 */
export function mockFetch(...routes: Route[]) {
  const fn = vi.fn(async (input: RequestInfo | URL, init: RequestInit = {}) => {
    const url = String(input)
    for (const route of routes) {
      const reply = route(url, init)
      if (reply) return jsonResponse(reply.status ?? 200, reply.body)
    }
    return jsonResponse(404, { status: 404, code: 'SF-HTTP-404', detail: `no mock for ${init.method ?? 'GET'} ${url}` })
  })
  vi.stubGlobal('fetch', fn)
  return fn
}

export function jsonResponse(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: body === undefined ? {} : { 'Content-Type': 'application/json' },
  })
}

/** Khớp theo method + đường dẫn (bỏ query). */
export const on = (method: string, path: string, reply: Reply | ((init: RequestInit) => Reply)): Route =>
  (url, init) => {
    if ((init.method ?? 'GET') !== method || url.split('?')[0] !== path) return undefined
    return typeof reply === 'function' ? reply(init) : reply
  }

/** Các request đã gửi, dạng gọn để so sánh. */
export const calls = (fn: ReturnType<typeof mockFetch>) =>
  fn.mock.calls.map(([input, init]) => ({ method: (init as RequestInit | undefined)?.method ?? 'GET', url: String(input) }))
