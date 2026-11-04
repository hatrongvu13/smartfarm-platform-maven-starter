// W5: mọi chữ hiển thị cho người dùng nằm ở src/i18n/vi.ts. Kịch bản này tìm chữ tiếng Việt (có dấu) còn sót
// trong mã nguồn ngoài file đó, sau khi bỏ comment. Chạy: npm run check:i18n (thoát mã 1 nếu còn sót).
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'

const ROOT = new URL('../src/', import.meta.url).pathname
const ALLOWED = new Set(['i18n/vi.ts'])
// Chữ cái tiếng Việt có dấu (không tính chữ Latin thường, để mã, tên biến, class CSS không bị bắt nhầm)
const VIETNAMESE = /[àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđ]/i

function files(dir) {
  return readdirSync(dir).flatMap(name => {
    const path = join(dir, name)
    return statSync(path).isDirectory() ? files(path) : /\.(tsx?|jsx?)$/.test(name) ? [path] : []
  })
}

// Bỏ comment /* */, {/* */} và //, giữ nguyên số dòng để báo đúng vị trí
const stripComments = source => source
  .replace(/\/\*[\s\S]*?\*\//g, m => m.replace(/[^\n]/g, ' '))
  .replace(/(^|[^:"'`])\/\/.*$/gm, (m, p) => p + ' '.repeat(m.length - p.length))

let found = 0
for (const file of files(ROOT)) {
  const rel = relative(ROOT, file)
  if (ALLOWED.has(rel) || rel.startsWith('generated/')) continue
  stripComments(readFileSync(file, 'utf8')).split('\n').forEach((line, i) => {
    if (VIETNAMESE.test(line)) {
      found++
      console.log(`${rel}:${i + 1}: ${line.trim().slice(0, 110)}`)
    }
  })
}
if (found) {
  console.log(`\n${found} dòng còn chữ tiếng Việt ngoài src/i18n/vi.ts`)
  process.exit(1)
}
console.log('OK: không còn chữ hiển thị nằm rải rác ngoài src/i18n/vi.ts')
