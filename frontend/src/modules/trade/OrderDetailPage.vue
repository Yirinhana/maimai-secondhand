<template>
  <section class="mm-page mm-order-detail">
    <header class="mm-order-detail__header">
      <div>
        <RouterLink
          v-if="detailSection"
          :to="
            detailSection === 'seller'
              ? '/seller/orders'
              : detailSection === 'admin'
                ? '/admin'
                : '/orders'
          "
          class="mm-order-detail__back"
          >←
          {{
            detailSection === 'admin' ? '返回管理后台' : '订单列表'
          }}</RouterLink
        >
        <h1>订单详情</h1>
      </div>
      <MmButton
        v-if="order"
        variant="ghost"
        :loading="loading"
        :disabled="busy"
        @click="load"
        >刷新状态</MmButton
      >
    </header>
    <div
      v-if="error"
      ref="errorNotice"
      tabindex="-1"
      class="mm-order-detail__error"
      role="alert"
    >
      <strong>暂时未能完成</strong>
      <p>{{ error }}</p>
      <MmButton v-if="!order" variant="ghost" :disabled="loading" @click="load"
        >重新加载</MmButton
      >
    </div>
    <p v-if="feedback" class="mm-order-detail__feedback" role="status">
      {{ feedback }}
    </p>
    <MmSkeleton
      v-if="loading && !order"
      kind="detail"
      :count="1"
      label="正在读取订单详情"
    />
    <template v-if="order">
      <section
        class="mm-order-detail__status"
        aria-labelledby="order-status-title"
      >
        <div class="mm-order-detail__status-copy">
          <p class="mm-order-detail__eyebrow">
            {{ isBuyer ? '我买到的' : isSeller ? '我卖出的' : '订单查看' }} ·
            {{ DELIVERY_METHOD_TEXT[order.deliveryMethod] }}
          </p>
          <h2 id="order-status-title">
            {{ FULFILLMENT_STATUS_TEXT[order.fulfillmentStatus] }}
          </h2>
          <p class="mm-order-detail__next">{{ nextStep }}</p>
          <p
            v-if="order.fulfillmentStatus === 'PENDING_PAYMENT'"
            class="mm-order-detail__deadline"
          >
            付款截止 {{ formatTime(order.expiresAt) }} · 超时自动关闭并释放库存
          </p>
        </div>
        <div class="mm-order-detail__status-meta">
          <span>买家应付 <PriceText :cents="order.totalCents" /></span
          ><span
            >{{ payText[order.payStatus] || order.payStatus }} ·
            {{ refundText[order.refundStatus] || order.refundStatus }}</span
          ><small>创建于 {{ formatTime(order.createdAt) }}</small>
          <div>
            <a href="#order-items">订单商品 ↓</a
            ><a href="#order-money">费用明细 ↓</a>
          </div>
        </div>
      </section>
      <div class="mm-order-detail__number">
        <div>
          <span>订单号</span><code tabindex="0">{{ order.orderNo }}</code>
        </div>
        <button type="button" :disabled="copying" @click="copyOrderNo">
          {{ copying ? '复制中…' : '复制订单号' }}</button
        ><span
          v-if="copyStatus"
          class="mm-order-detail__copy-status"
          role="status"
          >{{ copyStatus }}</span
        >
      </div>
      <p v-if="order.simulated" class="mm-order-detail__simulation">
        本地模拟交易，未发生真实扣款、退款或分账。
      </p>

      <div class="mm-order-detail__layout">
        <div class="mm-order-detail__main">
          <section
            v-if="isBuyer && order.fulfillmentStatus === 'PENDING_PAYMENT'"
            class="mm-panel mm-order-detail__action-panel"
          >
            <div class="mm-order-detail__section-heading">
              <h2>付款</h2>
              <span>下一步</span>
            </div>
            <p class="mm-muted">
              核对费用明细后发起支付，付款结果以订单状态为准。
            </p>
            <div class="mm-actions">
              <MmButton
                :loading="activeAction === 'pay'"
                :disabled="busy || loading"
                @click="startPay"
                >发起支付</MmButton
              ><MmButton
                variant="ghost"
                :disabled="busy || loading"
                @click="cancel"
                >取消订单</MmButton
              >
            </div>
            <div v-if="payment" class="mm-order-detail__payment">
              <p>{{ payment.message }}</p>
              <template v-if="payment.simulated"
                ><p>
                  <strong
                    >本地模拟支付，不会扣款，也不代表微信支付已开通。</strong
                  >
                </p>
                <div class="mm-actions">
                  <MmButton
                    :loading="activeAction === 'mock'"
                    :disabled="busy"
                    @click="mockPay(true)"
                    >模拟支付成功</MmButton
                  ><MmButton
                    variant="ghost"
                    :disabled="busy"
                    @click="mockPay(false)"
                    >模拟支付失败</MmButton
                  >
                </div></template
              >
            </div>
          </section>

          <form
            v-if="isSeller && order.fulfillmentStatus === 'PAID_PENDING_SHIP'"
            class="mm-panel mm-form mm-order-detail__action-panel"
            @submit.prevent="ship"
          >
            <div class="mm-order-detail__section-heading">
              <h2>登记快递</h2>
              <span>下一步</span>
            </div>
            <p class="mm-muted">填写实际承运商和运单号，让买家查看配送进度。</p>
            <fieldset :disabled="busy || loading">
              <legend class="mm-visually-hidden">发货信息</legend>
              <label
                >快递公司<select v-model="shipping.carrier">
                  <option
                    v-for="(label, key) in carriers"
                    :key="key"
                    :value="key"
                  >
                    {{ label }}
                  </option>
                </select></label
              ><label
                >快递单号<input
                  v-model="shipping.trackingNo"
                  required
                  minlength="6"
                  maxlength="32"
                  pattern="[A-Za-z0-9-]{6,32}" /></label
              ><MmButton
                type="submit"
                :loading="activeAction === 'ship'"
                :disabled="busy || loading"
                >确认发货</MmButton
              >
            </fieldset>
          </form>

          <section
            v-if="order.fulfillmentStatus === 'SHIPPED' || order.shippedAt"
            class="mm-panel"
          >
            <div class="mm-order-detail__section-heading">
              <h2>物流进度</h2>
              <MmButton
                variant="ghost"
                :loading="activeAction === 'shipment'"
                :disabled="busy || loading"
                @click="loadShipment"
                >查询轨迹</MmButton
              >
            </div>
            <template v-if="shipment"
              ><p class="mm-order-detail__tracking">
                {{ carriers[shipment.carrier] || shipment.carrier }} ·
                {{ shipment.trackingNo }}
              </p>
              <span class="mm-chip">{{
                SHIPMENT_STATUS_TEXT[shipment.status]
              }}</span>
              <p v-if="shipment.queryErrorCode" class="mm-notice">
                {{
                  shipment.queryErrorCode === 'LOGISTICS_NOT_CONFIGURED'
                    ? '真实物流服务待配置。'
                    : '本次轨迹查询暂不可用。'
                }}以下显示最近保存的记录，请稍后重试。
              </p>
              <p class="mm-order-detail__traces">
                {{ shipment.traces || '暂无轨迹记录' }}
              </p>
              <p class="mm-muted">
                {{
                  shipment.lastTraceAt
                    ? formatTime(shipment.lastTraceAt)
                    : '暂无更新时间'
                }}
              </p></template
            >
            <p v-else class="mm-muted">
              查询后将在这里展示最近保存的物流记录。
            </p>
            <MmButton
              v-if="isBuyer && order.fulfillmentStatus === 'SHIPPED'"
              :loading="activeAction === 'receipt'"
              :disabled="busy || loading"
              @click="confirmReceipt"
              >确认已收到货物</MmButton
            >
          </section>

          <template v-if="order.fulfillmentStatus === 'AWAITING_MEETUP'">
            <form
              v-if="isSeller"
              class="mm-panel mm-form"
              @submit.prevent="arrange"
            >
              <h2>面交约定</h2>
              <fieldset :disabled="busy || loading">
                <legend class="mm-visually-hidden">面交地点和时间</legend>
                <label
                  >公共面交地点<input
                    v-model="meetup.location"
                    required
                    maxlength="200"
                    @input="meetupEdited = true" /></label
                ><label
                  >约定时间<input
                    v-model="meetup.time"
                    required
                    type="datetime-local"
                    @input="meetupEdited = true" /></label
                ><MmButton
                  type="submit"
                  :loading="activeAction === 'meetup'"
                  :disabled="busy || loading"
                  >保存约定</MmButton
                >
              </fieldset>
            </form>
            <section
              v-if="isBuyer"
              class="mm-panel mm-order-detail__action-panel"
            >
              <h2>当面交付码</h2>
              <p class="mm-muted">
                检查商品后再向卖家出示。每次生成有效 10
                分钟，核验成功即完成订单。
              </p>
              <MmButton
                :loading="activeAction === 'code'"
                :disabled="busy || loading"
                @click="generateCode"
                >生成一次性交付码</MmButton
              >
              <div v-if="code" class="mm-order-detail__code" role="status">
                <strong>{{ code.code }}</strong
                ><span>有效至 {{ formatTime(code.expiresAt) }}</span>
              </div>
            </section>
            <form
              v-if="isSeller"
              class="mm-panel mm-form mm-order-detail__action-panel"
              @submit.prevent="verifyCode"
            >
              <h2>核验交付码</h2>
              <fieldset :disabled="busy || loading">
                <legend class="mm-visually-hidden">核验交付码</legend>
                <label
                  >买家出示的交付码<input
                    v-model="verification"
                    required
                    inputmode="numeric"
                    maxlength="10" /></label
                ><MmButton
                  type="submit"
                  :loading="activeAction === 'verify'"
                  :disabled="busy || loading"
                  >核验并完成面交</MmButton
                >
              </fieldset>
            </form>
          </template>

          <section id="order-items" class="mm-panel mm-order-detail__items">
            <div class="mm-order-detail__section-heading">
              <h2>订单商品</h2>
              <span
                >{{
                  order.items.reduce((sum, item) => sum + item.quantity, 0)
                }}
                件</span
              >
            </div>
            <p class="mm-muted">
              卖家：{{ order.sellerNickname }} · 以下为下单时保存的商品信息
            </p>
            <article
              v-for="(item, index) in order.items"
              :key="index"
              class="mm-order-detail__item"
            >
              <ItemImage :src="item.imagePath" :alt="item.title" />
              <div class="mm-order-detail__item-body">
                <h3>{{ item.title }}</h3>
                <p>
                  {{ CONDITION_TEXT[item.condition] }} · {{ item.quantity }} 件
                </p>
                <p class="mm-order-detail__defects">
                  已知缺陷：{{ item.defects || '卖家未填写' }}
                </p>
              </div>
              <div class="mm-order-detail__item-price">
                <PriceText :cents="item.priceCents" /><small> / 件</small>
              </div>
            </article>
          </section>

          <section class="mm-panel">
            <div class="mm-order-detail__section-heading">
              <h2>交付信息</h2>
              <span>{{ DELIVERY_METHOD_TEXT[order.deliveryMethod] }}</span>
            </div>
            <template v-if="order.deliveryMethod === 'EXPRESS'"
              ><p class="mm-order-detail__receiver">
                <strong>{{ order.receiver }}</strong
                ><span>{{ order.phone }}</span>
              </p>
              <p>{{ order.region }} {{ order.addressDetail }}</p>
              <p v-if="order.autoConfirmAt" class="mm-muted">
                预计自动确认：{{
                  formatTime(order.autoConfirmAt)
                }}；仅物流已签收且无进行中售后时自动确认，其他物流状态将转人工核查。
              </p></template
            >
            <template v-else
              ><dl class="mm-order-detail__delivery">
                <dt>地点</dt>
                <dd>{{ order.meetupLocation || '等待双方约定' }}</dd>
                <dt>时间</dt>
                <dd>
                  {{
                    order.meetupTime ? formatTime(order.meetupTime) : '尚未约定'
                  }}
                </dd>
              </dl></template
            >
            <MmButton
              v-if="isBuyer || isSeller"
              variant="ghost"
              :loading="activeAction === 'contact'"
              :disabled="busy || loading"
              @click="contact"
              >联系{{ isBuyer ? '卖家' : '买家' }}</MmButton
            >
            <p
              v-if="isBuyer && order.fulfillmentStatus === 'AWAITING_MEETUP'"
              class="mm-muted"
            >
              需要调整面交地点或时间，请联系卖家协商，由卖家更新约定。
            </p>
          </section>

          <section
            v-if="
              order.payStatus === 'PAID' &&
              isBuyer &&
              order.refundStatus !== 'FULL'
            "
            class="mm-panel"
          >
            <div class="mm-order-detail__section-heading">
              <h2>售后与退款</h2>
              <MmButton
                variant="ghost"
                :disabled="busy"
                :aria-expanded="showAftersale"
                aria-controls="order-aftersale-form"
                @click="showAftersale = !showAftersale"
                >{{ showAftersale ? '收起申请' : '申请售后' }}</MmButton
              >
            </div>
            <p class="mm-muted">
              常规售后收货后 15 天内申请，卖家 48
              小时答复；法定权利不受此窗口限制。申请金额不得超过剩余可退金额。
            </p>
            <form
              v-if="showAftersale"
              id="order-aftersale-form"
              class="mm-form"
              @submit.prevent="applyAftersale"
            >
              <fieldset :disabled="busy || loading">
                <legend class="mm-visually-hidden">售后申请</legend>
                <label
                  >类型<select v-model="aftersale.type">
                    <option value="REFUND_ONLY">仅退款</option>
                    <option value="RETURN_REFUND">退货退款</option>
                  </select></label
                >
                <div class="mm-order-detail__refund-inputs">
                  <label
                    >商品退款（元）<input
                      v-model="aftersale.goods"
                      type="number"
                      min="0"
                      step="0.01"
                      required /></label
                  ><label
                    >运费退款（元）<input
                      v-model="aftersale.freight"
                      type="number"
                      min="0"
                      step="0.01"
                      required
                  /></label>
                </div>
                <label
                  >原因<textarea
                    v-model="aftersale.reason"
                    required
                    maxlength="500"
                  /></label
                ><label
                  >证据说明<textarea
                    v-model="aftersale.evidence"
                    maxlength="2000"
                    placeholder="描述问题、与卖家的协商和已保存的证据"
                  /></label
                ><MmButton
                  type="submit"
                  :loading="activeAction === 'aftersale'"
                  :disabled="busy || loading"
                  >提交申请</MmButton
                >
              </fieldset>
            </form>
            <RouterLink to="/me/aftersales" class="mm-order-detail__text-link"
              >查看全部售后进度 →</RouterLink
            >
          </section>
          <OrderRating
            v-if="
              order.fulfillmentStatus === 'COMPLETED' && (isBuyer || isSeller)
            "
            :order-id="order.id"
          />
        </div>

        <aside
          id="order-money"
          class="mm-order-detail__money"
          aria-labelledby="order-money-title"
        >
          <h2 id="order-money-title">费用明细</h2>
          <dl class="mm-order-detail__amounts">
            <div>
              <dt>商品成交金额</dt>
              <dd><PriceText :cents="order.goodsAmountCents" /></dd>
            </div>
            <div>
              <dt>运费</dt>
              <dd><PriceText :cents="order.freightCents" /></dd>
            </div>
          </dl>
          <div class="mm-order-detail__total">
            <span>买家应付</span><PriceText :cents="order.totalCents" />
          </div>
          <p class="mm-order-detail__money-status">
            {{ payText[order.payStatus] || order.payStatus }} ·
            {{ refundText[order.refundStatus] || order.refundStatus }}
          </p>
          <details
            class="mm-order-detail__fees"
            :open="isSeller || (!isBuyer && !isSeller)"
          >
            <summary>平台费与卖家净额</summary>
            <dl class="mm-order-detail__amounts">
              <div>
                <dt>原平台服务费<small>卖家承担 0.03%，不含运费</small></dt>
                <dd><PriceText :cents="order.platformFeeCents" /></dd>
              </div>
              <div>
                <dt>退款后保留平台费</dt>
                <dd><PriceText :cents="order.retainedPlatformFeeCents" /></dd>
              </div>
              <div>
                <dt>卖家承担渠道费</dt>
                <dd>
                  <template
                    v-if="
                      order.channelFeeConfirmed &&
                      order.channelFeeCents !== null
                    "
                    ><PriceText :cents="order.channelFeeCents" /><small
                      v-if="order.simulated"
                      >模拟渠道费</small
                    ></template
                  ><span v-else>待渠道确认</span>
                </dd>
              </div>
              <div>
                <dt>卖家预计净额</dt>
                <dd>
                  <PriceText
                    v-if="order.expectedSellerNetCents !== null"
                    :cents="order.expectedSellerNetCents"
                  /><span v-else>待渠道确认</span>
                </dd>
              </div>
            </dl>
            <p>实际以渠道结果为准。</p>
            <div class="mm-order-detail__allocation">
              <span>分账状态</span
              ><strong>{{
                order.allocationStatus === 'WAITING_CHANNEL'
                  ? '等待渠道分账，尚未结算'
                  : '暂无分配记录'
              }}</strong>
            </div>
          </details>
        </aside>
      </div>
    </template>
  </section>
</template>

<script setup lang="ts">
import ItemImage from '../../shared/components/ItemImage.vue';
import { askConfirmation } from '../../shared/confirm';
import { computed, nextTick, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { get, post, type ApiError } from '../../shared/api';
import { useAuthStore } from '../../shared/stores/auth';
import { useTradeDetailNavigation } from '../../shared/navigation';
import { formatTime } from '../../shared/format';
import {
  CONDITION_TEXT,
  DELIVERY_METHOD_TEXT,
  FULFILLMENT_STATUS_TEXT,
  SHIPMENT_STATUS_TEXT,
  type OrderDto,
  type Payment,
  type Shipment,
  type DeliveryCodeResult,
  type AftersaleDetail,
} from '../../shared/types';
import MmButton from '../../shared/components/MmButton.vue';
import PriceText from '../../shared/components/PriceText.vue';
import MmSkeleton from '../../shared/components/MmSkeleton.vue';
import OrderRating from '../community/OrderRating.vue';
const route = useRoute(),
  router = useRouter(),
  auth = useAuthStore();
const order = ref<OrderDto | null>(null),
  error = ref(''),
  loading = ref(true),
  busy = ref(false),
  payment = ref<Payment | null>(null),
  shipment = ref<Shipment | null>(null),
  code = ref<DeliveryCodeResult | null>(null),
  verification = ref(''),
  showAftersale = ref(false);
const feedback = ref(''),
  activeAction = ref(''),
  copyStatus = ref(''),
  copying = ref(false),
  meetupEdited = ref(false);
const errorNotice = ref<HTMLElement | null>(null);
const nextStep = computed(() => {
  const item = order.value;
  if (!item) return '';
  if (!isBuyer.value && !isSeller.value)
    return '查看交易进度与金额信息，具体处理请进入相应管理页面。';
  if (item.refundStatus === 'FULL')
    return '该订单已全额退款，可查看已保存的订单与售后记录。';
  switch (item.fulfillmentStatus) {
    case 'PENDING_PAYMENT':
      return isBuyer.value
        ? '请先核对商品与交付信息，再在有效期内付款。'
        : '等待买家付款，确认订单状态更新后再安排交付。';
    case 'PAID_PENDING_SHIP':
      return isSeller.value
        ? '买家已付款，请准备商品并登记实际快递信息。'
        : '等待卖家发货；发货后可在这里查看物流进度。';
    case 'SHIPPED':
      return isBuyer.value
        ? '商品已发出，收到并检查物品后再确认收货。'
        : '商品已发出，可查看物流并与买家保持沟通。';
    case 'AWAITING_MEETUP':
      return isBuyer.value
        ? '按约定时间面交，检查商品后再出示交付码。'
        : '与买家确认面交安排，交付物品后核验买家出示的交付码。';
    case 'COMPLETED':
      return '本次交付已完成，可以留下真实的交易评价。';
    case 'CLOSED':
      return '订单已关闭，具体支付和退款情况请查看费用明细。';
    default:
      return '最新交易进度以本页订单状态为准。';
  }
});
async function copyOrderNo() {
  const value = order.value?.orderNo;
  if (!value || copying.value) return;
  copying.value = true;
  copyStatus.value = '';
  try {
    if (!navigator.clipboard?.writeText) throw new Error('unavailable');
    await navigator.clipboard.writeText(value);
    if (order.value?.orderNo === value) copyStatus.value = '订单号已复制';
  } catch {
    if (order.value?.orderNo === value)
      copyStatus.value = '无法自动复制，请选中订单号后手动复制';
  } finally {
    copying.value = false;
  }
}
const detailSection = useTradeDetailNavigation({
  detail: () => order.value,
  user: () => auth.me,
  routeKey: () => route.fullPath,
  matchesRoute: (item) => item.orderNo === String(route.params.orderNo),
});
const shipping = ref({ carrier: 'shunfeng', trackingNo: '' }),
  meetup = ref({ location: '', time: '' }),
  aftersale = ref({
    type: 'REFUND_ONLY',
    goods: '0',
    freight: '0',
    reason: '',
    evidence: '',
  });
const isBuyer = computed(() => order.value?.buyerId === auth.me?.id),
  isSeller = computed(() => order.value?.sellerId === auth.me?.id);
const payText: Record<string, string> = {
    UNPAID: '未支付',
    PAYING: '支付中',
    PAID: '已支付',
    CLOSED: '已关闭',
  },
  refundText: Record<string, string> = {
    NONE: '无退款',
    PARTIAL: '部分退款',
    FULL: '全额退款',
  };
const carriers: Record<string, string> = {
  shunfeng: '顺丰',
  zhongtong: '中通',
  yuantong: '圆通',
  shentong: '申通',
  yunda: '韵达',
  youzhengguonei: '邮政',
  jd: '京东',
};
const endpoint = () =>
  `/orders/${encodeURIComponent(String(route.params.orderNo))}`;
let loadSequence = 0;
async function load() {
  const sequence = ++loadSequence;
  const orderNo = String(route.params.orderNo);
  loading.value = true;
  error.value = '';
  try {
    const result = await get<OrderDto>(endpoint());
    if (sequence !== loadSequence || String(route.params.orderNo) !== orderNo)
      return;
    order.value = result;
    if (!showAftersale.value) {
      aftersale.value.goods = (result.goodsAmountCents / 100).toFixed(2);
      aftersale.value.freight = '0';
    }
    if (!meetupEdited.value) {
      meetup.value.location = result.meetupLocation ?? '';
      const time = result.meetupTime ? new Date(result.meetupTime) : null;
      meetup.value.time =
        time && !Number.isNaN(time.getTime())
          ? new Date(time.getTime() - time.getTimezoneOffset() * 60000)
              .toISOString()
              .slice(0, 16)
          : '';
    }
  } catch (e) {
    if (sequence === loadSequence && String(route.params.orderNo) === orderNo)
      error.value = (e as ApiError).message;
  } finally {
    if (sequence === loadSequence) loading.value = false;
  }
}
let actionSequence = 0;
async function run(
  action: (current: () => boolean) => Promise<void>,
  name = '',
  successMessage = '',
) {
  if (busy.value || loading.value) return;
  const sequence = ++actionSequence;
  const orderNo = String(route.params.orderNo);
  const current = () =>
    sequence === actionSequence && String(route.params.orderNo) === orderNo;
  busy.value = true;
  activeAction.value = name;
  error.value = '';
  feedback.value = '';
  try {
    await action(current);
    if (current() && successMessage && !error.value)
      feedback.value = successMessage;
  } catch (e) {
    if (current()) {
      error.value = (e as ApiError).message || '操作失败';
      await nextTick();
      errorNotice.value?.focus();
    }
  } finally {
    if (current()) {
      busy.value = false;
      activeAction.value = '';
    }
  }
}
async function startPay() {
  await run(async (current) => {
    const result = await post<Payment>(endpoint() + '/pay');
    if (current()) payment.value = result;
  }, 'pay');
}
async function mockPay(success: boolean) {
  await run(async (current) => {
    if (!payment.value?.simulated) return;
    await post(`/dev/mock-pay/${success ? 'confirm' : 'fail'}`, {
      payNo: payment.value.payNo,
      ...(success ? { amountCents: payment.value.amountCents } : {}),
    });
    if (!current()) return;
    await load();
    if (current()) payment.value = null;
  }, 'mock');
}
async function cancel() {
  const target = endpoint();
  if (
    !(await askConfirmation('确认取消此待付款订单？')) ||
    target !== endpoint()
  )
    return;
  await run(
    async (current) => {
      await post(target + '/cancel');
      if (current()) await load();
    },
    'cancel',
    '订单已取消',
  );
}
async function ship() {
  await run(
    async (current) => {
      await post(endpoint() + '/ship', shipping.value);
      if (current()) await load();
    },
    'ship',
    '发货信息已保存',
  );
}
async function loadShipment() {
  await run(async (current) => {
    const result = await get<Shipment>(endpoint() + '/shipment');
    if (current()) shipment.value = result;
  }, 'shipment');
}
async function confirmReceipt() {
  const target = endpoint();
  if (
    !(await askConfirmation('已检查并收到商品，确认完成收货？')) ||
    target !== endpoint()
  )
    return;
  await run(
    async (current) => {
      await post(target + '/confirm-receipt');
      if (current()) await load();
    },
    'receipt',
    '收货已确认',
  );
}
async function arrange() {
  await run(
    async (current) => {
      await post(endpoint() + '/meetup', {
        location: meetup.value.location,
        scheduledAt: new Date(meetup.value.time).toISOString(),
      });
      if (current()) {
        meetupEdited.value = false;
        await load();
      }
    },
    'meetup',
    '面交约定已保存',
  );
}
async function generateCode() {
  await run(async (current) => {
    const result = await post<DeliveryCodeResult>(
      endpoint() + '/delivery-code',
    );
    if (current()) code.value = result;
  }, 'code');
}
async function verifyCode() {
  await run(
    async (current) => {
      await post(endpoint() + '/verify-code', { code: verification.value });
      if (current()) await load();
    },
    'verify',
    '交付码核验成功，面交已完成',
  );
}
async function contact() {
  if (!isBuyer.value && !isSeller.value) return;
  await run(async (current) => {
    if (!order.value) return;
    const result = await post<{ id: number }>('/messages/conversations', {
      recipientId: isBuyer.value ? order.value.sellerId : order.value.buyerId,
    });
    if (current()) await router.push(`/messages/${result.id}`);
  }, 'contact');
}
async function applyAftersale() {
  await run(async (current) => {
    const goods = Number(aftersale.value.goods),
      freight = Number(aftersale.value.freight);
    if (
      !Number.isFinite(goods) ||
      !Number.isFinite(freight) ||
      goods < 0 ||
      freight < 0 ||
      goods + freight <= 0
    )
      throw new Error('请填写有效退款金额');
    const result = await post<AftersaleDetail>(endpoint() + '/aftersales', {
      type: aftersale.value.type,
      reason: aftersale.value.reason,
      goodsAmountCents: Math.round(goods * 100),
      freightAmountCents: Math.round(freight * 100),
      evidence: aftersale.value.evidence || null,
    });
    if (current()) await router.push(`/aftersales/${result.id}`);
  }, 'aftersale');
}
watch(
  () => route.params.orderNo,
  () => {
    order.value = null;
    payment.value = null;
    shipment.value = null;
    code.value = null;
    actionSequence++;
    busy.value = false;
    activeAction.value = '';
    feedback.value = '';
    copyStatus.value = '';
    verification.value = '';
    showAftersale.value = false;
    meetupEdited.value = false;
    meetup.value = { location: '', time: '' };
    shipping.value = { carrier: 'shunfeng', trackingNo: '' };
    aftersale.value = {
      type: 'REFUND_ONLY',
      goods: '0',
      freight: '0',
      reason: '',
      evidence: '',
    };
    void load();
  },
  { immediate: true },
);
</script>

<style scoped>
.mm-order-detail {
  max-width: 1160px;
  padding: 32px 24px 56px;
  gap: 20px;
}
.mm-order-detail__header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
}
.mm-order-detail__header h1 {
  font-size: 30px;
  margin-top: 12px;
  letter-spacing: -0.8px;
}
.mm-order-detail__back {
  font-size: 13px;
  color: var(--mm-muted);
  padding: 4px 0;
  display: inline-block;
}
.mm-order-detail__status {
  display: flex;
  justify-content: space-between;
  gap: 30px;
  border: 1px solid var(--mm-border);
  border-left: 4px solid var(--mm-primary);
  border-radius: 10px;
  background: white;
  padding: 26px 28px;
  align-items: center;
}
.mm-order-detail__status-copy {
  min-width: 0;
}
.mm-order-detail__eyebrow {
  font-size: 12px;
  color: var(--mm-muted);
  margin-bottom: 10px;
}
.mm-order-detail__status h2 {
  font-size: 28px;
  letter-spacing: -0.8px;
}
.mm-order-detail__next {
  font-size: 14px;
  line-height: 1.7;
  margin-top: 10px;
}
.mm-order-detail__deadline {
  font-size: 12px;
  color: var(--mm-primary);
  line-height: 1.7;
  margin-top: 10px;
}
.mm-order-detail__status-meta {
  display: flex;
  flex-direction: column;
  gap: 10px;
  text-align: right;
  flex-shrink: 0;
  font-size: 13px;
}
.mm-order-detail__status-meta small {
  font-size: 12px;
  color: var(--mm-muted);
}
.mm-order-detail__status-meta > div {
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 14px;
}
.mm-order-detail__status-meta a {
  margin-top: 6px;
  color: var(--mm-primary);
}
.mm-order-detail__number {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  font-size: 12px;
  color: var(--mm-muted);
  padding: 0 2px;
}
.mm-order-detail__number > div {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  max-width: 100%;
  flex-wrap: wrap;
}
.mm-order-detail__number code {
  font-size: 12px;
  letter-spacing: 0.3px;
  color: var(--mm-ink);
  overflow-wrap: anywhere;
  user-select: all;
}
.mm-order-detail__number button {
  min-height: 36px;
  padding: 5px 10px;
  border: 1px solid var(--mm-border);
  border-radius: 6px;
  color: var(--mm-ink);
  background: white;
}
.mm-order-detail__copy-status {
  color: var(--mm-primary);
  font-size: 12px;
  overflow-wrap: anywhere;
}
.mm-order-detail__simulation {
  font-size: 12px;
  color: var(--mm-muted);
  line-height: 1.65;
  padding: 11px 14px;
  background: var(--mm-warm);
  border-radius: 7px;
}
.mm-order-detail__layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 310px;
  gap: 26px;
  align-items: start;
}
.mm-order-detail__main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.mm-order-detail .mm-panel {
  padding: 24px;
  gap: 16px;
  border-radius: 12px;
  min-width: 0;
}
.mm-order-detail .mm-panel h2 {
  font-size: 18px;
}
.mm-order-detail .mm-panel p {
  line-height: 1.7;
  overflow-wrap: anywhere;
}
.mm-order-detail .mm-panel > .mm-button {
  align-self: flex-start;
}
.mm-order-detail .mm-form {
  max-width: none;
}
.mm-order-detail fieldset {
  min-width: 0;
  border: 0;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.mm-order-detail fieldset > .mm-button {
  align-self: flex-start;
}
.mm-order-detail__action-panel {
  border-top: 3px solid var(--mm-ink);
}
.mm-order-detail__section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.mm-order-detail__section-heading > span {
  font-size: 12px;
  color: var(--mm-muted);
  white-space: nowrap;
}
.mm-order-detail__payment {
  padding: 16px;
  border: 1px dashed #d9ae92;
  border-radius: 8px;
  background: #fff9f3;
  display: flex;
  flex-direction: column;
  gap: 12px;
  font-size: 13px;
}
.mm-order-detail__tracking {
  font-size: 14px;
  font-variant-numeric: tabular-nums;
  overflow-wrap: anywhere;
}
.mm-order-detail__traces {
  white-space: pre-wrap;
  border-left: 2px solid var(--mm-border);
  padding-left: 16px;
  font-size: 14px;
  line-height: 1.9 !important;
}
.mm-order-detail__code {
  padding: 20px;
  border: 1px solid var(--mm-border);
  border-radius: 8px;
  display: flex;
  align-items: flex-start;
  flex-direction: column;
  gap: 10px;
}
.mm-order-detail__code strong {
  font-size: 30px;
  letter-spacing: 6px;
  font-variant-numeric: tabular-nums;
}
.mm-order-detail__code span {
  font-size: 12px;
  color: var(--mm-muted);
}
.mm-order-detail__items {
  scroll-margin-top: 24px;
}
.mm-order-detail__item {
  display: grid;
  grid-template-columns: 76px minmax(0, 1fr) auto;
  gap: 16px;
  align-items: start;
  padding: 18px 0;
  border-top: 1px solid var(--mm-border);
}
.mm-order-detail__item > img {
  width: 76px;
  height: 88px;
  object-fit: cover;
  border-radius: 8px;
  background: var(--mm-canvas);
}
.mm-order-detail__item-body {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 7px;
}
.mm-order-detail__item h3 {
  font-size: 15px;
  line-height: 1.5;
  overflow-wrap: anywhere;
}
.mm-order-detail__item-body > p {
  font-size: 12px;
  color: var(--mm-muted);
}
.mm-order-detail__item-price {
  font-size: 14px;
  white-space: nowrap;
  padding-top: 2px;
}
.mm-order-detail__item-price small {
  color: var(--mm-muted);
  font-size: 11px;
  font-weight: 400;
}
.mm-order-detail__defects {
  background: var(--mm-canvas);
  padding: 7px 10px;
  border-radius: 5px;
}
.mm-order-detail__receiver {
  display: flex;
  gap: 14px;
  align-items: center;
  flex-wrap: wrap;
  font-size: 15px;
}
.mm-order-detail__receiver > span {
  font-size: 14px;
  color: var(--mm-muted);
}
.mm-order-detail__delivery {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr);
  gap: 14px;
  font-size: 14px;
}
.mm-order-detail__delivery dt {
  color: var(--mm-muted);
}
.mm-order-detail__delivery dd {
  margin: 0;
  overflow-wrap: anywhere;
}
.mm-order-detail__refund-inputs {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.mm-order-detail__refund-inputs label {
  min-width: 0;
}
.mm-order-detail__text-link {
  font-size: 13px;
  color: var(--mm-primary);
  align-self: flex-start;
  padding: 5px 0;
}
.mm-order-detail__money {
  position: sticky;
  top: 24px;
  min-width: 0;
  background: white;
  border: 1px solid var(--mm-border);
  border-top: 3px solid var(--mm-ink);
  border-radius: 4px 4px 12px 12px;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.mm-order-detail__money h2 {
  font-size: 19px;
}
.mm-order-detail__amounts {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.mm-order-detail__amounts > div {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  font-size: 13px;
  align-items: flex-start;
  line-height: 1.6;
}
.mm-order-detail__amounts dt {
  color: var(--mm-muted);
  min-width: 0;
}
.mm-order-detail__amounts dd {
  margin: 0;
  text-align: right;
  white-space: nowrap;
}
.mm-order-detail__amounts small {
  display: block;
  font-size: 11px;
  color: var(--mm-muted);
  white-space: normal;
  margin-top: 4px;
}
.mm-order-detail__total {
  border-top: 1px solid var(--mm-border);
  padding-top: 20px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  font-size: 13px;
}
.mm-order-detail__total > .mm-price {
  font-size: 32px;
  letter-spacing: -0.7px;
}
.mm-order-detail__money-status {
  font-size: 12px;
  color: var(--mm-muted);
  margin-top: -8px;
}
.mm-order-detail__fees {
  border-top: 1px solid var(--mm-border);
  padding-top: 10px;
}
.mm-order-detail__fees summary {
  padding: 10px 0;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}
.mm-order-detail__fees .mm-order-detail__amounts {
  margin-top: 16px;
}
.mm-order-detail__fees > p {
  font-size: 11px;
  line-height: 1.7;
  color: var(--mm-muted);
  margin-top: 12px;
}
.mm-order-detail__allocation {
  display: flex;
  flex-direction: column;
  gap: 7px;
  border-top: 1px dashed var(--mm-border);
  padding-top: 14px;
  margin-top: 16px;
  font-size: 12px;
}
.mm-order-detail__allocation > span {
  color: var(--mm-muted);
}
.mm-order-detail__allocation strong {
  font-size: 12px;
  font-weight: 500;
  line-height: 1.6;
}
.mm-order-detail__error {
  padding: 15px 18px;
  border: 1px solid #eac6bd;
  border-radius: 8px;
  background: #fff5f2;
  color: var(--mm-danger);
  font-size: 14px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}
.mm-order-detail__error p {
  margin: 5px 0;
}
.mm-order-detail__feedback {
  color: var(--mm-success);
  font-size: 14px;
  line-height: 1.7;
}
@media (max-width: 850px) {
  .mm-order-detail__layout {
    grid-template-columns: minmax(0, 1fr) 260px;
    gap: 18px;
  }
  .mm-order-detail__money,
  .mm-order-detail .mm-panel {
    padding: 18px;
  }
  .mm-order-detail__status {
    padding: 24px;
    gap: 20px;
  }
  .mm-order-detail__status-meta {
    flex-shrink: 1;
  }
  .mm-order-detail__item {
    grid-template-columns: 64px minmax(0, 1fr);
    gap: 12px;
  }
  .mm-order-detail__item > img {
    width: 64px;
    height: 76px;
  }
  .mm-order-detail__item-price {
    grid-column: 2;
  }
}
@media (max-width: 700px) {
  .mm-order-detail {
    padding: 24px 16px 40px;
    gap: 17px;
  }
  .mm-order-detail__header h1 {
    font-size: 27px;
  }
  .mm-order-detail__header > .mm-button {
    font-size: 13px;
  }
  .mm-order-detail__status {
    padding: 20px;
    flex-direction: column;
    gap: 18px;
    align-items: stretch;
  }
  .mm-order-detail__status h2 {
    font-size: 25px;
  }
  .mm-order-detail__status-meta {
    text-align: left;
    border-top: 1px solid var(--mm-border);
    padding-top: 14px;
    gap: 8px;
  }
  .mm-order-detail__status-meta a {
    margin-top: 0;
  }
  .mm-order-detail__status-meta > div {
    justify-content: flex-start;
  }
  .mm-order-detail__number {
    gap: 6px 12px;
  }
  .mm-order-detail__layout {
    display: flex;
    flex-direction: column;
    gap: 22px;
  }
  .mm-order-detail__main,
  .mm-order-detail__money {
    width: 100%;
  }
  .mm-order-detail__money {
    position: static;
    padding: 22px;
  }
  .mm-order-detail__total {
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
  }
  .mm-order-detail .mm-panel {
    padding: 18px;
  }
  .mm-order-detail .mm-actions {
    gap: 10px;
  }
  .mm-order-detail__refund-inputs {
    grid-template-columns: 1fr;
  }
  .mm-order-detail__section-heading {
    flex-wrap: wrap;
    gap: 10px;
  }
  .mm-order-detail__copy-status {
    flex-basis: 100%;
  }
  .mm-order-detail__code strong {
    font-size: 28px;
  }
  .mm-order-detail .mm-form input[type='datetime-local'] {
    max-width: 100%;
    min-width: 0;
  }
}
</style>
