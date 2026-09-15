export const EXPERIENCE_ORDER_SOURCE = 'maimai-experience-checkout-v1';
export const EXPERIENCE_PRODUCT_SOURCE = 'maimai-experience-045';
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
