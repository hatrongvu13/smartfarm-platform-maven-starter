// Nhớ id vừa tạo (itemId, warehouseId...) để luồng inventory -> order không phải copy tay.
const k = (n: string) => `sf.recent.${n}`
export const recent = {
  get(name: string, fallback = '') { try { return localStorage.getItem(k(name)) ?? fallback } catch { return fallback } },
  set(name: string, v: string) { try { localStorage.setItem(k(name), v) } catch { /* ignore */ } },
}
