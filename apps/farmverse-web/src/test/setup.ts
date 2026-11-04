// Cài đặt chung cho Vitest (W6): matcher của jest-dom, dọn DOM sau mỗi test, và bù phần jsdom còn thiếu.
import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, vi } from 'vitest'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
  document.cookie.split('; ').filter(Boolean).forEach(c => {
    document.cookie = `${c.split('=')[0]}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`
  })
})

// jsdom chưa có showModal()/close() của <dialog>. Bản thay thế tối thiểu: đặt/bỏ thuộc tính open
// và phát sự kiện "close" như trình duyệt, để onClose của React chạy đúng.
if (typeof HTMLDialogElement !== 'undefined' && !HTMLDialogElement.prototype.showModal) {
  HTMLDialogElement.prototype.showModal = function showModal(this: HTMLDialogElement) {
    this.setAttribute('open', '')
    // Như trình duyệt: focus vào phần tử có autofocus, nếu không có thì phần tử bấm được đầu tiên
    const target = this.querySelector<HTMLElement>('[autofocus]') ?? this.querySelector<HTMLElement>('button, input, select, textarea, a[href]')
    target?.focus()
  }
  HTMLDialogElement.prototype.close = function close(this: HTMLDialogElement) {
    if (!this.hasAttribute('open')) return
    this.removeAttribute('open')
    this.dispatchEvent(new Event('close'))
  }
}
