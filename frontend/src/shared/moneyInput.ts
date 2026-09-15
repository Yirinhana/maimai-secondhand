/** 接受原生 number 输入的数字或空字符串，金额最多两位小数，拒绝静默舍入。 */
export function parseYuan(value: unknown): number | null {
  const text = String(value ?? '').trim();
  if (!/^\d+(?:\.\d{1,2})?$/.test(text)) return null;
  const [whole, fraction = ''] = text.split('.');
  const cents = Number(whole) * 100 + Number(fraction.padEnd(2, '0'));
  return Number.isSafeInteger(cents) ? cents : null;
}
