<template>
  <div class="mm-detail">
    <MmSkeleton
      v-if="loading"
      kind="detail"
      :count="1"
      label="正在加载商品详情"
    />
    <EmptyState
      v-else-if="error"
      title="暂时无法显示这件商品"
      :description="error"
      ><div class="mm-detail__recovery">
        <MmButton @click="reload">重试</MmButton
        ><RouterLink to="/search">返回发现好物</RouterLink>
      </div></EmptyState
    >
    <template v-else-if="product">
      <p
        v-if="product.status !== 'ON_SALE'"
        class="mm-detail__status-banner"
        role="status"
      >
        该商品当前{{
          PRODUCT_STATUS_TEXT[product.status]
        }}，暂不可购买。可以继续查看已公开的信息。
      </p>
      <div class="mm-detail__layout">
        <ProductGallery
          :key="product.id"
          :images="product.images"
          :title="product.title"
        />
        <section class="mm-detail__info" aria-labelledby="product-title">
          <div class="mm-detail__meta">
            <MmTag
              :text="CONDITION_TEXT[product.condition]"
              tone="neutral"
            /><span>{{ product.region || '地区未填写' }}</span>
          </div>
          <h1 id="product-title" class="mm-detail__title">
            {{ product.title }}
          </h1>
          <p class="mm-detail__time">
            发布于 {{ formatTime(product.createdAt) }}
          </p>
          <div class="mm-detail__price-row">
            <div>
              <span class="mm-detail__price-label">单件售价</span
              ><PriceText
                class="mm-detail__price"
                :cents="product.priceCents"
              />
            </div>
            <span class="mm-detail__stock">{{
              product.stockAvailable > 0
                ? `库存 ${product.stockAvailable} 件`
                : isDemoPreview
                  ? '展示商品'
                  : '已售罄'
            }}</span>
          </div>
          <div class="mm-detail__defects">
            <h2>成色与缺陷说明</h2>
            <p>
              {{
                product.defects ||
                '卖家未填写缺陷说明，请结合图片与描述判断，必要时先与卖家沟通。'
              }}
            </p>
          </div>
          <div class="mm-detail__delivery">
            <h2>交付与运费</h2>
            <dl>
              <template v-if="product.deliveryMethods.includes('EXPRESS')"
                ><dt>快递</dt>
                <dd>
                  <strong>{{
                    product.freightCents > 0
                      ? `运费 ${formatPrice(product.freightCents)}`
                      : '免运费'
                  }}</strong
                  ><span>运费下单时一并结算</span>
                </dd>
                <dt>配送范围</dt>
                <dd>
                  {{
                    product.shippingProvinces?.length
                      ? product.shippingProvinces.join('、')
                      : '全国'
                  }}
                </dd></template
              >
              <template v-if="product.deliveryMethods.includes('MEETUP')"
                ><dt>同城面交</dt>
                <dd>与卖家约定时间地点，当面交付</dd></template
              >
            </dl>
          </div>
          <div v-if="product.returnPromise" class="mm-detail__return">
            <h2>退货承诺</h2>
            <p>{{ product.returnPromise }}</p>
          </div>

          <div v-if="isOwner" class="mm-detail__owner">
            <strong>这是你发布的商品</strong>
            <p>可以前往卖家工作台管理商品、库存和交易。</p>
            <RouterLink :to="`/publish/${product.id}`">编辑商品 →</RouterLink>
          </div>
          <div
            v-else-if="product.status === 'ON_SALE'"
            class="mm-detail__actions"
          >
            <p
              v-if="product.stockAvailable <= 0"
              class="mm-detail__unavailable"
            >
              {{
                isDemoPreview
                  ? '这件商品仅用于页面展示，不接受购买或议价。'
                  : '这件商品已售罄，暂不能购买或议价。'
              }}<RouterLink to="/search">看看其他闲置</RouterLink>
            </p>
            <template v-else>
              <div class="mm-detail__controls">
                <label class="mm-detail__control"
                  ><span>数量</span
                  ><input
                    v-model.number="quantity"
                    type="number"
                    min="1"
                    step="1"
                    :max="product.stockAvailable"
                    :disabled="cartLoading" /></label
                ><label
                  v-if="product.deliveryMethods.length > 1"
                  class="mm-detail__control mm-detail__control--delivery"
                  ><span>交付方式</span
                  ><select v-model="deliveryMethod" :disabled="cartLoading">
                    <option
                      v-for="method in product.deliveryMethods"
                      :key="method"
                      :value="method"
                    >
                      {{ DELIVERY_METHOD_TEXT[method] }}
                    </option>
                  </select></label
                >
              </div>
              <p v-if="!auth.me" class="mm-detail__login-hint">
                <RouterLink :to="loginTarget">登录</RouterLink
                >后可购买、加入购物车或议价。
              </p>
              <p
                v-if="actionError"
                class="mm-detail__action-error"
                role="alert"
              >
                {{ actionError }}
              </p>
              <div
                v-if="actionMessage"
                class="mm-detail__feedback"
                role="status"
              >
                <strong>{{ actionMessage }}</strong
                ><RouterLink v-if="actionKind === 'cart'" to="/cart"
                  >去购物车查看 →</RouterLink
                ><RouterLink
                  v-else-if="actionKind === 'bargain'"
                  to="/me/bargains"
                  >查看我的议价 →</RouterLink
                >
              </div>
              <div class="mm-detail__buttons">
                <MmButton
                  :disabled="cartLoading || bargainLoading"
                  @click="buyNow"
                  >立即购买</MmButton
                ><MmButton
                  variant="ghost"
                  :loading="cartLoading"
                  :disabled="bargainLoading"
                  @click="addToCart"
                  >加入购物车</MmButton
                >
              </div>
              <div class="mm-detail__bargain-row">
                <span>价格还想再商量？</span
                ><MmButton
                  variant="ghost"
                  :disabled="cartLoading || bargainLoading"
                  @click="openBargain"
                  >议价</MmButton
                >
              </div>
            </template>
          </div>
        </section>
      </div>

      <div class="mm-detail__about">
        <section
          class="mm-detail__description-section"
          aria-labelledby="product-description-title"
        >
          <p class="mm-eyebrow">ABOUT THIS ITEM</p>
          <h2 id="product-description-title">商品描述</h2>
          <p class="mm-detail__description">
            {{ product.description || '卖家还没有填写描述。' }}
          </p>
        </section>
        <aside class="mm-seller" aria-label="卖家信息">
          <p class="mm-seller__label">来自这位卖家</p>
          <p class="mm-seller__name">{{ product.seller.nickname }}</p>
          <p class="mm-seller__hint">
            看看 ta 的更多在售商品，也可以先聊聊商品细节。
          </p>
          <RouterLink
            class="mm-seller__link"
            :to="`/sellers/${product.seller.id}`"
            >查看卖家主页</RouterLink
          >
          <div class="mm-seller__social">
            <ProductSocialActions
              :key="product.id"
              :product-id="product.id"
              :seller-id="product.seller.id"
            />
          </div>
        </aside>
      </div>
    </template>

    <CatalogDialog
      v-model:open="bargainOpen"
      id="mm-bargain"
      title="向卖家议价"
    >
      <p class="mm-bargain__note">
        议价 24 小时内有效，卖家同意后仅你本人可按议价下单。
      </p>
      <p v-if="product" class="mm-bargain__item">
        {{ product.title
        }}<span>当前单价 {{ formatPrice(product.priceCents) }}</span>
      </p>
      <form novalidate @submit.prevent="submitBargain">
        <label class="mm-bargain__field"
          ><span>数量</span
          ><input
            v-model.number="bargainQuantity"
            type="number"
            min="1"
            step="1"
            :max="product?.stockAvailable"
            :disabled="bargainLoading"
            required /></label
        ><label class="mm-bargain__field"
          ><span>你的报价（元/件）</span
          ><input
            v-model="bargainPrice"
            type="text"
            min="0.01"
            step="0.01"
            inputmode="decimal"
            placeholder="输入期望单价"
            :disabled="bargainLoading"
            required
            autofocus
        /></label>
        <p v-if="bargainError" class="mm-detail__action-error" role="alert">
          {{ bargainError }}
        </p>
        <p v-if="bargainLoading" class="mm-bargain__note" role="status">
          正在提交，请稍候。关闭窗口不会撤销已提交的请求。
        </p>
        <div class="mm-bargain__buttons">
          <MmButton type="submit" :loading="bargainLoading">提交议价</MmButton
          ><MmButton variant="ghost" @click="bargainOpen = false"
            >取消</MmButton
          >
        </div>
      </form>
    </CatalogDialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onScopeDispose, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import ProductSocialActions from '../community/ProductSocialActions.vue';
import ProductGallery from './components/ProductGallery.vue';
import CatalogDialog from './components/CatalogDialog.vue';
import { get, post, type ApiError } from '../../shared/api';
import EmptyState from '../../shared/components/EmptyState.vue';
import MmButton from '../../shared/components/MmButton.vue';
import MmSkeleton from '../../shared/components/MmSkeleton.vue';
import MmTag from '../../shared/components/MmTag.vue';
import PriceText from '../../shared/components/PriceText.vue';
import { useAuthStore } from '../../shared/stores/auth';
import { formatPrice, formatTime } from '../../shared/format';
import { isDemoProductImage } from '../../shared/demoImages';
import {
  CONDITION_TEXT,
  DELIVERY_METHOD_TEXT,
  PRODUCT_STATUS_TEXT,
  type CheckoutItem,
  type DeliveryMethod,
  type ProductDetail,
} from '../../shared/types';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const product = ref<ProductDetail | null>(null);
const isDemoPreview = computed(
  () =>
    !!product.value &&
    product.value.stockAvailable === 0 &&
    product.value.images.some((image) => isDemoProductImage(image.path)),
);
const loading = ref(true);
const error = ref('');
const quantity = ref(1);
const deliveryMethod = ref<DeliveryMethod>('EXPRESS');
const actionMessage = ref('');
const actionError = ref('');
const actionKind = ref<'cart' | 'bargain' | null>(null);
const cartLoading = ref(false);
const bargainOpen = ref(false);
const bargainQuantity = ref(1);
const bargainPrice = ref('');
const bargainLoading = ref(false);
const bargainError = ref('');
let productGeneration = 0;
let actionGeneration = 0;
const isOwner = computed(
  () => !!product.value && auth.me?.id === product.value.seller.id,
);
const loginTarget = computed(() => ({
  path: '/login',
  query: { redirect: route.fullPath },
}));
function resetActions() {
  actionGeneration++;
  actionMessage.value = '';
  actionError.value = '';
  actionKind.value = null;
  cartLoading.value = false;
  bargainOpen.value = false;
  bargainLoading.value = false;
  bargainError.value = '';
  bargainPrice.value = '';
  bargainQuantity.value = 1;
}
async function load(id: string) {
  const generation = ++productGeneration;
  resetActions();
  loading.value = true;
  error.value = '';
  product.value = null;
  try {
    const data = await get<ProductDetail>(
      `/products/${encodeURIComponent(id)}`,
    );
    if (generation !== productGeneration) return;
    product.value = data;
    quantity.value = data.stockAvailable > 0 ? 1 : 0;
    deliveryMethod.value = data.deliveryMethods[0] ?? 'EXPRESS';
  } catch (cause) {
    if (generation === productGeneration)
      error.value = (cause as ApiError).message || '加载失败，请稍后重试';
  } finally {
    if (generation === productGeneration) loading.value = false;
  }
}
function reload() {
  if (typeof route.params.id === 'string') void load(route.params.id);
}
watch(
  () => route.params.id,
  (id) => {
    if (typeof id === 'string') void load(id);
  },
  { immediate: true },
);
watch(() => auth.me?.id, resetActions);
onScopeDispose(() => {
  productGeneration++;
  actionGeneration++;
});
function canTrade() {
  if (
    !product.value ||
    product.value.status !== 'ON_SALE' ||
    product.value.stockAvailable <= 0
  ) {
    actionError.value = '这件商品目前暂不可购买，请刷新查看最新状态。';
    return false;
  }
  if (isOwner.value) {
    actionError.value = '这是你自己的商品，请前往卖家工作台管理。';
    return false;
  }
  return true;
}
function validQuantity(value: number) {
  return (
    !!product.value &&
    Number.isInteger(value) &&
    value >= 1 &&
    value <= product.value.stockAvailable
  );
}
function checkoutItem(): CheckoutItem | null {
  if (!canTrade() || !product.value) return null;
  if (!validQuantity(quantity.value)) {
    actionError.value = `数量请输入 1 到 ${product.value.stockAvailable} 之间的整数。`;
    return null;
  }
  if (!product.value.deliveryMethods.includes(deliveryMethod.value)) {
    actionError.value = '请选择该商品支持的交付方式。';
    return null;
  }
  return {
    productId: product.value.id,
    quantity: quantity.value,
    deliveryMethod: deliveryMethod.value,
  };
}
function requireLogin() {
  if (auth.me) return true;
  void router.push(loginTarget.value);
  return false;
}
function buyNow() {
  if (cartLoading.value || bargainLoading.value) return;
  actionError.value = '';
  const item = checkoutItem();
  if (item)
    void router.push({
      path: '/checkout',
      query: { items: JSON.stringify([item]) },
    });
}
async function addToCart() {
  if (cartLoading.value || bargainLoading.value) return;
  actionError.value = '';
  const item = checkoutItem();
  if (!item || !requireLogin()) return;
  const generation = actionGeneration;
  cartLoading.value = true;
  actionMessage.value = '';
  actionKind.value = null;
  try {
    await post('/cart', item);
    if (generation !== actionGeneration) return;
    actionMessage.value = '已加入购物车';
    actionKind.value = 'cart';
  } catch (cause) {
    if (generation === actionGeneration)
      actionError.value =
        (cause as ApiError).message || '加入购物车失败，请重试。';
  } finally {
    if (generation === actionGeneration) cartLoading.value = false;
  }
}
function openBargain() {
  if (
    bargainLoading.value ||
    cartLoading.value ||
    !canTrade() ||
    !requireLogin()
  )
    return;
  bargainQuantity.value = validQuantity(quantity.value) ? quantity.value : 1;
  bargainPrice.value = '';
  bargainError.value = '';
  bargainOpen.value = true;
}
async function submitBargain() {
  if (bargainLoading.value || !product.value || !canTrade()) return;
  const priceText = bargainPrice.value.trim();
  const offerPriceCents = Math.round(Number(priceText) * 100);
  if (
    !/^\d+(\.\d{1,2})?$/.test(priceText) ||
    !Number.isSafeInteger(offerPriceCents) ||
    offerPriceCents <= 0
  ) {
    bargainError.value = '请输入大于 0 的报价，最多保留两位小数。';
    return;
  }
  if (!validQuantity(bargainQuantity.value)) {
    bargainError.value = `数量请输入 1 到 ${product.value.stockAvailable} 之间的整数。`;
    return;
  }
  const generation = actionGeneration;
  const id = product.value.id;
  bargainLoading.value = true;
  bargainError.value = '';
  actionMessage.value = '';
  actionKind.value = null;
  try {
    await post(`/products/${id}/bargains`, {
      quantity: bargainQuantity.value,
      offerPriceCents,
    });
    if (generation !== actionGeneration) return;
    bargainOpen.value = false;
    actionMessage.value = '议价已提交，24 小时内有效';
    actionKind.value = 'bargain';
    actionError.value = '';
  } catch (cause) {
    if (generation === actionGeneration) {
      bargainError.value =
        (cause as ApiError).message || '议价提交失败，请重试。';
      if (!bargainOpen.value) actionError.value = bargainError.value;
    }
  } finally {
    if (generation === actionGeneration) bargainLoading.value = false;
  }
}
</script>

<style scoped>
.mm-detail {
  max-width: 1240px;
  margin: 0 auto;
  padding: 24px 24px 64px;
}
.mm-detail__layout {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(0, 1fr);
  gap: 56px;
  margin-bottom: 48px;
  align-items: start;
}
.mm-detail__info {
  min-width: 0;
}
.mm-detail__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-bottom: 13px;
  font-size: 13px;
  color: var(--mm-muted);
}
.mm-detail__title {
  font-size: 29px;
  letter-spacing: -0.6px;
  line-height: 1.45;
  overflow-wrap: anywhere;
  margin-bottom: 10px;
}
.mm-detail__time {
  font-size: 12px;
  color: var(--mm-muted);
}
.mm-detail__price-row {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 18px;
  border-bottom: 1px solid var(--mm-border);
  padding: 23px 0;
  margin-bottom: 22px;
}
.mm-detail__price-row > div {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
}
.mm-detail__price-label {
  font-size: 12px;
  color: var(--mm-muted);
}
.mm-detail__price {
  font-size: 38px;
  color: var(--mm-ink);
  line-height: 1.15;
  letter-spacing: -1px;
}
.mm-detail__stock {
  padding-bottom: 3px;
  font-size: 13px;
  color: var(--mm-muted);
  white-space: nowrap;
}
.mm-detail__info h2 {
  font-size: 14px;
  margin-bottom: 8px;
}
.mm-detail__defects {
  padding: 16px 18px;
  border-left: 3px solid #c87848;
  background: #f7f0e7;
  margin-bottom: 23px;
  border-radius: 0 7px 7px 0;
}
.mm-detail__defects p,
.mm-detail__return p {
  font-size: 14px;
  line-height: 1.85;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.mm-detail__delivery {
  margin-bottom: 22px;
}
.mm-detail__delivery dl {
  display: grid;
  grid-template-columns: 70px minmax(0, 1fr);
  gap: 10px 16px;
  font-size: 13px;
  line-height: 1.7;
}
.mm-detail__delivery dt {
  color: var(--mm-muted);
}
.mm-detail__delivery dd {
  margin: 0;
  overflow-wrap: anywhere;
}
.mm-detail__delivery dd strong {
  display: block;
  font-weight: 600;
}
.mm-detail__delivery dd span {
  color: var(--mm-muted);
  font-size: 12px;
}
.mm-detail__return {
  padding-top: 16px;
  border-top: 1px solid var(--mm-border);
  margin-bottom: 22px;
}
.mm-detail__actions {
  padding-top: 22px;
  border-top: 1px solid var(--mm-border);
}
.mm-detail__controls {
  display: flex;
  gap: 16px;
  margin-bottom: 18px;
}
.mm-detail__control {
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  min-width: 0;
}
.mm-detail__control--delivery {
  flex: 1;
  max-width: 240px;
}
.mm-detail__control input,
.mm-detail__control select,
.mm-bargain__field input {
  min-width: 0;
  width: 100%;
  min-height: 44px;
  padding: 10px 12px;
  border: 1px solid var(--mm-border);
  border-radius: 6px;
  background: var(--mm-white);
  font-size: 15px;
}
.mm-detail__control input {
  width: 94px;
}
.mm-detail input:focus-visible,
.mm-detail select:focus-visible {
  outline: 2px solid var(--mm-primary);
  outline-offset: 3px;
}
.mm-detail__buttons {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
.mm-detail__buttons > :deep(.mm-button) {
  min-height: 48px;
  font-size: 15px;
}
.mm-detail__bargain-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 13px;
  font-size: 12px;
  color: var(--mm-muted);
}
.mm-detail__bargain-row > :deep(.mm-button) {
  min-height: 36px;
  border-color: var(--mm-border);
  background: transparent;
  color: var(--mm-ink);
  font-size: 13px;
  padding: 0 22px;
}
.mm-detail__feedback {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 16px;
  padding: 13px 15px;
  border: 1px solid #cbdaca;
  border-radius: 7px;
  color: #35563b;
  background: #f0f5ed;
  font-size: 13px;
  line-height: 1.6;
}
.mm-detail__feedback a {
  color: #35563b;
  text-decoration: underline;
  text-underline-offset: 3px;
}
.mm-detail__action-error {
  color: var(--mm-danger);
  font-size: 13px;
  line-height: 1.7;
  margin-bottom: 14px;
}
.mm-detail__login-hint {
  font-size: 12px;
  color: var(--mm-muted);
  margin-bottom: 13px;
}
.mm-detail__login-hint a {
  text-decoration: underline;
}
.mm-detail__owner,
.mm-detail__unavailable {
  padding: 18px;
  background: #f0eee8;
  border-radius: 8px;
  font-size: 14px;
  line-height: 1.8;
}
.mm-detail__owner p {
  color: var(--mm-muted);
  margin: 5px 0 10px;
  font-size: 13px;
}
.mm-detail__owner a,
.mm-detail__unavailable a {
  font-weight: 600;
}
.mm-detail__unavailable a {
  display: block;
  margin-top: 8px;
}
.mm-detail__status-banner {
  padding: 15px 18px;
  background: #f7f0e7;
  border: 1px solid #e5d2bc;
  border-radius: 7px;
  font-size: 14px;
  line-height: 1.7;
  margin-bottom: 24px;
}
.mm-detail__about {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(0, 1fr);
  gap: 56px;
  border-top: 1px solid var(--mm-border);
  padding-top: 34px;
}
.mm-detail__description-section h2 {
  font-size: 22px;
  margin: 8px 0 22px;
}
.mm-detail__description {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font-size: 15px;
  line-height: 1.95;
}
.mm-seller {
  align-self: start;
  padding: 24px;
  border: 1px solid var(--mm-border);
  border-radius: 10px;
  background: var(--mm-white);
  min-width: 0;
}
.mm-seller__label,
.mm-seller__hint {
  color: var(--mm-muted);
  font-size: 12px;
  line-height: 1.8;
}
.mm-seller__name {
  margin: 9px 0 8px;
  font-weight: 700;
  font-size: 21px;
  overflow-wrap: anywhere;
}
.mm-seller__link {
  display: inline-block;
  margin-top: 14px;
  font-size: 13px;
  font-weight: 600;
  text-decoration: underline;
  text-underline-offset: 4px;
}
.mm-seller__social {
  border-top: 1px solid var(--mm-border);
  margin-top: 20px;
  padding-top: 12px;
}
.mm-seller__social :deep(.social-actions) {
  margin-top: 0;
}
.mm-detail__recovery {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 20px;
}
.mm-bargain__note {
  color: var(--mm-muted);
  font-size: 13px;
  line-height: 1.8;
  margin-bottom: 18px;
}
.mm-bargain__item {
  padding: 13px 14px;
  background: #f4f1eb;
  border-radius: 6px;
  margin-bottom: 20px;
  font-size: 14px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}
.mm-bargain__item span {
  display: block;
  font-size: 12px;
  color: var(--mm-muted);
  margin-top: 5px;
}
.mm-bargain__field {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 17px;
  font-size: 13px;
  font-weight: 600;
}
.mm-bargain__buttons {
  display: flex;
  gap: 10px;
  margin-top: 20px;
}
.mm-bargain__buttons > :deep(.mm-button) {
  flex: 1;
  min-height: 44px;
}
@media (max-width: 900px) {
  .mm-detail__layout {
    gap: 28px;
  }
  .mm-detail__title {
    font-size: 24px;
  }
  .mm-detail__about {
    gap: 28px;
  }
}
@media (max-width: 700px) {
  .mm-detail {
    padding: 12px 16px 44px;
  }
  .mm-detail__layout {
    grid-template-columns: minmax(0, 1fr);
    gap: 26px;
    margin-bottom: 34px;
  }
  .mm-detail__title {
    font-size: 24px;
    line-height: 1.5;
  }
  .mm-detail__price {
    font-size: 35px;
  }
  .mm-detail__price-row {
    padding: 19px 0;
  }
  .mm-detail__buttons {
    gap: 10px;
  }
  .mm-detail__buttons > :deep(.mm-button) {
    padding: 0 12px;
    min-height: 48px;
  }
  .mm-detail__about {
    grid-template-columns: minmax(0, 1fr);
    gap: 30px;
    padding-top: 26px;
  }
  .mm-detail__description-section h2 {
    font-size: 21px;
    margin-bottom: 16px;
  }
  .mm-seller {
    padding: 20px;
  }
}
</style>
