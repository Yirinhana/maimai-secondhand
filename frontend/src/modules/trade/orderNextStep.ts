import type { OrderDto } from "../../shared/types";
export function orderNextStep(
  order: Pick<OrderDto, "fulfillmentStatus" | "refundStatus">,
  role: "buyer" | "seller",
): string {
  if (order.refundStatus === "FULL") return "退款已完成，查看退款记录";
  switch (order.fulfillmentStatus) {
    case "PENDING_PAYMENT":
      return role === "buyer"
        ? "核对金额，进入收银台"
        : "等待买家付款，暂勿发货";
    case "PAID_PENDING_SHIP":
      return role === "seller"
        ? "安排发货并填写运单"
        : "等待卖家发货，可联系卖家";
    case "SHIPPED":
      return role === "buyer"
        ? "查看物流，拿到商品后确认收货"
        : "跟进物流与买家收货";
    case "AWAITING_MEETUP":
      return role === "buyer"
        ? "核对面交安排，交付时出示交付码"
        : "核对面交安排，交付时核验买家交付码";
    case "COMPLETED":
      return "查看评价、售后与交易记录";
    case "CLOSED":
      return "查看关闭原因与交易记录";
    default:
      return "查看进度与可用操作";
  }
}
