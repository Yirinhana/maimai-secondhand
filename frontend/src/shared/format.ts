/** 金额（分）格式化为人民币字符串，如 1234 → "¥12.34" */
export function formatPrice(cents: number): string {
  const yuan = cents / 100
  return '¥' + yuan.toFixed(2)
}

/** ISO 时间格式化为本地中文时间，如 "2026年9月13日 14:30" */
export function formatTime(iso: string | null | undefined): string {
  if (!iso) return '—'
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return '—'
  const pad = (n: number) => String(n).padStart(2, '0')
  return (
    `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日 ` +
    `${pad(date.getHours())}:${pad(date.getMinutes())}`
  )
}
