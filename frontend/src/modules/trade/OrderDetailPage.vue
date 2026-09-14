<template>
  <section class="mm-page">
    <RouterLink
      v-if="detailSection"
      :to="
        detailSection === 'seller'
          ? '/seller/orders'
          : detailSection === 'admin'
            ? '/admin'
            : '/orders'
      "
      >←
      {{ detailSection === 'admin' ? '返回管理后台' : '订单列表' }}</RouterLink
    >
    <h1>订单详情</h1>
    <p v-if="error" class="mm-error" role="alert">{{ error }}</p>
    <p v-if="loading">加载中…</p>
    <template v-if="order">
      <div class="mm-panel">
        <div class="mm-actions">
          <span class="mm-chip">{{
            FULFILLMENT_STATUS_TEXT[order.fulfillmentStatus]
          }}</span
          ><span>{{ order.orderNo }}</span
          ><MmButton variant="ghost" :disabled="busy" @click="load"
            >刷新状态</MmButton
          >
        </div>
        <p
          v-if="order.fulfillmentStatus === 'PENDING_PAYMENT'"
          class="mm-notice"
        >
          请在
          {{ formatTime(order.expiresAt) }} 前付款，超时自动关闭并释放库存。
        </p>
        <p class="mm-muted">
          创建于 {{ formatTime(order.createdAt) }} ·
          {{ DELIVERY_METHOD_TEXT[order.deliveryMethod] }}
        </p>
        <div v-for="(item, index) in order.items" :key="index" class="mm-item">
          <ItemImage
            v-if="item.imagePath"
            :src="item.imagePath"
            :alt="item.title"
          />
          <div class="mm-item-body">
            <strong>{{ item.title }}</strong>
            <p>
              {{ CONDITION_TEXT[item.condition] }} · {{ item.quantity }} 件 ·
              <PriceText :cents="item.priceCents" />
            </p>
            <p class="mm-muted">已知缺陷：{{ item.defects || '卖家未填写' }}</p>
          </div>
        </div>
        <p v-if="order.simulated" class="mm-notice">
          本地模拟交易，未发生真实扣款、退款或分账。
        </p>
        <dl class="mm-data">
          <dt>商品成交金额</dt>
          <dd><PriceText :cents="order.goodsAmountCents" /></dd>
          <dt>运费</dt>
          <dd><PriceText :cents="order.freightCents" /></dd>
          <dt>买家应付</dt>
          <dd><PriceText :cents="order.totalCents" /></dd>
          <dt>原平台服务费</dt>
          <dd>
            <PriceText :cents="order.platformFeeCents" />（卖家承担
            0.03%，不含运费）
          </dd>
          <dt>退款后保留平台费</dt>
          <dd><PriceText :cents="order.retainedPlatformFeeCents" /></dd>
          <dt>卖家承担渠道费</dt>
          <dd>
            <template
              v-if="order.channelFeeConfirmed && order.channelFeeCents !== null"
              ><PriceText :cents="order.channelFeeCents" />{{
                order.simulated ? '（模拟渠道费）' : ''
              }}</template
            ><span v-else>待渠道确认</span>
          </dd>
          <dt>卖家预计净额</dt>
          <dd>
            <PriceText
              v-if="order.expectedSellerNetCents !== null"
              :cents="order.expectedSellerNetCents"
            /><span v-else>待渠道确认</span>（实际以渠道结果为准）
          </dd>
          <dt>支付 / 退款</dt>
          <dd>
            {{ payText[order.payStatus] || order.payStatus }} /
            {{ refundText[order.refundStatus] || order.refundStatus }}
          </dd>
          <dt>分账状态</dt>
          <dd>
            {{
              order.allocationStatus === 'WAITING_CHANNEL'
                ? '等待渠道分账，尚未结算'
                : '暂无分配记录'
            }}
          </dd>
        </dl>
      </div>
      <div class="mm-panel">
        <h2>交付信息</h2>
        <template v-if="order.deliveryMethod === 'EXPRESS'"
          ><p>{{ order.receiver }} {{ order.phone }}</p>
          <p>{{ order.region }} {{ order.addressDetail }}</p>
          <p v-if="order.autoConfirmAt" class="mm-muted">
            预计自动确认：{{
              formatTime(order.autoConfirmAt)
            }}；售后处理中或已知物流异常将暂停。
          </p></template
        ><template v-else
          ><p>地点：{{ order.meetupLocation || '等待双方约定' }}</p>
          <p>
            时间：{{
              order.meetupTime ? formatTime(order.meetupTime) : '尚未约定'
            }}
          </p></template
        >
        <MmButton variant="ghost" :disabled="busy" @click="contact"
          >联系{{ isBuyer ? '卖家' : '买家' }}</MmButton
        >
      </div>
      <div
        v-if="isBuyer && order.fulfillmentStatus === 'PENDING_PAYMENT'"
        class="mm-panel"
      >
        <h2>付款</h2>
        <div class="mm-actions">
          <MmButton :disabled="busy" @click="startPay">发起支付</MmButton
          ><MmButton variant="ghost" :disabled="busy" @click="cancel"
            >取消订单</MmButton
          >
        </div>
        <template v-if="payment"
          ><p class="mm-notice">{{ payment.message }}</p>
          <template v-if="payment.simulated"
            ><p>
              <strong>本地模拟支付，不会扣款，也不代表微信支付已开通。</strong>
            </p>
            <div class="mm-actions">
              <MmButton :disabled="busy" @click="mockPay(true)"
                >模拟支付成功</MmButton
              ><MmButton
                variant="ghost"
                :disabled="busy"
                @click="mockPay(false)"
                >模拟支付失败</MmButton
              >
            </div></template
          ></template
        >
      </div>
      <form
        v-if="isSeller && order.fulfillmentStatus === 'PAID_PENDING_SHIP'"
        class="mm-panel mm-form"
        @submit.prevent="ship"
      >
        <h2>登记快递</h2>
        <label
          >快递公司<select v-model="shipping.carrier">
            <option v-for="(label, key) in carriers" :key="key" :value="key">
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
        ><MmButton type="submit" :disabled="busy">确认发货</MmButton>
      </form>
      <div
        v-if="order.fulfillmentStatus === 'SHIPPED' || order.shippedAt"
        class="mm-panel"
      >
        <h2>物流进度</h2>
        <MmButton variant="ghost" :disabled="busy" @click="loadShipment"
          >查询轨迹</MmButton
        ><template v-if="shipment"
          ><p>
            {{ carriers[shipment.carrier] || shipment.carrier }} ·
            {{ shipment.trackingNo }} ·
            {{ SHIPMENT_STATUS_TEXT[shipment.status] }}
          </p>
          <p v-if="shipment.queryErrorCode" class="mm-notice">
            {{
              shipment.queryErrorCode === 'LOGISTICS_NOT_CONFIGURED'
                ? '真实物流服务待配置。'
                : '本次轨迹查询暂不可用。'
            }}以下显示最近保存的记录，请稍后重试。
          </p>
          <p style="white-space: pre-wrap">
            {{ shipment.traces || '暂无轨迹记录' }}
          </p>
          <p class="mm-muted">
            {{
              shipment.lastTraceAt
                ? formatTime(shipment.lastTraceAt)
                : '暂无更新时间'
            }}
          </p></template
        ><MmButton
          v-if="isBuyer && order.fulfillmentStatus === 'SHIPPED'"
          :disabled="busy"
          @click="confirmReceipt"
          >确认已收到货物</MmButton
        >
      </div>
      <template v-if="order.fulfillmentStatus === 'AWAITING_MEETUP'"
        ><form
          v-if="isBuyer || isSeller"
          class="mm-panel mm-form"
          @submit.prevent="arrange"
        >
          <h2>面交约定</h2>
          <label
            >公共面交地点<input
              v-model="meetup.location"
              required
              maxlength="200" /></label
          ><label
            >约定时间<input
              v-model="meetup.time"
              required
              type="datetime-local" /></label
          ><MmButton type="submit" :disabled="busy">保存约定</MmButton>
        </form>
        <div v-if="isBuyer" class="mm-panel">
          <h2>当面交付码</h2>
          <p class="mm-muted">
            检查商品后再向卖家出示。每次生成有效 10 分钟，核验成功即完成订单。
          </p>
          <MmButton :disabled="busy" @click="generateCode"
            >生成一次性交付码</MmButton
          >
          <p v-if="code">
            <strong style="font-size: 28px; letter-spacing: 8px">{{
              code.code
            }}</strong
            ><br />有效至 {{ formatTime(code.expiresAt) }}
          </p>
        </div>
        <form
          v-if="isSeller"
          class="mm-panel mm-form"
          @submit.prevent="verifyCode"
        >
          <h2>核验交付码</h2>
          <label
            >买家出示的交付码<input
              v-model="verification"
              required
              inputmode="numeric"
              maxlength="10" /></label
          ><MmButton type="submit" :disabled="busy">核验并完成面交</MmButton>
        </form></template
      >
      <div
        v-if="
          order.payStatus === 'PAID' && isBuyer && order.refundStatus !== 'FULL'
        "
        class="mm-panel"
      >
        <h2>售后与退款</h2>
        <p class="mm-muted">
          常规售后收货后 15 天内申请，卖家 48
          小时答复；法定权利不受此窗口限制。申请金额不得超过剩余可退金额。
        </p>
        <MmButton variant="ghost" @click="showAftersale = !showAftersale">{{
          showAftersale ? '收起申请' : '申请售后'
        }}</MmButton>
        <form
          v-if="showAftersale"
          class="mm-form"
          @submit.prevent="applyAftersale"
        >
          <label
            >类型<select v-model="aftersale.type">
              <option value="REFUND_ONLY">仅退款</option>
              <option value="RETURN_REFUND">退货退款</option>
            </select></label
          ><label
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
              required /></label
          ><label
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
          ><MmButton type="submit" :disabled="busy">提交申请</MmButton>
        </form>
        <RouterLink to="/me/aftersales">查看全部售后进度</RouterLink>
      </div>
      <OrderRating
        v-if="order.fulfillmentStatus === 'COMPLETED' && (isBuyer || isSeller)"
        :order-id="order.id"
      />
    </template>
  </section>
</template>

<script setup lang="ts">
import ItemImage from '../../shared/components/ItemImage.vue';
import { askConfirmation } from '../../shared/confirm';
import { computed, ref, watch } from 'vue';
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
    meetup.value.location = result.meetupLocation ?? '';
  } catch (e) {
    if (sequence === loadSequence && String(route.params.orderNo) === orderNo)
      error.value = (e as ApiError).message;
  } finally {
    if (sequence === loadSequence) loading.value = false;
  }
}
async function run(action: () => Promise<void>) {
  if (busy.value) return;
  busy.value = true;
  error.value = '';
  try {
    await action();
  } catch (e) {
    error.value = (e as ApiError).message || '操作失败';
  } finally {
    busy.value = false;
  }
}
async function startPay() {
  await run(async () => {
    payment.value = await post<Payment>(endpoint() + '/pay');
  });
}
async function mockPay(success: boolean) {
  await run(async () => {
    if (!payment.value?.simulated) return;
    await post(`/dev/mock-pay/${success ? 'confirm' : 'fail'}`, {
      payNo: payment.value.payNo,
      ...(success ? { amountCents: payment.value.amountCents } : {}),
    });
    await load();
    payment.value = null;
  });
}
async function cancel() {
  if (!(await askConfirmation('确认取消此待付款订单？'))) return;
  await run(async () => {
    await post(endpoint() + '/cancel');
    await load();
  });
}
async function ship() {
  await run(async () => {
    await post(endpoint() + '/ship', shipping.value);
    await load();
  });
}
async function loadShipment() {
  await run(async () => {
    shipment.value = await get<Shipment>(endpoint() + '/shipment');
  });
}
async function confirmReceipt() {
  if (!(await askConfirmation('已检查并收到商品，确认完成收货？'))) return;
  await run(async () => {
    await post(endpoint() + '/confirm-receipt');
    await load();
  });
}
async function arrange() {
  await run(async () => {
    await post(endpoint() + '/meetup', {
      location: meetup.value.location,
      scheduledAt: new Date(meetup.value.time).toISOString(),
    });
    await load();
  });
}
async function generateCode() {
  await run(async () => {
    code.value = await post<DeliveryCodeResult>(endpoint() + '/delivery-code');
  });
}
async function verifyCode() {
  await run(async () => {
    await post(endpoint() + '/verify-code', { code: verification.value });
    await load();
  });
}
async function contact() {
  await run(async () => {
    if (!order.value) return;
    const result = await post<{ id: number }>('/messages/conversations', {
      recipientId: isBuyer.value ? order.value.sellerId : order.value.buyerId,
    });
    await router.push(`/messages/${result.id}`);
  });
}
async function applyAftersale() {
  await run(async () => {
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
    await router.push(`/aftersales/${result.id}`);
  });
}
watch(
  () => route.params.orderNo,
  () => {
    order.value = null;
    payment.value = null;
    shipment.value = null;
    code.value = null;
    void load();
  },
  { immediate: true },
);
</script>
