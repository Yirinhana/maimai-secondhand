<template>
  <div class="experience-pay">
    <div class="experience-pay__intro">
      <span class="experience-pay__badge">体验支付 · 不扣款</span>
      <p>
        扫码在手机上继续，也可以直接打开收银页。只记录体验结果，无需输入微信支付密码。
      </p>
    </div>
    <p v-if="error" role="alert" class="experience-pay__error">{{ error }}</p>
    <MmButton v-if="!session" :loading="busy" @click="create"
      >生成订单二维码</MmButton
    >
    <div v-else class="experience-pay__body">
      <div
        v-if="session.status === 'CREATED' && !expired"
        class="experience-pay__qr"
      >
        <img
          v-if="!imageError"
          :src="session.qrPath"
          width="240"
          height="240"
          alt="本订单体验收银页二维码"
          @error="imageError = true"
        />
        <p v-else>二维码暂未加载，请直接打开收银页。</p>
        <small>剩余 {{ remainingPaymentTime(session.expiresAt, now) }}</small>
      </div>
      <div class="experience-pay__instructions">
        <strong>{{
          expired && session.status === 'CREATED'
            ? '付款时间已结束'
            : paymentStateText[session.status]
        }}</strong>
        <p>手机扫码后，请登录下单的买家账号。完成后本页会自动同步结果。</p>
        <RouterLink
          v-if="session.status === 'CREATED' && !expired"
          :to="session.checkoutPath"
          class="experience-pay__link"
          >打开体验收银页 →</RouterLink
        >
        <MmButton
          v-else-if="
            !expired && ['FAILED', 'CANCELLED'].includes(session.status)
          "
          :loading="busy"
          @click="create"
          >重新生成二维码</MmButton
        >
        <button
          v-if="session.status === 'CREATED'"
          class="experience-pay__refresh"
          type="button"
          :disabled="polling || busy"
          @click="refresh"
        >
          刷新付款状态
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { get, post, type ApiError } from '../../../shared/api';
import MmButton from '../../../shared/components/MmButton.vue';
import {
  type ExperiencePayment,
  paymentStateText,
  remainingPaymentTime,
} from '../experiencePayment';
const props = defineProps<{ orderNo: string }>();
const emit = defineEmits<{ paid: [] }>();
const session = ref<ExperiencePayment | null>(null),
  busy = ref(false),
  polling = ref(false),
  error = ref(''),
  imageError = ref(false),
  now = ref(Date.now());
const expired = computed(
  () => !!session.value && Date.parse(session.value.expiresAt) <= now.value,
);
let timer: ReturnType<typeof setInterval> | undefined,
  alive = true,
  announced = false,
  ticks = 0,
  generation = 0;
function receive(value: ExperiencePayment) {
  if (!alive) return;
  session.value = value;
  if (['PAID', 'REFUNDED'].includes(value.status) && !announced) {
    announced = true;
    emit('paid');
  }
}
async function create() {
  if (busy.value) return;
  busy.value = true;
  error.value = '';
  imageError.value = false;
  const current = ++generation;
  try {
    const value = await post<ExperiencePayment>(
      `/orders/${props.orderNo}/experience-pay`,
    );
    if (current === generation) receive(value);
  } catch (e) {
    if (alive && current === generation) error.value = (e as ApiError).message;
  } finally {
    busy.value = false;
  }
}
async function refresh() {
  if (!session.value || polling.value || busy.value) return;
  polling.value = true;
  const current = generation;
  try {
    const value = await get<ExperiencePayment>(
      `/experience-pay/${session.value.token}`,
    );
    if (current === generation) {
      error.value = '';
      receive(value);
    }
  } catch (e) {
    if (alive && current === generation) error.value = (e as ApiError).message;
  } finally {
    polling.value = false;
  }
}
onMounted(() => {
  timer = setInterval(() => {
    now.value = Date.now();
    if (
      ++ticks % 4 === 0 &&
      !document.hidden &&
      session.value?.status === 'CREATED' &&
      !expired.value
    )
      void refresh();
  }, 1000);
});
onUnmounted(() => {
  alive = false;
  clearInterval(timer);
});
</script>

<style scoped>
.experience-pay {
  padding: 20px;
  background: #f4f8f5;
  border: 1px solid #dce7df;
  border-radius: 16px;
}
.experience-pay__badge {
  color: #276448;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.03em;
}
.experience-pay__intro p,
.experience-pay__instructions p {
  color: var(--mm-muted);
  line-height: 1.8;
  font-size: 13px;
}
.experience-pay__body {
  display: flex;
  align-items: center;
  gap: 28px;
  margin-top: 16px;
}
.experience-pay__qr {
  background: white;
  border-radius: 12px;
  border: 1px solid #e1e7e2;
  padding: 12px;
  flex: 0 0 240px;
  text-align: center;
}
.experience-pay__qr img {
  width: 216px;
  height: 216px;
  max-width: 100%;
  display: block;
  margin: auto;
}
.experience-pay__qr small {
  display: block;
  font-variant-numeric: tabular-nums;
  color: #536159;
}
.experience-pay__instructions {
  min-width: 0;
}
.experience-pay__link {
  display: inline-block;
  padding: 12px 16px;
  background: #286044;
  border-radius: 10px;
  color: white;
  text-decoration: none;
  font-weight: 600;
}
.experience-pay__refresh {
  display: block;
  margin-top: 16px;
  border: 0;
  padding: 0;
  background: transparent;
  color: #52605a;
  cursor: pointer;
}
.experience-pay__error {
  color: var(--mm-danger);
}
@media (max-width: 640px) {
  .experience-pay__body {
    flex-direction: column;
    gap: 18px;
    align-items: stretch;
  }
  .experience-pay__qr {
    flex-basis: auto;
  }
}
</style>
