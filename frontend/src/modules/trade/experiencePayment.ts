export const EXPERIENCE_ORDER_SOURCE = 'maimai-experience-checkout-v1';
export const EXPERIENCE_PRODUCT_SOURCE = 'maimai-experience-045';
export const EXPERIENCE_CATALOG_SOURCE = 'maimai-experience-catalog-021';
export const SELLER_SIMULATION_SOURCE = 'maimai-seller-simulation-v1';
const EXPERIENCE_PRODUCT_SOURCES = new Set([
  EXPERIENCE_PRODUCT_SOURCE,
  EXPERIENCE_CATALOG_SOURCE,
  SELLER_SIMULATION_SOURCE,
]);
/** Keep this explicit allowlist aligned with the server; arbitrary provenance is not payment permission. */
export function isExperienceProduct(source: string | null | undefined): boolean {
  return typeof source === 'string' && EXPERIENCE_PRODUCT_SOURCES.has(source);
}
export function experienceProductNotice(source: string | null | undefined): string {
  if (source === SELLER_SIMULATION_SOURCE)
    return '商品资料与照片由卖家提供；当前仅供模拟交易，不发生扣款或实际发货。';
  if (source === EXPERIENCE_PRODUCT_SOURCE || source === EXPERIENCE_CATALOG_SOURCE)
    return '体验账号供给资料 · 库存为模拟数量，图片为生成素材。';
  return source ? '该商品保留体验来源信息，目前暂未开放结算。' : '';
}
export interface ExperiencePayment {
  token: string;
  payNo: string;
  orderNo: string;
  amountCents: number;
  status:
    | 'CREATED'
    | 'PAID'
    | 'FAILED'
    | 'CANCELLED'
    | 'EXPIRED'
    | 'CLOSED'
    | 'REFUNDED';
  refundStatus: string;
  expiresAt: string;
  checkoutPath: string;
  qrPath: string;
  items: { title: string; quantity: number; priceCents: number }[];
}
export const paymentStateText: Record<ExperiencePayment['status'], string> = {
  CREATED: '等待体验付款',
  PAID: '体验付款成功',
  FAILED: '本次付款未成功',
  CANCELLED: '已取消本次付款',
  EXPIRED: '付款时间已结束',
  CLOSED: '订单已关闭',
  REFUNDED: '体验退款已完成',
};
export function remainingPaymentTime(expiresAt: string, now: number): string {
  const seconds = Math.max(0, Math.ceil((Date.parse(expiresAt) - now) / 1000));
  return `${Math.floor(seconds / 60)
    .toString()
    .padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}`;
}
