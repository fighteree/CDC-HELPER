/**
 * 将 ISO 或时间戳格式化为 yyyy-MM-dd HH:mm:ss
 * @param {string|number|Date|null|undefined} val - 如 "2026-02-18T00:49:04" 或时间戳
 * @returns {string} 格式化后的字符串，无效则返回 '-'
 */
export function formatDateTime(val) {
  if (val == null || val === '') return '-'
  let date
  if (typeof val === 'number') {
    date = new Date(val)
  } else if (typeof val === 'string') {
    if (/^\d+$/.test(val)) date = new Date(Number(val))
    else date = new Date(val)
  } else if (val instanceof Date) {
    date = val
  } else {
    return '-'
  }
  if (Number.isNaN(date.getTime())) return '-'
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  const h = String(date.getHours()).padStart(2, '0')
  const min = String(date.getMinutes()).padStart(2, '0')
  const s = String(date.getSeconds()).padStart(2, '0')
  return `${y}-${m}-${d} ${h}:${min}:${s}`
}

/** 从行对象取创建时间并格式化，兼容 createdAt / created_at */
export function formatRowTime(row) {
  const val = row && (row.createdAt !== undefined ? row.createdAt : row.created_at)
  return formatDateTime(val)
}
