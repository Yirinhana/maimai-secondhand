<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { get, type ApiError } from '../../shared/api';
import { formatTime } from '../../shared/format';
import {
  FULFILLMENT_STATUS_TEXT,
  type FulfillmentStatus,
  type TotalPage,
} from '../../shared/types';
import MmButton from '../../shared/components/MmButton.vue';
import MmPagination from '../../shared/components/MmPagination.vue';
import PriceText from '../../shared/components/PriceText.vue';
interface FinanceOrder {
  orderId: number;
  orderNo: string;
  buyerId: number;
  sellerId: number;
  goodsAmountCents: number;
  freightCents: number;
  goodsRefundedCents: number;
  freightRefundedCents: number;
  originalPlatformFeeCents: number;
  retainedPlatformFeeCents: number;
  platformFeeRefundedCents: number;
  platformFeeRefundDueCents: number;
  channelFeeCents: number | null;
  channelFeeConfirmed: boolean;
  expectedSellerNetCents: number | null;
  simulated: boolean;
  channel: string | null;
  payStatus: string;
  refundStatus: string;
  fulfillmentStatus: FulfillmentStatus;
  allocationStatus: string;
  createdAt: string;
  paidAt: string | null;
}
interface FinanceDetail {
  order: FinanceOrder;
  ledger: {
    id: number;
    entryType: string;
    amountCents: number;
    referenceType: string;
    referenceId: number | null;
    createdAt: string;
  }[];
  reconciliationStatus: string;
  notice: string;
}
const from = ref(''),
  to = ref(''),
  page = ref(0),
  pages = ref(0),
  items = ref<FinanceOrder[]>([]),
  detail = ref<FinanceDetail | null>(null),
  loading = ref(false),
  error = ref('');
const inspecting = ref<number | null>(null);
const exportUrl = computed(
  () =>
    '/api/v1/admin/finance/export.csv?' +
    new URLSearchParams({
      ...(from.value ? { from: from.value } : {}),
      ...(to.value ? { to: to.value } : {}),
    }).toString(),
);
async function load() {
  loading.value = true;
  error.value = '';
  try {
    const result = await get<TotalPage<FinanceOrder>>('/admin/finance/orders', {
      from: from.value,
      to: to.value,
      page: page.value,
      size: 20,
    });
    items.value = result.content;
    pages.value = Math.ceil(result.total / result.size);
  } catch (e) {
    error.value = (e as ApiError).message;
  } finally {
    loading.value = false;
  }
}
async function inspect(id: number) {
  if (inspecting.value !== null) return;
  if (detail.value?.order.orderId === id) {
    detail.value = null;
    return;
  }
  error.value = '';
  detail.value = null;
  inspecting.value = id;
  try {
    detail.value = await get<FinanceDetail>(`/admin/finance/orders/${id}`);
  } catch (e) {
    error.value = (e as ApiError).message;
  } finally {
    inspecting.value = null;
  }
}
const ledgerNames: Record<string, string> = {
  PAYMENT: '支付记录',
  GOODS_REFUND: '商品退款',
  FREIGHT_REFUND: '运费退款',
  PLATFORM_FEE: '平台服务费',
  PLATFORM_FEE_REFUND: '平台费返还',
  CHANNEL_FEE: '渠道费',
};
onMounted(load);
</script>
<template>
  <section class="finance mm-stack">
    <header class="mm-page-heading">
      <div>
        <p class="mm-eyebrow">账目核对</p>
        <h1>资金与对账</h1>
        <p>按订单核对成交、退款与平台费，查看内部流水明细。</p>
      </div>
    </header>
    <p class="mm-notice">
      本站内部流水及预计分配；未核对渠道账单，未执行真实分账。模拟数据不代表资金到账。
    </p>
    <form
      class="finance__toolbar"
      @submit.prevent="
        page = 0;
        load();
      "
    >
      <label>起始日期<input v-model="from" type="date" /></label
      ><label>截止日期<input v-model="to" type="date" /></label
      ><MmButton type="submit" :disabled="loading">筛选订单</MmButton
      ><a
        :href="exportUrl"
        download="maimai-finance.csv"
        class="finance__export"
        >导出当前范围 CSV</a
      >
    </form>
    <p v-if="error" class="mm-error" role="alert">{{ error }}</p>
    <p v-if="loading" role="status">正在核对订单流水…</p>
    <div v-else class="finance__list">
      <article v-for="item in items" :key="item.orderId" class="finance-order">
        <header class="finance-order__heading">
          <div>
            <strong>{{ item.orderNo }}</strong
            ><time>{{ formatTime(item.createdAt) }}</time>
          </div>
          <div class="finance-order__tags">
            <span class="mm-chip">{{
              FULFILLMENT_STATUS_TEXT[item.fulfillmentStatus]
            }}</span
            ><span v-if="item.simulated" class="finance-order__source"
              >模拟记录</span
            >
          </div>
        </header>
        <dl class="finance-order__amounts">
          <div>
            <dt>成交商品金额</dt>
            <dd><PriceText :cents="item.goodsAmountCents" /></dd>
          </div>
          <div>
            <dt>运费</dt>
            <dd><PriceText :cents="item.freightCents" /></dd>
          </div>
          <div>
            <dt>已退商品款</dt>
            <dd><PriceText :cents="item.goodsRefundedCents" /></dd>
          </div>
          <div>
            <dt>保留平台费</dt>
            <dd><PriceText :cents="item.retainedPlatformFeeCents" /></dd>
          </div>
          <div>
            <dt>渠道费</dt>
            <dd>
              <PriceText
                v-if="item.channelFeeConfirmed && item.channelFeeCents !== null"
                :cents="item.channelFeeCents"
              /><span v-else>待渠道确认</span>
            </dd>
          </div>
          <div class="finance-order__net">
            <dt>卖家预计净额</dt>
            <dd>
              <PriceText
                v-if="item.expectedSellerNetCents !== null"
                :cents="item.expectedSellerNetCents"
              /><span v-else>待渠道确认</span>
            </dd>
          </div>
        </dl>
        <footer class="finance-order__footer">
          <p>
            {{
              item.allocationStatus === 'WAITING_CHANNEL'
                ? '等待渠道分账'
                : '暂无分配记录'
            }}
          </p>
          <MmButton
            variant="ghost"
            :loading="inspecting === item.orderId"
            :disabled="inspecting !== null && inspecting !== item.orderId"
            :aria-expanded="detail?.order.orderId === item.orderId"
            @click="inspect(item.orderId)"
            >{{
              detail?.order.orderId === item.orderId
                ? '收起内部明细'
                : '查看内部明细'
            }}</MmButton
          >
        </footer>
        <section
          v-if="detail?.order.orderId === item.orderId"
          class="finance-detail"
          aria-label="内部流水明细"
        >
          <h2>订单内部明细</h2>
          <p class="mm-notice">{{ detail.notice }}</p>
          <dl class="finance-detail__facts">
            <div>
              <dt>初始平台费</dt>
              <dd>
                <PriceText :cents="detail.order.originalPlatformFeeCents" />
              </dd>
            </div>
            <div>
              <dt>已返还平台费</dt>
              <dd>
                <PriceText :cents="detail.order.platformFeeRefundedCents" />
              </dd>
            </div>
            <div>
              <dt>待返还平台费</dt>
              <dd>
                <PriceText :cents="detail.order.platformFeeRefundDueCents" />
              </dd>
            </div>
            <div>
              <dt>已退运费</dt>
              <dd><PriceText :cents="detail.order.freightRefundedCents" /></dd>
            </div>
          </dl>
          <p v-if="!detail.ledger.length" class="mm-muted">暂无流水记录。</p>
          <div
            v-for="entry in detail.ledger"
            :key="entry.id"
            class="finance-detail__entry"
          >
            <div>
              <strong>{{
                ledgerNames[entry.entryType] || entry.entryType
              }}</strong>
              <p>{{ formatTime(entry.createdAt) }}</p>
            </div>
            <PriceText :cents="entry.amountCents" />
          </div>
        </section>
      </article>
      <div v-if="!items.length && !error" class="finance__empty">
        <h2>当前范围没有订单</h2>
        <p>试试调整日期，或清空日期范围查看全部记录。</p>
        <MmButton
          v-if="from || to"
          variant="ghost"
          @click="
            from = '';
            to = '';
            page = 0;
            load();
          "
          >查看全部记录</MmButton
        >
      </div>
    </div>
    <MmPagination
      :page="page"
      :total-pages="pages"
      @change="
        page = $event;
        load();
      "
    />
  </section>
</template>
<style scoped>
.finance__toolbar {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr)) auto auto;
  gap: 14px;
  align-items: end;
  padding: 20px;
  background: white;
  border: 1px solid var(--mm-zone-border);
  border-radius: 12px;
}
.finance__toolbar label {
  display: grid;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
}
.finance__toolbar input {
  width: 100%;
}
.finance__export {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 44px;
  padding: 9px 14px;
  border: 1px solid var(--mm-zone-border);
  border-radius: 9px;
  color: var(--mm-zone-accent);
  background: var(--mm-zone-soft);
  font-size: 13px;
  font-weight: 600;
  text-decoration: none;
}
.finance__export:hover {
  border-color: var(--mm-zone-accent);
}
.finance__list {
  display: grid;
  gap: 16px;
}
.finance-order {
  background: white;
  border: 1px solid var(--mm-zone-border);
  border-radius: 12px;
  overflow: hidden;
  min-width: 0;
}
.finance-order__heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 18px 20px;
  border-bottom: 1px solid var(--mm-border);
}
.finance-order__heading > div:first-child {
  min-width: 0;
  overflow-wrap: anywhere;
}
.finance-order__heading strong {
  font-size: 15px;
}
.finance-order__heading time {
  display: block;
  font-size: 12px;
  color: var(--mm-muted);
  margin-top: 5px;
}
.finance-order__tags {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}
.finance-order__source {
  font-size: 12px;
  border: 1px solid #e8d6bd;
  padding: 4px 8px;
  border-radius: 6px;
  color: #856034;
  background: #fbf5e9;
  white-space: nowrap;
}
.finance-order__amounts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px 24px;
  margin: 0;
  padding: 20px;
}
.finance-order dt,
.finance-detail dt {
  font-size: 12px;
  color: var(--mm-muted);
  margin-bottom: 6px;
}
.finance-order dd,
.finance-detail dd {
  margin: 0;
  font-size: 15px;
  font-variant-numeric: tabular-nums;
  overflow-wrap: anywhere;
}
.finance-order dd > span:not(.mm-price) {
  font-size: 13px;
}
.finance-order__net {
  padding: 10px 12px;
  margin: -10px -12px;
  background: var(--mm-zone-soft);
  border-radius: 8px;
}
.finance-order__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 14px 20px;
  border-top: 1px solid var(--mm-border);
  background: #fafbfc;
}
.finance-order__footer p {
  font-size: 13px;
  color: var(--mm-muted);
}
.finance-detail {
  display: grid;
  gap: 16px;
  padding: 20px;
  border-top: 1px solid var(--mm-zone-border);
  background: var(--mm-zone-canvas);
}
.finance-detail h2 {
  font-size: 17px;
}
.finance-detail__facts {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin: 0;
}
.finance-detail__entry {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  border-top: 1px solid var(--mm-zone-border);
  padding-top: 14px;
  min-width: 0;
}
.finance-detail__entry strong {
  font-size: 14px;
}
.finance-detail__entry p {
  font-size: 12px;
  color: var(--mm-muted);
  margin-top: 5px;
}
.finance__empty {
  display: grid;
  justify-items: center;
  gap: 12px;
  padding: 36px 20px;
  text-align: center;
  background: white;
  border: 1px solid var(--mm-zone-border);
  border-radius: 12px;
}
.finance__empty h2 {
  font-size: 18px;
}
.finance__empty p {
  color: var(--mm-muted);
}
@media (max-width: 1100px) {
  .finance__toolbar {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .finance__toolbar > button,
  .finance__export {
    justify-self: start;
  }
}
@media (max-width: 650px) {
  .finance-order__heading {
    align-items: start;
    flex-direction: column;
    padding: 16px;
  }
  .finance-order__tags {
    justify-content: flex-start;
  }
  .finance-order__amounts {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 20px;
    padding: 20px 16px;
  }
  .finance-detail__facts {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .finance-order__footer {
    flex-wrap: wrap;
    padding: 14px 16px;
  }
  .finance-detail {
    padding: 16px;
  }
  .finance__toolbar {
    padding: 16px;
    gap: 12px;
  }
}
@media (max-width: 420px) {
  .finance__toolbar {
    grid-template-columns: 1fr;
  }
  .finance__toolbar > button,
  .finance__export {
    justify-self: stretch;
  }
}
</style>
