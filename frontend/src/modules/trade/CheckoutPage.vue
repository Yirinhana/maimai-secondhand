<template>
  <div class="mm-checkout">
    <header class="mm-checkout__header">
      <div>
        <p class="mm-checkout__eyebrow">最后核对一下，让交付更顺利</p>
        <h1 class="mm-checkout__heading">确认订单</h1>
      </div>
      <RouterLink to="/cart" class="mm-checkout__back">← 返回购物车</RouterLink>
    </header>
    <ol class="mm-checkout__steps" aria-label="购买流程">
      <li>选择商品</li>
      <li aria-current="step">确认订单</li>
      <li>按子订单付款</li>
    </ol>
    <MmSkeleton
      v-if="loading"
      kind="detail"
      :count="1"
      label="正在核对结算信息"
    />
    <div v-else-if="loadError" class="mm-checkout__load-error" role="alert">
      <h2>结算信息暂未准备好</h2>
      <p>{{ loadError }}</p>
      <div>
        <MmButton @click="load">重新加载</MmButton
        ><RouterLink to="/cart">返回购物车调整</RouterLink>
      </div>
    </div>
    <form
      v-else-if="lines.length"
      class="mm-checkout__layout"
      @submit.prevent="submit"
    >
      <div class="mm-checkout__main">
        <section
          class="mm-checkout__section"
          aria-labelledby="checkout-delivery-title"
        >
          <header class="mm-checkout__section-heading">
            <span aria-hidden="true">01</span>
            <div>
              <h2 id="checkout-delivery-title">确认交付信息</h2>
              <p>核对地址，或与卖家约好面交时间。</p>
            </div>
          </header>
          <fieldset :disabled="submitting" class="mm-checkout__delivery-fields">
            <legend class="mm-visually-hidden">交付信息</legend>
            <div v-if="needsExpress" class="mm-checkout__delivery-block">
              <div class="mm-checkout__block-heading">
                <h3>收货地址</h3>
                <button
                  type="button"
                  :disabled="addressLoading || submitting"
                  @click="loadAddresses"
                >
                  {{ addressLoading ? '刷新中…' : '刷新地址' }}
                </button>
              </div>
              <p v-if="addressError" class="mm-checkout__error" role="alert">
                {{ addressError }}，请刷新重试。
              </p>
              <ul
                v-if="addresses.length"
                class="mm-checkout__addresses"
                :aria-busy="addressLoading"
              >
                <li v-for="addr in addresses" :key="addr.id">
                  <label
                    class="mm-checkout__address"
                    :class="{ 'is-selected': addressId === addr.id }"
                  >
                    <input
                      v-model="addressId"
                      type="radio"
                      name="address"
                      :value="addr.id"
                    />
                    <span class="mm-checkout__address-text"
                      ><span class="mm-checkout__receiver"
                        ><strong>{{ addr.receiver }}</strong
                        ><span>{{ addr.phone }}</span
                        ><MmTag
                          v-if="addr.isDefault"
                          text="默认"
                          tone="primary" /></span
                      ><span>{{ addr.region }} {{ addr.detail }}</span></span
                    >
                  </label>
                </li>
              </ul>
              <p
                v-else-if="!addressLoading && !addressError"
                class="mm-checkout__hint"
              >
                还没有收货地址。先添加地址，再回到这里刷新即可。
              </p>
              <RouterLink
                to="/me?tab=addresses"
                target="_blank"
                rel="noopener"
                class="mm-checkout__link"
                >管理收货地址 <span>（新窗口）↗</span></RouterLink
              >
            </div>
            <div v-if="needsMeetup" class="mm-checkout__delivery-block">
              <h3>面交约定</h3>
              <p class="mm-checkout__hint">
                选择便于双方到达、可当面检查物品的公共地点。
              </p>
              <div class="mm-checkout__meetup">
                <MmInput
                  v-model="meetupLocation"
                  label="面交地点"
                  placeholder="例如：地铁站出口旁的公共广场"
                  :maxlength="200"
                  :error="fieldErrors.meetupLocation"
                />
                <MmInput
                  v-model="meetupTime"
                  label="面交时间"
                  type="datetime-local"
                  :error="fieldErrors.meetupTime"
                  hint="请先与卖家约好时间；后续调整由卖家在订单中更新"
                />
                <MapPicker @select="meetupLocation = $event.fullAddress" />
              </div>
            </div>
          </fieldset>
        </section>

        <section
          class="mm-checkout__section"
          aria-labelledby="checkout-items-title"
        >
          <header class="mm-checkout__section-heading">
            <span aria-hidden="true">02</span>
            <div>
              <h2 id="checkout-items-title">核对商品与交付费用</h2>
              <p>
                {{ totalUnits }} 件商品，将按卖家与交付方式生成
                {{ previewGroups.length }} 个子订单。
              </p>
            </div>
          </header>
          <article
            v-for="(group, index) in previewGroups"
            :key="group.key"
            class="mm-checkout__group"
          >
            <header class="mm-checkout__group-heading">
              <div>
                <small>子订单 {{ String(index + 1).padStart(2, '0') }}</small>
                <h3>卖家：{{ group.sellerNickname }}</h3>
              </div>
              <MmTag
                :text="DELIVERY_METHOD_TEXT[group.deliveryMethod]"
                tone="primary"
              />
            </header>
            <ul class="mm-checkout__lines">
              <li
                v-for="line in group.lines"
                :key="line.key"
                class="mm-checkout__line"
              >
                <ItemImage
                  :src="line.coverImage"
                  :alt="line.title"
                  class="mm-checkout__line-cover"
                />
                <div class="mm-checkout__line-info">
                  <span class="mm-checkout__line-title">{{ line.title }}</span
                  ><span v-if="line.bargainId" class="mm-checkout__line-bargain"
                    >议价成交 · 议价单 #{{ line.bargainId }}</span
                  ><span class="mm-checkout__line-price"
                    ><PriceText :cents="line.unitPriceCents" /> ×
                    {{ line.quantity }}</span
                  >
                </div>
                <PriceText
                  :cents="line.unitPriceCents * line.quantity"
                  class="mm-checkout__line-total"
                />
              </li>
            </ul>
            <dl class="mm-checkout__amounts">
              <div>
                <dt>商品款</dt>
                <dd><PriceText :cents="group.goodsCents" /></dd>
              </div>
              <div>
                <dt>
                  {{ group.deliveryMethod === 'EXPRESS' ? '运费' : '面交运费'
                  }}<small v-if="group.deliveryMethod === 'EXPRESS'"
                    >同组取最高，多件不叠加</small
                  >
                </dt>
                <dd><PriceText :cents="group.freightCents" /></dd>
              </div>
              <div>
                <dt>卖家承担平台服务费<small>预估，不计入买家应付</small></dt>
                <dd><PriceText :cents="group.feeCents" /></dd>
              </div>
              <div class="mm-checkout__amounts-total">
                <dt>子订单合计</dt>
                <dd>
                  <PriceText :cents="group.goodsCents + group.freightCents" />
                </dd>
              </div>
            </dl>
          </article>
        </section>
      </div>
      <aside
        class="mm-checkout__summary"
        aria-labelledby="checkout-summary-title"
      >
        <h2 id="checkout-summary-title">订单合计</h2>
        <p class="mm-checkout__hint">
          {{ totalUnits }} 件商品 · {{ previewGroups.length }} 个子订单
        </p>
        <dl class="mm-checkout__amounts">
          <div>
            <dt>商品款</dt>
            <dd><PriceText :cents="totalGoodsCents" /></dd>
          </div>
          <div>
            <dt>运费</dt>
            <dd><PriceText :cents="totalFreightCents" /></dd>
          </div>
        </dl>
        <div class="mm-checkout__grand">
          <span>应付合计</span><PriceText :cents="totalPayCents" />
        </div>
        <p class="mm-checkout__split-note">
          提交后进入付款步骤；不同子订单分别付款。
        </p>
        <p v-if="submissionHint" class="mm-checkout__submission-hint">
          {{ submissionHint }}
        </p>
        <div v-if="error" class="mm-checkout__submit-error" role="alert">
          <strong>订单尚未提交成功</strong>
          <p>{{ error }}</p>
        </div>
        <MmButton type="submit" :loading="submitting" :disabled="!canSubmit"
          >提交订单</MmButton
        >
        <p class="mm-checkout__note">
          提交时会再次核对价格和库存。若任一商品库存不足，本次整批订单不会提交，请调整后重试。
        </p>
        <p v-if="submitting" class="mm-visually-hidden" role="status">
          正在提交订单，请稍候，无需重复点击。
        </p>
      </aside>
    </form>
    <EmptyState
      v-else
      title="没有待结算的商品"
      description="从购物车勾选商品，或在商品详情页直接购买"
      ><RouterLink to="/cart" class="mm-checkout__empty-link"
        >去购物车 →</RouterLink
      ></EmptyState
    >
  </div>
</template>

<script setup lang="ts">
import ItemImage from '../../shared/components/ItemImage.vue';
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { get, post, type ApiError } from '../../shared/api';
import {
  DELIVERY_METHOD_TEXT,
  type Address,
  type Bargain,
  type CartItem,
  type CheckoutItem,
  type CheckoutResponse,
  type DeliveryMethod,
  type ProductDetail,
} from '../../shared/types';
import MmButton from '../../shared/components/MmButton.vue';
import MmSkeleton from '../../shared/components/MmSkeleton.vue';
import MmInput from '../../shared/components/MmInput.vue';
import MmTag from '../../shared/components/MmTag.vue';
import EmptyState from '../../shared/components/EmptyState.vue';
import PriceText from '../../shared/components/PriceText.vue';
import MapPicker from '../../shared/components/MapPicker.vue';

/** 结算预览行：购物车勾选或详情直购统一成此结构 */
interface PreviewLine {
  key: string;
  productId: number;
  title: string;
  coverImage: string | null;
  unitPriceCents: number;
  quantity: number;
  deliveryMethod: DeliveryMethod;
  sellerId: number;
  sellerNickname: string;
  freightCents: number;
  bargainId?: number;
}

interface PreviewGroup {
  key: string;
  sellerNickname: string;
  deliveryMethod: DeliveryMethod;
  lines: PreviewLine[];
  goodsCents: number;
  freightCents: number;
  feeCents: number;
}

/** 直购入口传入的条目（query.items JSON 或 history.state.items） */
interface DirectItem {
  productId: number;
  quantity: number;
  deliveryMethod: DeliveryMethod;
  bargainId?: number;
  priceCents?: number;
}

const route = useRoute();
const router = useRouter();

const lines = ref<PreviewLine[]>([]);
const cartItemIds = ref<number[]>([]);
const loading = ref(true);
const error = ref('');
const loadError = ref('');

const addresses = ref<Address[]>([]);
const addressError = ref('');
const addressLoading = ref(false);
const addressId = ref<number | null>(null);
const meetupLocation = ref('');
const meetupTime = ref('');
const fieldErrors = reactive({ meetupLocation: '', meetupTime: '' });

const submitting = ref(false);
/** 幂等键在进入页面时生成一次：网络重试复用同一键，后端幂等返回原批次 */
const idempotencyKey = crypto.randomUUID();

const needsExpress = computed(() =>
  lines.value.some((l) => l.deliveryMethod === 'EXPRESS'),
);
const needsMeetup = computed(() =>
  lines.value.some((l) => l.deliveryMethod === 'MEETUP'),
);

const previewGroups = computed<PreviewGroup[]>(() => {
  const map = new Map<string, PreviewGroup>();
  for (const line of lines.value) {
    const key = `${line.sellerId}|${line.deliveryMethod}`;
    let group = map.get(key);
    if (!group) {
      group = {
        key,
        sellerNickname: line.sellerNickname,
        deliveryMethod: line.deliveryMethod,
        lines: [],
        goodsCents: 0,
        freightCents: 0,
        feeCents: 0,
      };
      map.set(key, group);
    }
    group.lines.push(line);
    group.goodsCents += line.unitPriceCents * line.quantity;
    group.freightCents = Math.max(group.freightCents, line.freightCents);
  }
  // 平台服务费预估：费率万分之三，仅按商品款计（与后端 FeeCalculator 一致）
  for (const group of map.values()) {
    group.feeCents = Math.floor((group.goodsCents * 3 + 5000) / 10000);
  }
  return [...map.values()];
});

const totalGoodsCents = computed(() =>
  previewGroups.value.reduce((s, g) => s + g.goodsCents, 0),
);
const totalFreightCents = computed(() =>
  previewGroups.value.reduce((s, g) => s + g.freightCents, 0),
);
const totalPayCents = computed(
  () => totalGoodsCents.value + totalFreightCents.value,
);
const totalUnits = computed(() =>
  lines.value.reduce((sum, line) => sum + line.quantity, 0),
);
const submissionHint = computed(() => {
  if (addressLoading.value) return '正在刷新收货地址，请稍候';
  if (needsExpress.value && (addressId.value === null || addressError.value))
    return '请先选择有效的收货地址';
  if (
    needsMeetup.value &&
    (!meetupLocation.value.trim() || !meetupTimeValid.value)
  )
    return '请先填写面交地点和时间';
  return '';
});

const meetupTimeValid = computed(() => {
  if (!needsMeetup.value) return true;
  if (!meetupTime.value) return false;
  return !Number.isNaN(new Date(meetupTime.value).getTime());
});

const canSubmit = computed(() => {
  if (
    submitting.value ||
    loading.value ||
    addressLoading.value ||
    lines.value.length === 0
  )
    return false;
  if (needsExpress.value && addressError.value) return false;
  if (needsExpress.value && addressId.value === null) return false;
  if (
    needsMeetup.value &&
    (!meetupLocation.value.trim() || !meetupTimeValid.value)
  )
    return false;
  return true;
});

function parseDirectItems(): DirectItem[] | null {
  const stateItems = (history.state as { items?: DirectItem[] } | null)?.items;
  if (Array.isArray(stateItems) && stateItems.length) return stateItems;
  const raw = route.query.items;
  if (typeof raw === 'string' && raw) {
    try {
      const parsed: unknown = JSON.parse(raw);
      if (Array.isArray(parsed) && parsed.length) return parsed as DirectItem[];
    } catch {
      /* 落到下面的报错 */
    }
    throw new Error('直购参数格式不正确');
  }
  return null;
}

async function loadFromCart(ids: number[]) {
  const cart = await get<CartItem[]>('/cart');
  const wanted = cart.filter((i) => ids.includes(i.id));
  if (wanted.length !== new Set(ids).size)
    throw new Error('部分勾选商品已被移除，请返回购物车重新确认');
  const invalid = wanted.filter((i) => i.invalid);
  if (invalid.length)
    throw new Error('勾选商品中包含已失效商品，请返回购物车调整');
  cartItemIds.value = wanted.map((i) => i.id);
  const productMap = await fetchProducts(wanted.map((i) => i.productId));
  lines.value = wanted.map((i) => {
    const p = productMap.get(i.productId);
    return {
      key: `cart-${i.id}`,
      productId: i.productId,
      title: i.title ?? p?.title ?? '商品',
      coverImage: i.coverImage,
      unitPriceCents: i.priceCents,
      quantity: i.quantity,
      deliveryMethod: i.deliveryMethod,
      sellerId: i.sellerId ?? p?.seller.id ?? 0,
      sellerNickname: i.sellerNickname ?? p?.seller.nickname ?? '卖家',
      freightCents: i.deliveryMethod === 'EXPRESS' ? (p?.freightCents ?? 0) : 0,
    };
  });
}

async function loadDirect(directItems: DirectItem[]) {
  if (
    directItems.length > 100 ||
    directItems.some(
      (i) =>
        !i ||
        !Number.isSafeInteger(i.productId) ||
        i.productId <= 0 ||
        !Number.isSafeInteger(i.quantity) ||
        i.quantity <= 0 ||
        !['EXPRESS', 'MEETUP'].includes(i.deliveryMethod),
    )
  ) {
    throw new Error('购买条目不正确，请从商品详情重新选择');
  }
  const productMap = await fetchProducts(directItems.map((i) => i.productId));
  const bargains = directItems.some((i) => i.bargainId)
    ? await get<Bargain[]>('/me/bargains')
    : [];
  lines.value = directItems.map((i, idx) => {
    const p = productMap.get(i.productId);
    if (!p) throw new Error('部分商品不存在或已下架');
    const bargain = i.bargainId
      ? bargains.find(
          (b) =>
            b.id === i.bargainId &&
            b.productId === i.productId &&
            b.quantity === i.quantity &&
            b.status === 'CONFIRMED' &&
            new Date(b.expiresAt).getTime() > Date.now(),
        )
      : undefined;
    if (i.bargainId && !bargain)
      throw new Error('议价单无效或已过期，请返回我的议价确认');
    return {
      key: `direct-${idx}-${i.productId}`,
      productId: i.productId,
      title: p.title,
      coverImage: p.images[0]?.path ?? null,
      unitPriceCents: bargain
        ? (bargain.counterPriceCents ?? bargain.offerPriceCents)
        : p.priceCents,
      quantity: i.quantity,
      deliveryMethod: i.deliveryMethod,
      sellerId: p.seller.id,
      sellerNickname: p.seller.nickname,
      freightCents: i.deliveryMethod === 'EXPRESS' ? p.freightCents : 0,
      bargainId: i.bargainId,
    };
  });
}

async function fetchProducts(
  ids: number[],
): Promise<Map<number, ProductDetail>> {
  const unique = [...new Set(ids)];
  const results = await Promise.all(
    unique.map(async (id) => {
      try {
        return await get<ProductDetail>(`/products/${id}`);
      } catch {
        throw new Error(
          '暂时无法核对商品价格与运费，请重新加载；若商品已下架，请返回购物车调整',
        );
      }
    }),
  );
  const map = new Map<number, ProductDetail>();
  results.forEach((p) => {
    if (p) map.set(p.id, p);
  });
  return map;
}

async function loadAddresses() {
  if (!needsExpress.value) return;
  if (addressLoading.value) return;
  addressLoading.value = true;
  addressError.value = '';
  try {
    addresses.value = await get<Address[]>('/me/addresses');
    const def = addresses.value.find((a) => a.isDefault) ?? addresses.value[0];
    if (!addresses.value.some((address) => address.id === addressId.value))
      addressId.value = def ? def.id : null;
  } catch (e) {
    addressError.value = (e as ApiError).message;
  } finally {
    addressLoading.value = false;
  }
}

async function submit() {
  if (!canSubmit.value) return;
  fieldErrors.meetupLocation = '';
  fieldErrors.meetupTime = '';
  if (needsMeetup.value) {
    if (!meetupLocation.value.trim())
      fieldErrors.meetupLocation = '请填写面交地点';
    if (!meetupTimeValid.value) fieldErrors.meetupTime = '请选择面交时间';
    if (fieldErrors.meetupLocation || fieldErrors.meetupTime) return;
  }
  submitting.value = true;
  error.value = '';
  try {
    const items: CheckoutItem[] = lines.value.map((l) => ({
      productId: l.productId,
      quantity: l.quantity,
      deliveryMethod: l.deliveryMethod,
      bargainId: l.bargainId,
    }));
    const res = await post<CheckoutResponse>('/checkout', {
      idempotencyKey,
      items,
      addressId: needsExpress.value
        ? (addressId.value ?? undefined)
        : undefined,
      meetupLocation: needsMeetup.value
        ? meetupLocation.value.trim()
        : undefined,
      meetupTime: needsMeetup.value
        ? new Date(meetupTime.value).toISOString()
        : undefined,
      removeCartItemIds: cartItemIds.value.length
        ? cartItemIds.value
        : undefined,
    });
    if (res.orders.length === 1) {
      router.replace({
        name: 'order-detail',
        params: { orderNo: res.orders[0].orderNo },
      });
    } else {
      router.replace({ name: 'my-orders' });
    }
  } catch (e) {
    error.value = (e as ApiError).message;
  } finally {
    submitting.value = false;
  }
}

async function load() {
  loading.value = true;
  loadError.value = '';
  error.value = '';
  try {
    const direct = parseDirectItems();
    if (direct) {
      await loadDirect(direct);
    } else {
      const raw = route.query.cartItemIds;
      const ids =
        typeof raw === 'string' && raw
          ? raw
              .split(',')
              .map((s) => Number(s))
              .filter((n) => Number.isInteger(n) && n > 0)
          : [];
      if (ids.length === 0) {
        lines.value = [];
        return;
      }
      await loadFromCart(ids);
    }
    await loadAddresses();
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : (e as ApiError).message;
    lines.value = [];
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.mm-checkout {
  max-width: 1160px;
  margin: 0 auto;
  padding: 32px 24px 56px;
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 24px;
}
.mm-checkout__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
}
.mm-checkout__eyebrow {
  font-size: 13px;
  color: var(--mm-muted);
  margin-bottom: 8px;
}
.mm-checkout__heading {
  font-size: 32px;
  letter-spacing: -1px;
}
.mm-checkout__back {
  font-size: 14px;
  padding: 10px 0;
  white-space: nowrap;
}
.mm-checkout__steps {
  list-style: none;
  display: flex;
  gap: 28px;
  margin: 0;
  padding: 0 0 22px;
  border-bottom: 1px solid var(--mm-border);
  font-size: 13px;
  color: var(--mm-muted);
  counter-reset: step;
}
.mm-checkout__steps li {
  display: flex;
  align-items: center;
  gap: 8px;
  counter-increment: step;
}
.mm-checkout__steps li:before {
  content: counter(step);
  width: 23px;
  height: 23px;
  display: grid;
  place-items: center;
  border: 1px solid var(--mm-border);
  border-radius: 50%;
  font-size: 11px;
}
.mm-checkout__steps li[aria-current] {
  color: var(--mm-ink);
  font-weight: 600;
}
.mm-checkout__steps li[aria-current]:before {
  background: var(--mm-ink);
  color: white;
  border-color: var(--mm-ink);
}
.mm-checkout__layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 28px;
  align-items: start;
}
.mm-checkout__main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 32px;
}
.mm-checkout__section-heading {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 18px;
}
.mm-checkout__section-heading > span {
  font-size: 12px;
  line-height: 26px;
  color: var(--mm-primary);
  border-bottom: 2px solid var(--mm-primary);
}
.mm-checkout__section-heading h2 {
  font-size: 19px;
}
.mm-checkout__section-heading p {
  font-size: 13px;
  color: var(--mm-muted);
  line-height: 1.6;
  margin-top: 6px;
}
.mm-checkout__delivery-fields {
  min-width: 0;
  border: 1px solid var(--mm-border);
  border-radius: 12px;
  padding: 0;
  background: white;
}
.mm-checkout__delivery-block {
  padding: 22px;
}
.mm-checkout__delivery-block + .mm-checkout__delivery-block {
  border-top: 1px solid var(--mm-border);
}
.mm-checkout__delivery-block h3 {
  font-size: 16px;
}
.mm-checkout__block-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}
.mm-checkout__block-heading button {
  border: 0;
  background: transparent;
  font-size: 13px;
  min-height: 36px;
  color: var(--mm-primary);
  text-decoration: underline;
  text-underline-offset: 4px;
}
.mm-checkout__block-heading button:disabled {
  opacity: 0.5;
}
.mm-checkout__hint {
  font-size: 13px;
  color: var(--mm-muted);
  line-height: 1.7;
}
.mm-checkout__addresses {
  display: flex;
  flex-direction: column;
  gap: 10px;
  list-style: none;
  margin: 0;
  padding: 0;
}
.mm-checkout__address {
  display: flex;
  gap: 12px;
  padding: 16px;
  border: 1px solid var(--mm-border);
  border-radius: 9px;
  line-height: 1.65;
  font-size: 14px;
  cursor: pointer;
}
.mm-checkout__address.is-selected {
  border-color: var(--mm-primary);
  background: var(--mm-zone-soft);
}
.mm-checkout__address input {
  width: 17px;
  height: 17px;
  flex-shrink: 0;
  margin-top: 4px;
  accent-color: var(--mm-primary);
}
.mm-checkout__address-text {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
  overflow-wrap: anywhere;
}
.mm-checkout__receiver {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}
.mm-checkout__receiver > span {
  font-size: 13px;
  color: var(--mm-muted);
}
.mm-checkout__link {
  display: inline-block;
  margin-top: 14px;
  font-size: 13px;
  color: var(--mm-primary);
  padding: 4px 0;
}
.mm-checkout__link span {
  font-size: 12px;
}
.mm-checkout__meetup {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-top: 18px;
}
.mm-checkout__group {
  border: 1px solid var(--mm-border);
  border-radius: 12px;
  padding: 22px;
  background: white;
}
.mm-checkout__group + .mm-checkout__group {
  margin-top: 16px;
}
.mm-checkout__group-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}
.mm-checkout__group-heading small {
  font-size: 11px;
  color: var(--mm-muted);
}
.mm-checkout__group-heading h3 {
  font-size: 15px;
  margin-top: 5px;
  overflow-wrap: anywhere;
}
.mm-checkout__group-heading > div {
  min-width: 0;
}
.mm-checkout__group-heading :deep(.mm-tag) {
  white-space: nowrap;
}
.mm-checkout__lines {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.mm-checkout__line {
  display: grid;
  grid-template-columns: 68px minmax(0, 1fr) auto;
  align-items: center;
  gap: 14px;
}
.mm-checkout__line-cover {
  width: 68px;
  height: 78px;
  object-fit: cover;
  border-radius: 8px;
  background: var(--mm-canvas);
}
.mm-checkout__line-info {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.mm-checkout__line-title {
  font-size: 14px;
  line-height: 1.6;
  font-weight: 600;
  overflow-wrap: anywhere;
}
.mm-checkout__line-bargain {
  font-size: 12px;
  color: var(--mm-primary);
  overflow-wrap: anywhere;
}
.mm-checkout__line-price {
  font-size: 13px;
  color: var(--mm-muted);
}
.mm-checkout__line-total {
  font-size: 15px;
  white-space: nowrap;
}
.mm-checkout__amounts {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 20px;
  border-top: 1px solid var(--mm-border);
  padding-top: 18px;
}
.mm-checkout__amounts > div {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  font-size: 13px;
}
.mm-checkout__amounts dt {
  color: var(--mm-muted);
  min-width: 0;
  line-height: 1.6;
}
.mm-checkout__amounts dd {
  margin: 0;
  white-space: nowrap;
}
.mm-checkout__amounts small {
  display: block;
  font-size: 11px;
}
.mm-checkout__amounts-total {
  padding-top: 12px;
  border-top: 1px dashed var(--mm-border);
  font-weight: 600;
}
.mm-checkout__amounts-total dt {
  color: var(--mm-ink);
}
.mm-checkout__summary {
  background: white;
  padding: 24px;
  border: 1px solid var(--mm-border);
  border-top: 3px solid var(--mm-ink);
  border-radius: 4px 4px 12px 12px;
  position: sticky;
  top: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.mm-checkout__summary h2 {
  font-size: 19px;
}
.mm-checkout__summary .mm-checkout__hint {
  margin-top: -9px;
}
.mm-checkout__summary .mm-checkout__amounts {
  margin: 0;
}
.mm-checkout__grand {
  display: flex;
  flex-direction: column;
  gap: 8px;
  border-top: 1px solid var(--mm-border);
  padding-top: 18px;
  font-size: 13px;
}
.mm-checkout__grand :deep(.mm-price) {
  font-size: 32px;
  letter-spacing: -0.7px;
}
.mm-checkout__split-note {
  font-size: 13px;
  line-height: 1.7;
  color: var(--mm-ink);
}
.mm-checkout__note {
  font-size: 12px;
  line-height: 1.7;
  color: var(--mm-muted);
}
.mm-checkout__submission-hint {
  font-size: 13px;
  color: var(--mm-primary);
  line-height: 1.6;
  padding: 10px 12px;
  background: var(--mm-accent-soft);
  border-radius: 8px;
}
.mm-checkout__error,
.mm-checkout__submit-error {
  font-size: 13px;
  color: var(--mm-danger);
  overflow-wrap: anywhere;
  line-height: 1.7;
}
.mm-checkout__submit-error {
  background: #fff5f2;
  border: 1px solid #eac6bd;
  border-radius: 8px;
  padding: 12px;
}
.mm-checkout__submit-error p {
  margin-top: 5px;
}
.mm-checkout__load-error {
  padding: 32px;
  border: 1px solid var(--mm-border);
  border-radius: 12px;
  background: white;
  display: flex;
  flex-direction: column;
  gap: 18px;
  align-items: flex-start;
}
.mm-checkout__load-error h2 {
  font-size: 20px;
}
.mm-checkout__load-error p {
  line-height: 1.7;
  overflow-wrap: anywhere;
}
.mm-checkout__load-error > div {
  display: flex;
  gap: 20px;
  align-items: center;
  flex-wrap: wrap;
}
.mm-checkout__load-error a {
  font-size: 14px;
  text-decoration: underline;
}
.mm-checkout__empty-link {
  display: inline-flex;
  padding: 12px 22px;
  border-radius: 9px;
  background: var(--mm-ink);
  color: white;
}
@media (max-width: 850px) {
  .mm-checkout__layout {
    grid-template-columns: minmax(0, 1fr) 260px;
    gap: 18px;
  }
  .mm-checkout__group,
  .mm-checkout__delivery-block,
  .mm-checkout__summary {
    padding: 18px;
  }
  .mm-checkout__line {
    grid-template-columns: 58px minmax(0, 1fr);
    gap: 10px;
  }
  .mm-checkout__line-cover {
    width: 58px;
    height: 68px;
  }
  .mm-checkout__line-total {
    grid-column: 2;
  }
}
@media (max-width: 700px) {
  .mm-checkout {
    padding: 24px 16px 40px;
    gap: 22px;
  }
  .mm-checkout__heading {
    font-size: 28px;
  }
  .mm-checkout__header {
    flex-wrap: wrap;
    gap: 10px;
  }
  .mm-checkout__eyebrow {
    font-size: 12px;
  }
  .mm-checkout__back {
    font-size: 13px;
  }
  .mm-checkout__steps {
    gap: 14px;
    font-size: 11px;
    justify-content: space-between;
  }
  .mm-checkout__steps li {
    gap: 6px;
  }
  .mm-checkout__steps li:before {
    width: 20px;
    height: 20px;
  }
  .mm-checkout__layout {
    display: flex;
    flex-direction: column;
    gap: 26px;
  }
  .mm-checkout__main,
  .mm-checkout__summary {
    width: 100%;
  }
  .mm-checkout__summary {
    position: static;
    padding: 22px;
  }
  .mm-checkout__address {
    padding: 12px;
  }
  .mm-checkout__grand {
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
  }
  .mm-checkout__group-heading {
    gap: 10px;
  }
  .mm-checkout__group-heading h3 {
    font-size: 14px;
  }
  .mm-checkout__amounts > div {
    gap: 12px;
  }
  .mm-checkout__load-error {
    padding: 24px;
  }
}
</style>
