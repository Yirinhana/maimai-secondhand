<template>
  <div class="mm-order-list">
    <nav class="mm-order-list__tabs" aria-label="订单状态筛选">
      <button
        v-for="tab in tabs"
        :key="tab.label"
        type="button"
        class="mm-order-list__tab"
        :class="{ 'is-active': tab.status === activeStatus }"
        :aria-pressed="tab.status === activeStatus"
        @click="switchTab(tab.status)"
      >
        {{ tab.label }}
      </button>
    </nav>

    <p v-if="error" class="mm-order-list__error" role="alert">{{ error }}</p>
    <p v-else-if="loading" class="mm-order-list__hint">加载中…</p>

    <template v-else-if="orders.length">
      <RouterLink
        v-for="order in orders"
        :key="order.orderNo"
        :to="{ name: 'order-detail', params: { orderNo: order.orderNo } }"
        class="mm-order-list__card"
      >
        <div class="mm-order-list__card-head">
          <span class="mm-order-list__no"
            >订单号 {{ order.orderNo
            }}<small
              v-if="order.experienceSource"
              style="margin-left: 10px; color: var(--mm-muted)"
              >体验订单</small
            ></span
          >
          <MmTag
            :text="FULFILLMENT_STATUS_TEXT[order.fulfillmentStatus]"
            :tone="statusTone(order.fulfillmentStatus)"
          />
        </div>
        <div class="mm-order-list__peer">
          {{ role === 'buyer' ? `卖家：${order.sellerNickname}` : `买家订单` }}
          · {{ DELIVERY_METHOD_TEXT[order.deliveryMethod] }} ·
          {{ formatTime(order.createdAt) }}
        </div>
        <ul class="mm-order-list__items">
          <li
            v-for="(item, idx) in order.items"
            :key="idx"
            class="mm-order-list__item"
          >
            <span class="mm-order-list__item-cover">
              <ItemImage
                v-if="item.imagePath"
                :src="item.imagePath"
                :alt="item.title"
              />
              <span v-else class="mm-order-list__item-noimg">暂无图</span>
            </span>
            <span class="mm-order-list__item-title">{{ item.title }}</span>
            <span class="mm-order-list__item-qty">× {{ item.quantity }}</span>
          </li>
        </ul>
        <dl class="mm-order-list__amounts">
          <div>
            <dt>商品款</dt>
            <dd><PriceText :cents="order.goodsAmountCents" /></dd>
          </div>
          <div>
            <dt>运费</dt>
            <dd><PriceText :cents="order.freightCents" /></dd>
          </div>
          <div>
            <dt>卖家承担的平台费</dt>
            <dd><PriceText :cents="order.platformFeeCents" /></dd>
          </div>
          <div class="mm-order-list__total">
            <dt>合计</dt>
            <dd><PriceText :cents="order.totalCents" /></dd>
          </div>
        </dl>
        <p
          v-if="order.fulfillmentStatus === 'PENDING_PAYMENT'"
          class="mm-order-list__deadline"
        >
          请在 {{ formatTime(order.expiresAt) }} 前付款，逾期订单自动关闭
        </p>
        <span class="mm-order-list__detail"
          >查看订单详情 <span aria-hidden="true">→</span></span
        >
      </RouterLink>
      <MmPagination :page="page" :total-pages="totalPages" @change="onPage" />
    </template>

    <EmptyState
      v-else
      title="暂无相关订单"
      :description="
        activeStatus
          ? '这个状态下还没有订单，试试查看全部订单。'
          : role === 'buyer'
            ? '遇见喜欢的好物，下单后可以在这里跟进进度。'
            : '有买家下单后，订单会出现在这里。'
      "
      :icon="role === 'buyer' ? 'cart' : 'box'"
      ><RouterLink :to="role === 'buyer' ? '/search' : '/seller/products'">{{
        role === 'buyer' ? '去逛逛闲置 →' : '查看我的商品 →'
      }}</RouterLink></EmptyState
    >
  </div>
</template>

<script setup lang="ts">
import ItemImage from '../../../shared/components/ItemImage.vue';
import { onMounted, ref } from 'vue';
import { get, type ApiError } from '../../../shared/api';
import { formatTime } from '../../../shared/format';
import {
  DELIVERY_METHOD_TEXT,
  FULFILLMENT_STATUS_TEXT,
  type FulfillmentStatus,
  type OrderDto,
  type Page,
} from '../../../shared/types';
import MmTag from '../../../shared/components/MmTag.vue';
import MmPagination from '../../../shared/components/MmPagination.vue';
import EmptyState from '../../../shared/components/EmptyState.vue';
import PriceText from '../../../shared/components/PriceText.vue';

const props = defineProps<{ role: 'buyer' | 'seller' }>();

const tabs: { label: string; status: FulfillmentStatus | '' }[] = [
  { label: '全部', status: '' },
  { label: '待付款', status: 'PENDING_PAYMENT' },
  { label: '待发货', status: 'PAID_PENDING_SHIP' },
  { label: '待收货', status: 'SHIPPED' },
  { label: '待面交', status: 'AWAITING_MEETUP' },
  { label: '已完成', status: 'COMPLETED' },
  { label: '已关闭', status: 'CLOSED' },
];

const activeStatus = ref<FulfillmentStatus | ''>('');
const orders = ref<OrderDto[]>([]);
const page = ref(0);
const totalPages = ref(0);
const loading = ref(true);
const error = ref('');

function statusTone(
  status: FulfillmentStatus,
): 'primary' | 'success' | 'warning' | 'danger' | 'info' | 'neutral' {
  switch (status) {
    case 'PENDING_PAYMENT':
      return 'warning';
    case 'PAID_PENDING_SHIP':
    case 'SHIPPED':
    case 'AWAITING_MEETUP':
      return 'info';
    case 'COMPLETED':
      return 'success';
    case 'CLOSED':
      return 'neutral';
    default:
      return 'primary';
  }
}

async function load() {
  loading.value = true;
  error.value = '';
  try {
    const res = await get<Page<OrderDto>>('/orders', {
      role: props.role,
      status: activeStatus.value || undefined,
      page: page.value,
      size: 10,
    });
    orders.value = res.content;
    totalPages.value = res.totalPages;
  } catch (e) {
    error.value = (e as ApiError).message;
  } finally {
    loading.value = false;
  }
}

function switchTab(status: FulfillmentStatus | '') {
  if (status === activeStatus.value) return;
  activeStatus.value = status;
  page.value = 0;
  load();
}

function onPage(p: number) {
  page.value = p;
  load();
}

onMounted(load);
</script>

<style scoped>
.mm-order-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.mm-order-list__tabs {
  display: flex;
  gap: 5px;
  overflow-x: auto;
  border: 1px solid var(--mm-zone-border);
  padding: 7px;
  background: white;
  border-radius: 11px;
}
.mm-order-list__tab {
  flex: none;
  white-space: nowrap;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: var(--mm-muted);
  font-size: 13px;
  padding: 10px 19px;
  min-height: 42px;
}
.mm-order-list__tab:hover {
  background: var(--mm-zone-soft);
  color: var(--mm-zone-accent);
}
.mm-order-list__tab.is-active {
  background: var(--mm-zone-accent);
  color: white;
  font-weight: 750;
}
.mm-order-list__error {
  color: var(--mm-danger);
  font-size: 13px;
}
.mm-order-list__hint {
  color: var(--mm-muted);
  font-size: 13px;
}
.mm-order-list__card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 270px;
  gap: 15px 30px;
  background: white;
  border: 1px solid var(--mm-border);
  border-radius: 8px;
  overflow: hidden;
  padding: 0 24px 23px;
  color: var(--mm-ink);
}
.mm-order-list__card:hover {
  text-decoration: none;
  border-color: #b9bfb2;
}
.mm-order-list__card-head {
  grid-column: 1/-1;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  background: var(--mm-zone-soft);
  border-bottom: 1px solid var(--mm-zone-border);
  margin: 0 -24px;
  padding: 13px 24px;
}
.mm-order-list__no {
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  color: var(--mm-muted);
  overflow-wrap: anywhere;
}
.mm-order-list__peer {
  grid-column: 1;
  font-size: 11px;
  color: var(--mm-muted);
}
.mm-order-list__items {
  grid-column: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.mm-order-list__item {
  display: flex;
  align-items: center;
  gap: 17px;
  min-width: 0;
}
.mm-order-list__item-cover {
  width: 76px;
  height: 76px;
  flex: none;
  border-radius: 6px;
  overflow: hidden;
  background: var(--mm-canvas);
  display: flex;
  align-items: center;
  justify-content: center;
}
.mm-order-list__item-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.mm-order-list__item-noimg {
  font-size: 10px;
  color: var(--mm-muted);
}
.mm-order-list__item-title {
  flex: 1;
  min-width: 0;
  font-size: 14px;
  overflow-wrap: anywhere;
  font-weight: 600;
}
.mm-order-list__item-qty {
  font-size: 12px;
  color: var(--mm-muted);
}
.mm-order-list__amounts {
  grid-column: 2;
  grid-row: 2 / span 2;
  margin: 0;
  border-left: 1px solid var(--mm-border);
  padding-left: 25px;
  display: flex;
  flex-direction: column;
  gap: 7px;
  justify-content: center;
}
.mm-order-list__amounts > div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 12px;
}
.mm-order-list__amounts dt {
  color: var(--mm-muted);
}
.mm-order-list__amounts dd {
  margin: 0;
}
.mm-order-list__amounts :deep(.mm-price) {
  font-weight: 500;
  color: var(--mm-ink);
}
.mm-order-list__total {
  padding-top: 8px;
  border-top: 1px solid var(--mm-border);
  margin-top: 4px;
}
.mm-order-list__total :deep(.mm-price) {
  font-size: 20px;
  font-weight: 750;
}
.mm-order-list__detail {
  grid-column: 1/-1;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--mm-border);
  color: var(--mm-zone-accent);
  font-size: 13px;
  font-weight: 650;
}
.mm-order-list__deadline {
  grid-column: 1/-1;
  font-size: 11px;
  color: var(--mm-warning);
  border-top: 1px solid var(--mm-border);
  padding-top: 12px;
}
@media (max-width: 760px) {
  .mm-order-list__detail {
    justify-content: flex-start;
  }
  .mm-order-list__tabs {
    gap: 4px;
  }
  .mm-order-list__tab {
    padding: 10px 14px;
  }
  .mm-order-list__card {
    grid-template-columns: minmax(0, 1fr);
    padding: 0 16px 18px;
    gap: 15px;
  }
  .mm-order-list__card-head {
    margin: 0 -16px;
    padding: 13px 16px;
    gap: 10px;
  }
  .mm-order-list__amounts {
    grid-column: 1;
    grid-row: auto;
    border-left: 0;
    border-top: 1px dashed var(--mm-border);
    padding: 14px 0 0;
  }
  .mm-order-list__item-cover {
    width: 62px;
    height: 62px;
  }
  .mm-order-list__item-title {
    font-size: 13px;
  }
  .mm-order-list__no {
    font-size: 10px;
  }
  .mm-order-list__card-head :deep(.mm-tag) {
    font-size: 11px;
  }
}
</style>
