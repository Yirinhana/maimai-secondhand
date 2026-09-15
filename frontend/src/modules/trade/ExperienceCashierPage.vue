<template>
  <div class="cashier">
    <RouterLink
      :to="session ? `/orders/${session.orderNo}` : '/orders'"
      class="cashier__back"
      >← 返回订单</RouterLink
    >
    <section
      class="cashier__card"
      aria-labelledby="cashier-title"
      :aria-busy="loading"
    >
      <header class="cashier__header">
        <span>麦麦收银台</span
        ><span class="cashier__badge">体验支付 · 不扣款</span>
      </header>
      <p v-if="loading" class="cashier__loading">正在核对订单…</p>
      <template v-else-if="session">
        <div class="cashier__amount">
          <p>本次体验金额</p>
          <PriceText :cents="session.amountCents" />
        </div>
        <div
          v-if="session.status !== 'CREATED' || expired"
          class="cashier__result"
          role="status"
          :key="displayStatus"
        >
          <span
            class="cashier__status-icon"
            :class="{
              'is-success': ['PAID', 'REFUNDED'].includes(displayStatus),
            }"
            aria-hidden="true"
            >{{
              ['PAID', 'REFUNDED'].includes(displayStatus) ? '✓' : '—'
            }}</span
          >
          <h1 id="cashier-title">{{ paymentStateText[displayStatus] }}</h1>
          <p>{{ resultDescription }}</p>
        </div>
        <div v-else class="cashier__pending">
          <h1 id="cashier-title">核对订单，继续体验</h1>
          <p>
            请在
            <strong>{{ remainingPaymentTime(session.expiresAt, now) }}</strong>
            内完成
          </p>
        </div>
        <ul class="cashier__items">
          <li v-for="(item, i) in session.items" :key="i">
            <span
              >{{ item.title }}<small>数量 {{ item.quantity }}</small></span
            ><PriceText :cents="item.priceCents * item.quantity" />
          </li>
        </ul>
        <dl class="cashier__details">
          <div>
            <dt>订单编号</dt>
            <dd>{{ session.orderNo }}</dd>
          </div>
          <div>
            <dt>付款方式</dt>
            <dd>站内体验支付</dd>
          </div>
        </dl>
        <p class="cashier__note">
          本页只保存体验结果，不连接微信或银行卡，不发生扣款、转账和实际发货。
        </p>
        <div
          v-if="session.status === 'CREATED' && !expired"
          class="cashier__actions"
        >
          <MmButton
            :loading="busy === 'SUCCESS'"
            :disabled="!!busy"
            @click="finish('SUCCESS')"
            >确认体验付款（不扣款）</MmButton
          >
          <MmButton variant="ghost" :disabled="!!busy" @click="finish('CANCEL')"
            >取消本次付款</MmButton
          >
          <details>
            <summary>体验其他付款结果</summary>
            <button type="button" :disabled="!!busy" @click="finish('FAIL')">
              体验支付失败
            </button>
          </details>
        </div>
        <RouterLink
          v-else
          :to="`/orders/${session.orderNo}`"
          class="cashier__return"
          >{{
            ['PAID', 'REFUNDED'].includes(displayStatus)
              ? '查看订单与售后'
              : '返回订单查看或重试'
          }}
          →</RouterLink
        >
      </template>
      <div v-if="error" class="cashier__error" role="alert">
        <p>{{ error }}</p>
        <MmButton variant="ghost" :disabled="loading || !!busy" @click="load()"
          >重新加载</MmButton
        >
      </div>
    </section>
    <p class="cashier__footer">二维码仅对应当前订单 · 仅下单买家可操作</p>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { get, post, type ApiError } from '../../shared/api';
import MmButton from '../../shared/components/MmButton.vue';
import PriceText from '../../shared/components/PriceText.vue';
import {
  type ExperiencePayment,
  paymentStateText,
  remainingPaymentTime,
} from './experiencePayment';
const route = useRoute();
const session = ref<ExperiencePayment | null>(null),
  loading = ref(true),
  error = ref(''),
  busy = ref(''),
  now = ref(Date.now());
let timer: ReturnType<typeof setInterval> | undefined,
  generation = 0,
  refreshing = false,
  ticks = 0;
const expired = computed(
  () => !!session.value && Date.parse(session.value.expiresAt) <= now.value,
);
const displayStatus = computed(() =>
  session.value?.status === 'CREATED' && expired.value
    ? 'EXPIRED'
    : (session.value?.status ?? 'CREATED'),
);
const resultDescription = computed(
  () =>
    ({
      PAID:
        session.value?.refundStatus === 'PARTIAL'
          ? '该订单已有部分体验退款，详情可在订单中查看。'
          : '订单状态已同步，可以继续体验订单与售后流程。没有实际扣款。',
      REFUNDED: '体验金额已记为退回，未发生实际资金转账。',
      CANCELLED: '本次付款已取消，订单仍保留至付款期限，可以返回后重新发起。',
      FAILED: '本次未完成付款，未扣款。可以返回订单重新发起。',
      EXPIRED: '二维码不再接受付款，请返回订单重新下单。',
      CLOSED: '该订单已关闭，无法继续付款。',
      CREATED: '',
    })[displayStatus.value],
);
async function load(quiet = false) {
  if ((refreshing && quiet) || busy.value) return;
  const current = generation,
    token = String(route.params.token ?? '');
  refreshing = true;
  if (!quiet) loading.value = true;
  try {
    const value = await get<ExperiencePayment>(
      `/experience-pay/${encodeURIComponent(token)}`,
    );
    if (current === generation) {
      session.value = value;
      error.value = '';
    }
  } catch (e) {
    if (current === generation) error.value = (e as ApiError).message;
  } finally {
    if (current === generation) {
      refreshing = false;
      loading.value = false;
    }
  }
}
async function finish(result: 'SUCCESS' | 'CANCEL' | 'FAIL') {
  if (busy.value || !session.value || expired.value) return;
  busy.value = result;
  error.value = '';
  const current = ++generation;
  refreshing = false;
  try {
    const value = await post<ExperiencePayment>(
      `/experience-pay/${session.value.token}/result`,
      { result },
    );
    if (current === generation) session.value = value;
  } catch (e) {
    if (current === generation) error.value = (e as ApiError).message;
  } finally {
    if (current === generation) busy.value = '';
  }
}
watch(
  () => route.params.token,
  () => {
    generation++;
    refreshing = false;
    busy.value = '';
    session.value = null;
    void load();
  },
  { immediate: true },
);
onMounted(() => {
  timer = setInterval(() => {
    now.value = Date.now();
    if (
      ++ticks % 4 === 0 &&
      session.value?.status === 'CREATED' &&
      !expired.value &&
      !document.hidden
    )
      void load(true);
  }, 1000);
});
onUnmounted(() => {
  generation++;
  clearInterval(timer);
});
</script>

<style scoped>
.cashier {
  max-width: 580px;
  margin: 20px auto 60px;
}
.cashier__back {
  display: inline-block;
  color: var(--mm-muted);
  text-decoration: none;
  margin-bottom: 18px;
  font-size: 13px;
}
.cashier__card {
  background: white;
  border: 1px solid #dfe5df;
  border-radius: 22px;
  overflow: hidden;
  box-shadow: 0 14px 45px #1638260a;
}
.cashier__header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  padding: 20px 28px;
  border-bottom: 1px solid #edf0eb;
  font-weight: 700;
}
.cashier__badge {
  font-size: 12px;
  color: #286044;
  background: #eef5ef;
  padding: 6px 10px;
  border-radius: 20px;
  white-space: nowrap;
}
.cashier__amount {
  text-align: center;
  padding: 30px 24px 16px;
}
.cashier__amount p {
  font-size: 13px;
  color: var(--mm-muted);
  margin: 0 0 10px;
}
.cashier__amount :deep(.mm-price) {
  font-size: 40px;
  letter-spacing: -1px;
}
.cashier__pending,
.cashier__result {
  text-align: center;
  padding: 0 28px 20px;
}
h1 {
  font-size: 20px;
  margin: 12px 0;
}
.cashier__pending p,
.cashier__result p {
  color: var(--mm-muted);
  font-size: 13px;
  line-height: 1.8;
}
.cashier__pending strong {
  color: #286044;
  font-variant-numeric: tabular-nums;
}
.cashier__items {
  list-style: none;
  padding: 0;
  margin: 0 28px;
  border-block: 1px solid #edf0eb;
}
.cashier__items li {
  padding: 15px 0;
  display: flex;
  justify-content: space-between;
  gap: 20px;
  font-size: 14px;
}
.cashier__items small {
  display: block;
  font-size: 12px;
  color: var(--mm-muted);
  margin-top: 6px;
}
.cashier__details {
  margin: 18px 28px;
  font-size: 12px;
}
.cashier__details div {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  margin: 10px 0;
}
.cashier__details dt {
  color: var(--mm-muted);
  flex-shrink: 0;
}
.cashier__details dd {
  margin: 0;
  text-align: right;
  overflow-wrap: anywhere;
}
.cashier__note {
  margin: 20px 28px;
  padding: 12px 14px;
  background: #f5f8f4;
  border-radius: 10px;
  color: #526153;
  font-size: 12px;
  line-height: 1.8;
}
.cashier__actions {
  padding: 0 28px 26px;
  display: grid;
  gap: 10px;
}
.cashier__actions > :first-child {
  background: #286044;
  border-color: #286044;
}
.cashier__actions details {
  margin-top: 8px;
  color: var(--mm-muted);
  font-size: 12px;
}
.cashier__actions summary {
  cursor: pointer;
}
.cashier__actions details button {
  margin-top: 12px;
  border: 1px solid var(--mm-border);
  border-radius: 8px;
  padding: 9px 14px;
  background: white;
  cursor: pointer;
}
.cashier__return {
  display: block;
  margin: 0 28px 28px;
  border-radius: 10px;
  text-align: center;
  background: #286044;
  color: white;
  padding: 14px;
  text-decoration: none;
}
.cashier__status-icon {
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  background: #f0efea;
  color: #68675e;
  border-radius: 50%;
  margin: 8px auto 12px;
  font-size: 25px;
}
.cashier__status-icon.is-success {
  background: #e8f3ea;
  color: #286044;
  animation: result-in 0.35s ease-out;
}
.cashier__footer {
  text-align: center;
  color: var(--mm-muted);
  font-size: 12px;
  margin-top: 20px;
}
.cashier__error {
  padding: 0 28px 24px;
  color: var(--mm-danger);
}
.cashier__loading {
  padding: 40px;
  text-align: center;
  color: var(--mm-muted);
}
@keyframes result-in {
  from {
    transform: scale(0.7);
    opacity: 0.3;
  }
  to {
    transform: scale(1);
    opacity: 1;
  }
}
@media (prefers-reduced-motion: reduce) {
  .cashier__status-icon.is-success {
    animation: none;
  }
}
@media (max-width: 480px) {
  .cashier {
    margin-top: 6px;
  }
  .cashier__header {
    padding: 18px;
  }
  .cashier__amount {
    padding-top: 24px;
  }
  .cashier__items,
  .cashier__details,
  .cashier__note {
    margin-inline: 18px;
  }
  .cashier__actions {
    padding-inline: 18px;
  }
  .cashier__return {
    margin-inline: 18px;
  }
}
</style>
