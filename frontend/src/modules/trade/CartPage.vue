<template>
  <div class="mm-cart">
    <header class="mm-cart__header">
      <div>
        <p class="mm-cart__eyebrow">挑好闲置，再一起结算</p>
        <h1 class="mm-cart__heading">
          购物车 <span v-if="items.length">{{ items.length }}</span>
        </h1>
      </div>
      <RouterLink to="/search" class="mm-cart__browse"
        >继续选购 <span aria-hidden="true">↗</span></RouterLink
      >
    </header>

    <div v-if="error" class="mm-cart__error" role="alert">
      <div>
        <strong>{{
          items.length ? '刚才的操作未完成' : '暂时无法读取购物车'
        }}</strong>
        <p>{{ error }}</p>
      </div>
      <MmButton
        variant="ghost"
        :disabled="loading || busyId !== null"
        @click="load"
        >重新加载</MmButton
      >
    </div>
    <p v-if="feedback" class="mm-cart__feedback" role="status">
      {{ feedback }}
    </p>
    <MmSkeleton
      v-if="loading && !items.length"
      kind="rows"
      :count="3"
      label="正在读取购物车"
    />

    <div v-else-if="groups.length" class="mm-cart__layout" :aria-busy="loading">
      <div class="mm-cart__groups">
        <div class="mm-cart__selection">
          <label class="mm-cart__check-all">
            <input
              type="checkbox"
              :checked="allValidSelected"
              :indeterminate="selectedItems.length > 0 && !allValidSelected"
              :disabled="validItems.length === 0 || busyId !== null || loading"
              @change="toggleAll"
            />
            全选可购买商品
          </label>
          <span
            >{{ validItems.length }} 种可购买<span
              v-if="items.length > validItems.length"
            >
              · {{ items.length - validItems.length }} 种暂不可购买</span
            ></span
          >
        </div>
        <section
          v-for="group in groups"
          :key="group.sellerKey"
          class="mm-cart__group"
        >
          <header class="mm-cart__seller">
            <span class="mm-cart__seller-icon" aria-hidden="true">{{
              group.sellerNickname.slice(0, 1)
            }}</span>
            <h2><small>卖家</small>{{ group.sellerNickname }}</h2>
            <span>{{ group.items.length }} 种商品</span>
          </header>
          <ul class="mm-cart__items">
            <li
              v-for="item in group.items"
              :key="item.id"
              class="mm-cart__item"
              :class="{
                'is-invalid': item.invalid,
                'is-busy': busyId === item.id,
              }"
              :aria-busy="busyId === item.id"
            >
              <label class="mm-cart__check">
                <input
                  type="checkbox"
                  :checked="selected.has(item.id)"
                  :disabled="item.invalid || busyId !== null || loading"
                  :aria-label="'选择 ' + (item.title ?? '失效商品')"
                  @change="toggle(item)"
                />
              </label>
              <RouterLink
                :to="'/products/' + item.productId"
                class="mm-cart__cover"
              >
                <ItemImage
                  :src="item.coverImage"
                  :alt="item.title ?? '失效商品'"
                />
              </RouterLink>
              <div class="mm-cart__info">
                <RouterLink
                  :to="'/products/' + item.productId"
                  class="mm-cart__title"
                  >{{ item.title ?? '商品已删除' }}</RouterLink
                >
                <div class="mm-cart__meta">
                  <MmTag
                    :text="DELIVERY_METHOD_TEXT[item.deliveryMethod]"
                    tone="primary"
                  />
                  <MmTag
                    v-if="item.invalid"
                    :text="invalidReason(item)"
                    tone="danger"
                  />
                </div>
                <div class="mm-cart__unit">
                  <PriceText :cents="item.priceCents" /><span> / 件</span>
                </div>
                <div class="mm-cart__row">
                  <div
                    class="mm-cart__stepper"
                    role="group"
                    :aria-label="(item.title ?? '商品') + '数量'"
                  >
                    <button
                      type="button"
                      :disabled="
                        item.quantity <= 1 || busyId !== null || loading
                      "
                      aria-label="减少数量"
                      @click="changeQuantity(item, item.quantity - 1)"
                    >
                      −
                    </button>
                    <span class="mm-cart__qty">{{ item.quantity }}</span>
                    <button
                      type="button"
                      :disabled="atMax(item) || busyId !== null || loading"
                      aria-label="增加数量"
                      @click="changeQuantity(item, item.quantity + 1)"
                    >
                      ＋
                    </button>
                  </div>
                  <span
                    v-if="busyId === item.id"
                    class="mm-cart__stock"
                    role="status"
                    >{{
                      busyAction === 'remove' ? '正在移除…' : '正在更新数量…'
                    }}</span
                  >
                  <span
                    v-else-if="item.stockAvailable !== null"
                    class="mm-cart__stock"
                    >可购 {{ item.stockAvailable }} 件</span
                  >
                  <button
                    type="button"
                    class="mm-cart__remove"
                    :disabled="busyId !== null || loading"
                    :aria-label="'删除 ' + (item.title ?? '失效商品')"
                    @click="remove(item)"
                  >
                    删除
                  </button>
                </div>
                <p v-if="item.invalid" class="mm-cart__invalid-hint">
                  暂不可结算，可调整数量或移除后重新选购。
                </p>
              </div>
              <div class="mm-cart__subtotal">
                <small>商品小计</small
                ><PriceText :cents="item.priceCents * item.quantity" />
              </div>
            </li>
          </ul>
        </section>
      </div>
      <aside class="mm-cart__summary" aria-labelledby="cart-summary-title">
        <h2 id="cart-summary-title">结算预览</h2>
        <dl>
          <div>
            <dt>选中商品</dt>
            <dd>{{ selectedUnits }} 件 / {{ selectedItems.length }} 种</dd>
          </div>
          <div>
            <dt>卖家</dt>
            <dd>{{ selectedSellerCount }} 位</dd>
          </div>
        </dl>
        <div class="mm-cart__total">
          <span>商品款合计</span><PriceText :cents="selectedGoodsCents" />
        </div>
        <p class="mm-cart__freight-note">
          下一步确认交付方式和运费。不同卖家的订单分别付款。
        </p>
        <MmButton
          :disabled="selectedItems.length === 0 || busyId !== null || loading"
          @click="goCheckout"
          >去结算<span v-if="selectedUnits"
            >（{{ selectedUnits }} 件）</span
          ></MmButton
        >
        <p v-if="!selectedItems.length" class="mm-cart__selection-hint">
          先勾选想购买的商品
        </p>
        <p v-else class="mm-cart__selection-hint">提交前还可核对收货信息</p>
      </aside>
    </div>

    <section v-else-if="!error" class="mm-cart__empty">
      <svg
        width="96"
        height="96"
        viewBox="0 0 96 96"
        fill="none"
        aria-hidden="true"
      >
        <circle cx="48" cy="48" r="46" fill="var(--mm-warm)" />
        <path
          d="M25 29h8l6 31h30l7-23H36M43 70h.1M65 70h.1"
          stroke="currentColor"
          stroke-width="3"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
        <path
          d="M51 29v16m-8-8h16"
          stroke="var(--mm-primary)"
          stroke-width="2.5"
          stroke-linecap="round"
        />
      </svg>
      <h2>购物车是空的</h2>
      <p>遇到心动的闲置，先加入购物车，慢慢挑选。</p>
      <RouterLink to="/search" class="mm-cart__empty-link"
        >去逛逛 <span aria-hidden="true">→</span></RouterLink
      >
    </section>
  </div>
</template>

<script setup lang="ts">
import ItemImage from '../../shared/components/ItemImage.vue';
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { del, get, put, type ApiError } from '../../shared/api';
import {
  DELIVERY_METHOD_TEXT,
  PRODUCT_STATUS_TEXT,
  type CartItem,
} from '../../shared/types';
import MmButton from '../../shared/components/MmButton.vue';
import MmTag from '../../shared/components/MmTag.vue';
import MmSkeleton from '../../shared/components/MmSkeleton.vue';
import PriceText from '../../shared/components/PriceText.vue';

interface SellerGroup {
  sellerKey: string;
  sellerNickname: string;
  items: CartItem[];
}

const router = useRouter();
const items = ref<CartItem[]>([]);
const loading = ref(true);
const error = ref('');
const busyId = ref<number | null>(null);
const busyAction = ref<'quantity' | 'remove'>('quantity');
const feedback = ref('');
const selected = ref<Set<number>>(new Set());

const groups = computed<SellerGroup[]>(() => {
  const map = new Map<string, SellerGroup>();
  for (const item of items.value) {
    const key = item.sellerId === null ? 'deleted' : String(item.sellerId);
    let group = map.get(key);
    if (!group) {
      group = {
        sellerKey: key,
        sellerNickname: item.sellerNickname ?? '商品已删除',
        items: [],
      };
      map.set(key, group);
    }
    group.items.push(item);
  }
  return [...map.values()];
});

const validItems = computed(() => items.value.filter((i) => !i.invalid));
const selectedItems = computed(() =>
  items.value.filter((i) => !i.invalid && selected.value.has(i.id)),
);
const selectedGoodsCents = computed(() =>
  selectedItems.value.reduce((sum, i) => sum + i.priceCents * i.quantity, 0),
);
const selectedUnits = computed(() =>
  selectedItems.value.reduce((sum, item) => sum + item.quantity, 0),
);
const selectedSellerCount = computed(
  () => new Set(selectedItems.value.map((item) => item.sellerId)).size,
);
const allValidSelected = computed(
  () =>
    validItems.value.length > 0 &&
    validItems.value.every((i) => selected.value.has(i.id)),
);

function invalidReason(item: CartItem): string {
  if (item.title === null || item.productStatus === null) return '商品已删除';
  if (item.productStatus !== 'ON_SALE') {
    return PRODUCT_STATUS_TEXT[item.productStatus] ?? '商品不可售';
  }
  return '库存不足';
}

function atMax(item: CartItem): boolean {
  return item.stockAvailable !== null && item.quantity >= item.stockAvailable;
}

function toggle(item: CartItem) {
  if (item.invalid) return;
  const next = new Set(selected.value);
  if (next.has(item.id)) next.delete(item.id);
  else next.add(item.id);
  selected.value = next;
}

function toggleAll() {
  const next = new Set(selected.value);
  if (allValidSelected.value) {
    for (const i of validItems.value) next.delete(i.id);
  } else {
    for (const i of validItems.value) next.add(i.id);
  }
  selected.value = next;
}

async function load() {
  if (loading.value && items.value.length) return;
  loading.value = true;
  error.value = '';
  try {
    items.value = await get<CartItem[]>('/cart');
    const alive = new Set(
      items.value.filter((i) => !i.invalid).map((i) => i.id),
    );
    selected.value = new Set([...selected.value].filter((id) => alive.has(id)));
  } catch (e) {
    error.value = (e as ApiError).message;
  } finally {
    loading.value = false;
  }
}

async function changeQuantity(item: CartItem, quantity: number) {
  if (quantity < 1 || busyId.value !== null) return;
  busyId.value = item.id;
  busyAction.value = 'quantity';
  error.value = '';
  feedback.value = '';
  try {
    const updated = await put<CartItem>(`/cart/${item.id}`, { quantity });
    Object.assign(item, updated);
    if (updated.invalid) {
      const next = new Set(selected.value);
      next.delete(item.id);
      selected.value = next;
    }
    feedback.value =
      '已更新数量：' + (item.title ?? '商品') + '，' + item.quantity + ' 件';
  } catch (e) {
    const message = (e as ApiError).message || '数量更新失败，请重试';
    await load();
    error.value = message;
  } finally {
    busyId.value = null;
  }
}

async function remove(item: CartItem) {
  if (busyId.value !== null) return;
  busyId.value = item.id;
  busyAction.value = 'remove';
  error.value = '';
  feedback.value = '';
  try {
    await del(`/cart/${item.id}`);
    items.value = items.value.filter((i) => i.id !== item.id);
    const next = new Set(selected.value);
    next.delete(item.id);
    selected.value = next;
    feedback.value = '已从购物车移除：' + (item.title ?? '失效商品');
  } catch (e) {
    error.value = (e as ApiError).message;
  } finally {
    busyId.value = null;
  }
}

function goCheckout() {
  if (busyId.value !== null || loading.value || !selectedItems.value.length)
    return;
  const ids = selectedItems.value.map((i) => i.id);
  router.push({ name: 'checkout', query: { cartItemIds: ids.join(',') } });
}

onMounted(load);
</script>

<style scoped>
.mm-cart {
  max-width: 1160px;
  margin: 0 auto;
  padding: 32px 24px 56px;
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 22px;
}
.mm-cart__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}
.mm-cart__eyebrow {
  font-size: 13px;
  color: var(--mm-muted);
  margin-bottom: 8px;
}
.mm-cart__heading {
  font-size: 32px;
  letter-spacing: -1px;
  display: flex;
  align-items: center;
  gap: 14px;
}
.mm-cart__heading span {
  font-size: 14px;
  letter-spacing: 0;
  font-weight: 500;
  background: var(--mm-warm);
  border-radius: 999px;
  min-width: 30px;
  padding: 3px 9px;
  text-align: center;
}
.mm-cart__browse {
  font-size: 14px;
  color: var(--mm-ink);
  white-space: nowrap;
  padding: 10px 0;
}
.mm-cart__browse span {
  margin-left: 8px;
  color: var(--mm-primary);
}
.mm-cart__layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 28px;
  align-items: start;
}
.mm-cart__groups {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.mm-cart__selection {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  font-size: 12px;
  color: var(--mm-muted);
  min-height: 36px;
}
.mm-cart__check-all {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--mm-ink);
  font-size: 14px;
  min-height: 40px;
}
.mm-cart__group {
  background: var(--mm-white);
  border: 1px solid var(--mm-border);
  border-radius: 14px;
  overflow: hidden;
}
.mm-cart__seller {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px 20px;
  border-bottom: 1px solid var(--mm-border);
  background: var(--mm-zone-soft);
}
.mm-cart__seller-icon {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  flex-shrink: 0;
  background: var(--mm-warm);
  border-radius: 50%;
  font-size: 14px;
}
.mm-cart__seller h2 {
  font-size: 15px;
  min-width: 0;
  overflow-wrap: anywhere;
  display: flex;
  gap: 9px;
  align-items: center;
}
.mm-cart__seller h2 small {
  font-size: 12px;
  font-weight: 400;
  color: var(--mm-muted);
  white-space: nowrap;
}
.mm-cart__seller > span:last-child {
  font-size: 12px;
  color: var(--mm-muted);
  margin-left: auto;
  white-space: nowrap;
}
.mm-cart__items {
  list-style: none;
  margin: 0;
  padding: 0;
}
.mm-cart__item {
  display: grid;
  grid-template-columns: 22px 92px minmax(0, 1fr) auto;
  gap: 14px;
  align-items: start;
  padding: 22px 20px;
}
.mm-cart__item + .mm-cart__item {
  border-top: 1px solid var(--mm-border);
}
.mm-cart__item.is-invalid .mm-cart__cover {
  opacity: 0.55;
}
.mm-cart__check {
  display: grid;
  place-items: center;
  min-height: 40px;
}
.mm-cart input[type='checkbox'] {
  width: 18px;
  height: 18px;
  accent-color: var(--mm-primary);
}
.mm-cart__cover {
  width: 92px;
  height: 104px;
  border-radius: 9px;
  overflow: hidden;
  background: var(--mm-canvas);
}
.mm-cart__cover :deep(img) {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.mm-cart__info {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.mm-cart__title {
  color: var(--mm-ink);
  font-weight: 600;
  font-size: 15px;
  line-height: 1.5;
  overflow-wrap: anywhere;
}
.mm-cart__title:hover {
  text-decoration: underline;
  text-underline-offset: 3px;
}
.mm-cart__meta {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.mm-cart__unit {
  font-size: 14px;
}
.mm-cart__unit > span:last-child {
  font-size: 12px;
  color: var(--mm-muted);
}
.mm-cart__row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.mm-cart__stepper {
  display: flex;
  align-items: center;
  border: 1px solid var(--mm-border);
  border-radius: 8px;
}
.mm-cart__stepper button {
  border: 0;
  background: transparent;
  color: var(--mm-ink);
  border-radius: 7px;
  width: 36px;
  min-height: 36px;
  font-size: 17px;
}
.mm-cart__stepper button:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.mm-cart__qty {
  min-width: 28px;
  text-align: center;
  font-variant-numeric: tabular-nums;
  font-size: 14px;
}
.mm-cart__stock {
  font-size: 12px;
  color: var(--mm-muted);
}
.mm-cart__remove {
  border: 0;
  background: transparent;
  font-size: 13px;
  color: var(--mm-muted);
  text-decoration: underline;
  text-underline-offset: 4px;
  min-height: 36px;
  margin-left: auto;
}
.mm-cart__remove:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.mm-cart__subtotal {
  display: flex;
  flex-direction: column;
  gap: 6px;
  text-align: right;
  white-space: nowrap;
  padding-top: 3px;
  font-size: 16px;
}
.mm-cart__subtotal small {
  font-size: 11px;
  color: var(--mm-muted);
}
.mm-cart__invalid-hint {
  font-size: 12px;
  color: var(--mm-danger);
  line-height: 1.6;
}
.mm-cart__summary {
  margin-top: 52px;
  padding: 24px;
  background: var(--mm-white);
  border: 1px solid var(--mm-border);
  border-top: 3px solid var(--mm-ink);
  border-radius: 4px 4px 12px 12px;
  display: flex;
  flex-direction: column;
  gap: 20px;
  position: sticky;
  top: 24px;
}
.mm-cart__summary h2 {
  font-size: 18px;
}
.mm-cart__summary dl {
  font-size: 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.mm-cart__summary dl > div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}
.mm-cart__summary dt {
  color: var(--mm-muted);
}
.mm-cart__total {
  padding-top: 20px;
  border-top: 1px solid var(--mm-border);
  display: flex;
  flex-direction: column;
  gap: 10px;
  font-size: 13px;
}
.mm-cart__total :deep(.mm-price) {
  font-size: 30px;
  letter-spacing: -0.6px;
}
.mm-cart__freight-note {
  font-size: 13px;
  line-height: 1.7;
  color: var(--mm-muted);
}
.mm-cart__selection-hint {
  text-align: center;
  font-size: 12px;
  color: var(--mm-muted);
  margin-top: -10px;
}
.mm-cart__error {
  display: flex;
  align-items: center;
  gap: 16px;
  justify-content: space-between;
  padding: 16px 18px;
  background: #fff6f4;
  border: 1px solid #eac6bd;
  border-radius: 10px;
  color: var(--mm-danger);
  font-size: 14px;
  overflow-wrap: anywhere;
}
.mm-cart__error p {
  margin-top: 5px;
}
.mm-cart__feedback {
  font-size: 14px;
  color: var(--mm-success);
  padding: 10px 0;
}
.mm-cart__empty {
  min-height: 370px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: 16px;
  text-align: center;
  border-top: 1px solid var(--mm-border);
  margin-top: 6px;
}
.mm-cart__empty svg {
  color: var(--mm-ink);
  margin-bottom: 4px;
}
.mm-cart__empty h2 {
  font-size: 23px;
}
.mm-cart__empty p {
  font-size: 14px;
  color: var(--mm-muted);
  line-height: 1.7;
}
.mm-cart__empty-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 28px;
  padding: 12px 24px;
  background: var(--mm-ink);
  color: white;
  border-radius: 9px;
  margin-top: 7px;
  font-weight: 600;
}
@media (max-width: 900px) {
  .mm-cart__layout {
    grid-template-columns: minmax(0, 1fr) 260px;
    gap: 18px;
  }
  .mm-cart__item {
    grid-template-columns: 20px 76px minmax(0, 1fr);
    gap: 10px;
    padding: 18px 14px;
  }
  .mm-cart__cover {
    width: 76px;
    height: 88px;
  }
  .mm-cart__subtotal {
    grid-column: 3;
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
    text-align: left;
  }
  .mm-cart__summary {
    padding: 20px;
  }
}
@media (max-width: 700px) {
  .mm-cart {
    padding: 24px 16px 40px;
    gap: 18px;
  }
  .mm-cart__heading {
    font-size: 28px;
  }
  .mm-cart__layout {
    display: flex;
    flex-direction: column;
    gap: 24px;
  }
  .mm-cart__groups,
  .mm-cart__summary {
    width: 100%;
  }
  .mm-cart__summary {
    position: static;
    margin-top: 0;
    padding: 22px;
  }
  .mm-cart__summary dl {
    flex-direction: row;
    justify-content: space-between;
    flex-wrap: wrap;
    gap: 14px;
  }
  .mm-cart__summary dl > div {
    gap: 12px;
  }
  .mm-cart__total {
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
  }
  .mm-cart__seller {
    padding: 14px;
  }
  .mm-cart__seller h2 {
    display: block;
  }
  .mm-cart__seller h2 small {
    margin-right: 7px;
  }
  .mm-cart__stock {
    width: auto;
  }
  .mm-cart__header {
    gap: 8px;
  }
  .mm-cart__eyebrow {
    font-size: 12px;
  }
  .mm-cart__browse {
    font-size: 13px;
  }
  .mm-cart__error {
    align-items: flex-start;
    flex-direction: column;
  }
  .mm-cart__empty {
    min-height: 350px;
  }
  .mm-cart__stepper button {
    width: 38px;
    min-height: 38px;
  }
}
</style>
