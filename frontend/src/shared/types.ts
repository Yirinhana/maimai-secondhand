/**
 * API v1 契约 DTO 类型（docs/design/api-v1.md）。
 * 金额一律整数分（*Cents），时间为 ISO-8601 UTC 字符串。
 */

/* ---------- 通用 ---------- */

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  page: number
  size: number
}

/** 售后/后台模块分页结果（PageResult：{content,total,page,size}） */
export interface TotalPage<T> {
  content: T[]
  total: number
  page: number
  size: number
}

/* ---------- 认证与账号（identity） ---------- */

export type SellerStatus =
  'NONE' | 'PENDING' | 'SUPPLEMENT' | 'APPROVED' | 'REJECTED' | 'SUSPENDED'

export interface User {
  id: number
  email: string
  nickname: string
  avatarUrl?: string | null
  roles: string[]
  sellerStatus: SellerStatus
}

/** GET /auth/me 与登录响应同构 */
export type Me = User

export interface RegisterCodeRequest {
  email: string
}

export interface RegisterRequest {
  email: string
  code: string
  password: string
  nickname: string
  acceptedTerms: boolean
  policyVersion: string
}

export interface LoginRequest {
  email: string
  password: string
}

export interface PasswordResetRequest {
  email: string
  code: string
  newPassword: string
}

export interface Address {
  id: number
  receiver: string
  phone: string
  region: string
  detail: string
  isDefault: boolean
  createdAt: string
}

export interface AddressRequest {
  receiver: string
  phone: string
  region: string
  detail: string
  isDefault: boolean
}

export type ChannelStatus =
  'NONE' | 'PENDING' | 'QUALIFIED' | 'REJECTED' | 'UNQUALIFIED'

export interface SellerApplication {
  id: number
  status: SellerStatus
  channelStatus: ChannelStatus
  intro: string | null
  reason: string | null
  createdAt: string
}

export interface Notification {
  id: number
  type: string
  title: string
  content: string
  read: boolean
  createdAt: string
}

/* ---------- 商品（catalog） ---------- */

export interface Category {
  id: number
  name: string
  children: Category[]
}

export type Condition = 'NEW' | 'LIKE_NEW' | 'GOOD' | 'FAIR' | 'POOR'

export type DeliveryMethod = 'EXPRESS' | 'MEETUP'

export type ProductStatus =
  | 'DRAFT'
  | 'PENDING_REVIEW'
  | 'ON_SALE'
  | 'CHANGES_REVIEW'
  | 'REJECTED'
  | 'OFF_SHELF'

export type ProductSort =
  'time_desc' | 'price_asc' | 'price_desc' | 'distance_asc'

export interface ProductSummary {
  id: number
  title: string
  priceCents: number
  condition: Condition
  region: string
  coverImage: string | null
  stockAvailable: number
  sellerId: number
  sellerNickname: string
  freightCents: number
  deliveryMethods: DeliveryMethod[]
  distanceMeters: number | null
}

export interface ProductSeller {
  avatarUrl?: string | null
  id: number
  nickname: string
}

export interface ProductImage {
  id: number
  path: string
  sort: number
}

export interface ProductDetail {
  experienceSource?: string | null
  supplyNote?: string | null
  id: number
  categoryId?: number
  latitude: number | null
  longitude: number | null
  shippingProvinces: string[]
  title: string
  description: string
  condition: Condition
  defects: string | null
  priceCents: number
  stockAvailable: number
  region: string
  deliveryMethods: DeliveryMethod[]
  freightCents: number
  returnPromise: string | null
  status: ProductStatus
  images: ProductImage[]
  seller: ProductSeller
  createdAt: string
}

export interface SellerProfile {
  avatarUrl?: string | null
  id: number
  nickname: string
  joinedAt: string
  onSaleCount: number
  sellerApproved: boolean
}

/** GET /seller/products 列表项：含全部状态与审核原因 */
export interface SellerProductItem {
  id: number
  title: string
  priceCents: number
  condition: Condition
  region: string
  coverImage: string | null
  stockAvailable: number
  stockReserved: number
  stockSold: number
  status: ProductStatus
  reviewReason: string | null
  freightCents: number
  deliveryMethods: DeliveryMethod[]
  createdAt: string
  updatedAt: string
}

export interface ProductUpsertRequest {
  title: string
  categoryId: number
  description: string
  condition: Condition
  defects: string | null
  priceCents: number
  stock: number
  region: string
  deliveryMethods: DeliveryMethod[]
  freightCents: number
  returnPromise: string | null
  submit: boolean
  latitude: number | null
  longitude: number | null
  shippingProvinces: string[]
}

/** PUT /seller/products/{id} 修改：不含 submit，库存调整走 /stock 端点 */
export interface ProductUpdateRequest {
  title: string
  categoryId: number
  description: string
  condition: Condition
  defects: string | null
  priceCents: number
  region: string
  deliveryMethods: DeliveryMethod[]
  freightCents: number
  returnPromise: string | null
  latitude: number | null
  longitude: number | null
  shippingProvinces: string[]
}

/* ---------- 交易（trade） ---------- */

export type BargainStatus =
  'PENDING' | 'COUNTERED' | 'CONFIRMED' | 'REJECTED' | 'EXPIRED' | 'USED'

/** 对应后端 TradeDtos.BargainDto（无 rejectReason 落库字段） */
export interface Bargain {
  id: number
  productId: number
  productTitle: string | null
  coverImage: string | null
  buyerId: number
  buyerNickname: string | null
  sellerId: number | null
  sellerNickname: string | null
  quantity: number
  offerPriceCents: number
  counterPriceCents: number | null
  status: BargainStatus
  expiresAt: string
  usedOrderId: number | null
  createdAt: string
}

/** 对应后端 TradeDtos.CartItemDto；invalid=true 表示商品已失效不可结算；商品被删时部分字段为 null */
export interface CartItem {
  id: number
  productId: number
  title: string | null
  coverImage: string | null
  priceCents: number
  quantity: number
  deliveryMethod: DeliveryMethod
  stockAvailable: number | null
  sellerId: number | null
  sellerNickname: string | null
  productStatus: ProductStatus | null
  invalid: boolean
}

export interface CheckoutItem {
  productId: number
  quantity: number
  deliveryMethod: DeliveryMethod
  bargainId?: number
}

export interface CheckoutRequest {
  idempotencyKey: string
  items: CheckoutItem[]
  addressId?: number
  meetupLocation?: string
  meetupTime?: string
  /** 结算成功后从购物车删除的条目 id（仅本人条目生效） */
  removeCartItemIds?: number[]
}

export interface CheckoutResponse {
  batchNo: string
  orders: OrderDto[]
}

export interface OrderItem {
  productId: number
  title: string
  condition: Condition
  defects: string | null
  priceCents: number
  quantity: number
  imagePath: string | null
}

export type FulfillmentStatus =
  | 'PENDING_PAYMENT'
  | 'PAID_PENDING_SHIP'
  | 'SHIPPED'
  | 'AWAITING_MEETUP'
  | 'COMPLETED'
  | 'CLOSED'

export type PayStatus = 'UNPAID' | 'PAYING' | 'PAID' | 'CLOSED'

export type RefundStatus = 'NONE' | 'PARTIAL' | 'FULL'

export type SettleStatus = 'NONE' | 'PENDING' | 'SETTLED'

export interface OrderDto {
  experienceSource?: string | null
  id: number
  orderNo: string
  buyerId: number
  sellerId: number
  sellerNickname: string
  deliveryMethod: DeliveryMethod
  items: OrderItem[]
  goodsAmountCents: number
  freightCents: number
  platformFeeCents: number
  totalCents: number
  retainedPlatformFeeCents: number
  channelFeeCents: number | null
  channelFeeConfirmed: boolean
  expectedSellerNetCents: number | null
  simulated: boolean
  allocationStatus: 'NOT_RECORDED' | 'WAITING_CHANNEL'
  fulfillmentStatus: FulfillmentStatus
  payStatus: PayStatus
  refundStatus: RefundStatus
  settleStatus: SettleStatus
  receiver?: string
  phone?: string
  region?: string
  addressDetail?: string
  meetupLocation?: string
  meetupTime?: string
  expiresAt: string
  paidAt: string | null
  shippedAt: string | null
  autoConfirmAt: string | null
  createdAt: string
}

export type ShipmentStatus =
  'IN_TRANSIT' | 'DELIVERED' | 'EXCEPTION' | 'UNKNOWN'

/** 对应后端 TradeDtos.ShipmentDto：traces 为轨迹文本（本地环境为模拟轨迹） */
export interface Shipment {
  carrier: string
  trackingNo: string
  status: ShipmentStatus
  traces: string | null
  lastTraceAt: string | null
  queryErrorCode?: string | null
  lastQueryAttemptAt?: string | null
}

/** 对应后端 TradeDtos.DeliveryCodeResponse：明文码仅生成当次返回 */
export interface DeliveryCodeResult {
  code: string
  expiresAt: string
}

/* ---------- 支付（payment） ---------- */

export interface Payment {
  payNo: string
  channel: string
  amountCents: number
  simulated: boolean
  message: string
}

export interface PaymentStatus {
  payNo: string
  status: string
  simulated: boolean
  paidAt: string | null
}

/** 对应后端 PaymentDtos.MockPayResultResponse（仅 local profile 可用） */
export interface MockPayResult {
  payNo: string
  status: string
  simulated: boolean
  message: string
}

/* ---------- 售后（aftersales） ---------- */

export type AftersaleType = 'REFUND_ONLY' | 'RETURN_REFUND'

export type AftersaleStatus =
  | 'PENDING_SELLER'
  | 'SELLER_REJECTED'
  | 'PENDING_RETURN'
  | 'RETURN_SHIPPED'
  | 'PENDING_MANUAL'
  | 'RESOLVED'
  | 'CLOSED'

/** 售后单概要（列表项） */
export interface AftersaleSummary {
  id: number
  aftersaleNo: string
  orderId: number
  orderNo: string
  type: AftersaleType
  goodsAmountCents: number
  freightAmountCents: number
  status: AftersaleStatus
  sellerDeadline: string | null
  returnDeadline: string | null
  createdAt: string
}

/** 售后处理日志项 */
export interface AftersaleLogItem {
  id: number
  actorId: number
  actorRole: string
  action: string
  note: string | null
  createdAt: string
}

/** 售后单详情（含处理日志） */
export interface AftersaleDetail {
  experience?: boolean
  id: number
  aftersaleNo: string
  orderId: number
  orderNo: string
  buyerId: number
  sellerId: number
  type: AftersaleType
  reason: string
  goodsAmountCents: number
  freightAmountCents: number
  status: AftersaleStatus
  evidence: string | null
  sellerReply: string | null
  sellerDeadline: string | null
  returnDeadline: string | null
  returnCarrier: string | null
  returnTrackingNo: string | null
  returnRecipient: string | null
  returnPhone: string | null
  returnAddress: string | null
  returnReceivedAt: string | null
  returnInspectionDeadline: string | null
  returnShippedAt: string | null
  createdAt: string
  updatedAt: string
  logs: AftersaleLogItem[]
}

/** 兼容旧命名：详情即完整售后单 */
export type Aftersale = AftersaleDetail

export interface AftersaleRequest {
  type: AftersaleType
  reason: string
  goodsAmountCents: number
  freightAmountCents: number
  evidence: string | null
}

/* ---------- 后台（admin） ---------- */

export interface AdminSellerApplication {
  id: number
  userId: number
  email: string
  nickname: string
  status: SellerStatus
  channelStatus: ChannelStatus
  intro: string | null
  reason: string | null
  reviewedBy: number | null
  reviewedAt: string | null
  createdAt: string
}

/** 卖家申请审核动作 */
export type ReviewAction = 'APPROVE' | 'REJECT' | 'SUPPLEMENT' | 'SUSPEND'

export interface ReviewApplicationRequest {
  action: ReviewAction
  reason: string
  channelQualified?: boolean
}

/** 后台商品审核列表项 */
export interface AdminProductItem {
  id: number
  title: string
  sellerId: number
  sellerNickname: string
  categoryId: number
  priceCents: number
  condition: Condition
  region: string
  status: ProductStatus
  reviewReason: string | null
  coverImage: string | null
  deliveryMethods: DeliveryMethod[]
  createdAt: string
  updatedAt: string
}

/** 后台售后列表项 */
export interface AdminAftersaleItem {
  id: number
  aftersaleNo: string
  orderId: number
  orderNo: string
  buyerId: number
  type: AftersaleType
  reason: string
  goodsAmountCents: number
  freightAmountCents: number
  status: AftersaleStatus
  createdAt: string
}

/** 人工售后处理请求：action ∈ REFUND|REJECT，金额缺省用售后申请值 */
export interface ResolveAftersaleRequest {
  action: 'REFUND' | 'REJECT'
  note: string
  goodsAmountCents?: number
  freightAmountCents?: number
}

export interface AdminStatsOverview {
  userCount: number
  productOnSaleCount: number
  orderCount: number
  paidOrderCount: number
  refundSuccessCount: number
  platformFeeSumCents: number
}

export interface AdminUser {
  id: number
  email: string
  nickname: string
  status: string
  roles: string[]
  createdAt: string
}

export interface AuditLog {
  id: number
  adminId: number
  action: string
  targetType: string
  targetId: number
  reason: string | null
  beforeState: string | null
  afterState: string | null
  createdAt: string
}

/* ---------- 私信（messaging） ---------- */

/** GET /messages/conversations 列表项 */
export interface ConversationSummary {
  id: number
  otherUserId: number
  otherNickname: string
  otherAvatarUrl: string | null
  productTitle: string | null
  productId: number | null
  lastMessage: string | null
  updatedAt: string
  unread: number
}

/** 消息 DTO：GET 会话消息 items 与发送响应同构 */
export interface MessageItem {
  product?: {id:number;title:string;coverImage:string|null;priceCents:number} | null
  id: number
  conversationId: number
  senderId: number
  clientId: string
  body: string | null
  /** 服务端返回的私有附件地址（同源 /api/v1/messages/attachments/{id}），不拼 /uploads */
  attachmentUrl: string | null
  createdAt: string
}

/** GET /messages/conversations/{id} 分页（items 按 id 倒序） */
export interface MessagePage {
  items: MessageItem[]
  hasMore: boolean
  nextBeforeId: number | null
}

export type SendMessageResponse = MessageItem

/** POST /messages/conversations/{id}/images 上传响应 */
export interface ImageUploadResponse {
  id: string
  url: string
  byteSize: number
}

export interface CreateConversationRequest {
  recipientId: number
  productId?: number
}

export interface CreateConversationResponse {
  id: number
}

/** WebSocket /messages/socket 下行事件；上行仅允许字符串 ping */
export type MessageSocketEvent =
  | { type: 'ready' }
  | { type: 'pong' }
  | { type: 'messages.changed'; conversationId: number }

/* ---------- 状态中文文案 ---------- */

export const CONDITION_TEXT: Record<Condition, string> = {
  NEW: '全新',
  LIKE_NEW: '几乎全新',
  GOOD: '成色良好',
  FAIR: '有使用痕迹',
  POOR: '成色较差',
}

export const PRODUCT_STATUS_TEXT: Record<ProductStatus, string> = {
  DRAFT: '草稿',
  PENDING_REVIEW: '审核中',
  ON_SALE: '在售',
  CHANGES_REVIEW: '变更审核中',
  REJECTED: '已驳回',
  OFF_SHELF: '已下架',
}

export const BARGAIN_STATUS_TEXT: Record<BargainStatus, string> = {
  PENDING: '待卖家回应',
  COUNTERED: '卖家已还价',
  CONFIRMED: '已确认',
  REJECTED: '已拒绝',
  EXPIRED: '已过期',
  USED: '已使用',
}

export const FULFILLMENT_STATUS_TEXT: Record<FulfillmentStatus, string> = {
  PENDING_PAYMENT: '待付款',
  PAID_PENDING_SHIP: '待发货',
  SHIPPED: '已发货',
  AWAITING_MEETUP: '待面交',
  COMPLETED: '已完成',
  CLOSED: '已关闭',
}

export const AFTERSALE_TYPE_TEXT: Record<AftersaleType, string> = {
  REFUND_ONLY: '仅退款',
  RETURN_REFUND: '退货退款',
}

export const AFTERSALE_STATUS_TEXT: Record<AftersaleStatus, string> = {
  PENDING_SELLER: '待卖家处理',
  SELLER_REJECTED: '卖家已拒绝',
  PENDING_RETURN: '待寄回',
  RETURN_SHIPPED: '退货已发出',
  PENDING_MANUAL: '待平台仲裁',
  RESOLVED: '已解决',
  CLOSED: '已关闭',
}

export const DELIVERY_METHOD_TEXT: Record<DeliveryMethod, string> = {
  EXPRESS: '快递',
  MEETUP: '面交',
}

export const SELLER_STATUS_TEXT: Record<SellerStatus, string> = {
  NONE: '未申请',
  PENDING: '审核中',
  SUPPLEMENT: '待补充材料',
  APPROVED: '已通过',
  REJECTED: '已拒绝',
  SUSPENDED: '已暂停',
}

export const CHANNEL_STATUS_TEXT: Record<ChannelStatus, string> = {
  UNQUALIFIED: '尚未具备收款资格',
  NONE: '未开通',
  PENDING: '开通中',
  QUALIFIED: '已具备收款资格',
  REJECTED: '未通过',
}

export const SHIPMENT_STATUS_TEXT: Record<ShipmentStatus, string> = {
  IN_TRANSIT: '运输中',
  DELIVERED: '已签收',
  EXCEPTION: '物流异常',
  UNKNOWN: '状态未知',
}
