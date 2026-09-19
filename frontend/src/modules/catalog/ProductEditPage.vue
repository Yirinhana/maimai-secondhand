<template>
  <div class="mm-edit">
    <h1 class="mm-edit__heading">{{ isEdit ? '编辑商品' : '发布闲置' }}</h1>

    <p v-if="pageLoading" class="mm-edit__loading">加载中……</p>
    <div v-else-if="pageError" role="alert">
      <EmptyState title="无法编辑该商品" :description="pageError" />
    </div>

    <template v-else>
      <p
        v-if="
          isEdit &&
          product &&
          (product.status === 'ON_SALE' || product.status === 'CHANGES_REVIEW')
        "
        class="mm-edit__review-banner"
        role="alert"
      >
        关键信息（标题/描述/分类/图片/成色/缺陷/价格/交付/运费）修改后将重新审核，审核期间暂停新成交；仅调整库存不会触发重新审核。
      </p>

      <div class="mm-edit__draft-strip">
        <div>
          <strong>未完成内容自动保留</strong>
          <p>
            按账号保存在当前浏览器，回来可继续填写。完整草稿保存后可在「我的商品」管理。
          </p>
          <p
            v-if="draftNotice"
            :class="{ 'mm-edit__field-error': draftFailure }"
            role="status"
          >
            {{ draftNotice }}
          </p>
        </div>
        <MmButton
          v-if="!isEdit"
          variant="ghost"
          :disabled="savingDraft || savingSubmit"
          @click="clearForm"
          >一键清空</MmButton
        >
      </div>
      <form ref="formElement" class="mm-edit__form" novalidate @submit.prevent>
        <MmCard title="基本信息" class="mm-edit__section">
          <div class="mm-edit__fields">
            <MmInput
              id="product-title"
              v-model="form.title"
              @update:model-value="fieldErrors.title = ''"
              label="标题"
              :maxlength="120"
              placeholder="一句话说明这件闲置"
              :error="fieldErrors.title"
            />
            <label class="mm-edit__field">
              <span class="mm-edit__label">分类</span>
              <select
                v-model="form.categoryId"
                @input="fieldErrors.categoryId = ''"
                :aria-invalid="!!fieldErrors.categoryId"
                :aria-describedby="
                  fieldErrors.categoryId
                    ? 'product-categoryId-error'
                    : undefined
                "
              >
                <option value="" disabled>请选择分类</option>
                <option
                  v-for="opt in categoryOptions"
                  :key="opt.id"
                  :value="String(opt.id)"
                >
                  {{ opt.name }}
                </option>
              </select>

              <span
                v-if="fieldErrors.categoryId"
                id="product-categoryId-error"
                class="mm-edit__field-error"
                role="alert"
                >{{ fieldErrors.categoryId }}</span
              >
            </label>
            <label class="mm-edit__field">
              <span class="mm-edit__label">商品描述</span>
              <textarea
                v-model="form.description"
                rows="5"
                placeholder="品牌、入手渠道、使用情况、出手原因……"
              ></textarea>
            </label>
            <label class="mm-edit__field">
              <span class="mm-edit__label">成色</span>
              <select v-model="form.condition">
                <option
                  v-for="(text, value) in CONDITION_TEXT"
                  :key="value"
                  :value="value"
                >
                  {{ text }}
                </option>
              </select>
            </label>
            <label class="mm-edit__field">
              <span class="mm-edit__label"
                >缺陷说明（会向买家显著展示，请如实填写）</span
              >
              <textarea
                v-model="form.defects"
                rows="3"
                maxlength="500"
                placeholder="划痕、磕碰、功能异常等；无缺陷可留空"
              ></textarea>
            </label>
          </div>
        </MmCard>

        <MmCard title="价格与库存" class="mm-edit__section">
          <div class="mm-edit__fields mm-edit__fields--row">
            <label class="mm-edit__field">
              <span class="mm-edit__label">价格（元）</span>
              <input
                v-model="form.priceYuan"
                @input="fieldErrors.priceYuan = ''"
                :aria-invalid="!!fieldErrors.priceYuan"
                :aria-describedby="
                  fieldErrors.priceYuan ? 'product-priceYuan-error' : undefined
                "
                type="text"
                inputmode="decimal"
                min="0.01"
                step="0.01"
                placeholder="如 25.00"
              />
              <span
                v-if="fieldErrors.priceYuan"
                id="product-priceYuan-error"
                class="mm-edit__field-error"
                role="alert"
                >{{ fieldErrors.priceYuan }}</span
              >
            </label>
            <label v-if="!isEdit" class="mm-edit__field">
              <span class="mm-edit__label">库存</span>
              <input
                v-model="form.stock"
                @input="fieldErrors.stock = ''"
                :aria-invalid="!!fieldErrors.stock"
                :aria-describedby="
                  fieldErrors.stock ? 'product-stock-error' : undefined
                "
                type="text"
                inputmode="decimal"
                min="0"
                step="1"
              />
              <span
                v-if="fieldErrors.stock"
                id="product-stock-error"
                class="mm-edit__field-error"
                role="alert"
                >{{ fieldErrors.stock }}</span
              >
            </label>
            <label class="mm-edit__field">
              <span class="mm-edit__label">所在地区</span>
              <input
                v-model="form.region"
                @input="fieldErrors.region = ''"
                :aria-invalid="!!fieldErrors.region"
                :aria-describedby="
                  fieldErrors.region ? 'product-region-error' : undefined
                "
                type="text"
                maxlength="100"
                placeholder="如：成都市 武侯区"
              />
              <span
                v-if="fieldErrors.region"
                id="product-region-error"
                class="mm-edit__field-error"
                role="alert"
                >{{ fieldErrors.region }}</span
              >
            </label>
          </div>
        </MmCard>

        <MmCard title="交付与售后" class="mm-edit__section">
          <div class="mm-edit__fields">
            <div class="mm-stack">
              <strong>公开交接区域（选填）</strong>
              <p class="mm-edit__hint">
                用于附近商品排序。请选择公共场所，不要标记私人住址；地图距离不用于计算快递运费。
              </p>
              <MapPicker
                :key="mapResetKey"
                :initial-latitude="form.latitude"
                :initial-longitude="form.longitude"
                @select="setPublicLocation"
              />
              <p
                v-if="form.latitude !== null && form.longitude !== null"
                class="mm-edit__hint"
              >
                已选择公开位置（GCJ-02）：{{ form.latitude.toFixed(5) }}，{{
                  form.longitude.toFixed(5)
                }}
                <button
                  type="button"
                  @click="
                    form.latitude = null;
                    form.longitude = null;
                  "
                >
                  清除位置
                </button>
              </p>
            </div>
            <fieldset class="mm-edit__fieldset">
              <legend class="mm-edit__label">交付方式（可多选）</legend>
              <label class="mm-edit__checkbox">
                <input
                  v-model="form.deliveryMethods"
                  :aria-invalid="!!fieldErrors.deliveryMethods"
                  aria-describedby="product-delivery-error"
                  type="checkbox"
                  value="EXPRESS"
                />
                快递
              </label>
              <label class="mm-edit__checkbox">
                <input
                  v-model="form.deliveryMethods"
                  :aria-invalid="!!fieldErrors.deliveryMethods"
                  aria-describedby="product-delivery-error"
                  type="checkbox"
                  value="MEETUP"
                />
                面交
              </label>
              <span
                v-if="fieldErrors.deliveryMethods"
                id="product-delivery-error"
                class="mm-edit__field-error"
                role="alert"
                >{{ fieldErrors.deliveryMethods }}</span
              >
            </fieldset>
            <label class="mm-edit__field">
              <span class="mm-edit__label"
                >快递运费（元，买家下单时一并结算；填 0 为免运费）</span
              >
              <input
                v-model="form.freightYuan"
                @input="fieldErrors.freightYuan = ''"
                :aria-invalid="!!fieldErrors.freightYuan"
                :aria-describedby="
                  fieldErrors.freightYuan
                    ? 'product-freightYuan-error'
                    : undefined
                "
                type="text"
                inputmode="decimal"
                min="0"
                step="0.01"
                placeholder="如 8.00"
              />
              <span
                v-if="fieldErrors.freightYuan"
                id="product-freightYuan-error"
                class="mm-edit__field-error"
                role="alert"
                >{{ fieldErrors.freightYuan }}</span
              >
            </label>
            <fieldset class="mm-edit__fieldset">
              <legend class="mm-edit__label">快递配送地区</legend>
              <label class="mm-edit__checkbox"
                ><input
                  v-model="limitShipping"
                  :aria-invalid="!!fieldErrors.shippingProvinces"
                  aria-describedby="product-shipping-error"
                  type="checkbox"
                />仅配送到选定省份（不勾选表示全国）</label
              >
              <div v-if="limitShipping" class="mm-edit__province-list">
                <label
                  v-for="province in provinceOptions"
                  :key="province"
                  class="mm-edit__checkbox"
                  ><input
                    v-model="form.shippingProvinces"
                    type="checkbox"
                    :value="province"
                  />{{ province }}</label
                >
              </div>
              <p v-if="provinceError" class="mm-edit__field-error">
                {{ provinceError }}
              </p>
              <p
                v-if="fieldErrors.shippingProvinces"
                id="product-shipping-error"
                class="mm-edit__field-error"
              >
                {{ fieldErrors.shippingProvinces }}
              </p>
              <p v-if="limitShipping" class="mm-edit__hint">
                请至少选一项。收货地区须填写省级名称，系统将在下单时核对是否支持配送。
              </p>
            </fieldset>
            <label class="mm-edit__field">
              <span class="mm-edit__label">退货承诺（选填）</span>
              <textarea
                v-model="form.returnPromise"
                rows="2"
                maxlength="200"
                placeholder="如：签收后 48 小时内与描述不符可退货"
              ></textarea>
            </label>
          </div>
        </MmCard>

        <!-- 图片管理：仅编辑模式（新建需先保存草稿获得商品 ID） -->
        <MmCard title="商品图片" class="mm-edit__section">
          <template v-if="isEdit">
            <p class="mm-edit__hint">
              合计 1~9 张，单张 ≤5MB，仅支持 JPG / PNG，首图将作为封面；图片最多
              1600 万像素，服务端会压缩最长边。
            </p>
            <ul v-if="product?.images.length" class="mm-edit__images">
              <li
                v-for="(img, i) in product.images"
                :key="img.id"
                class="mm-edit__image"
              >
                <ItemImage
                  :src="img.path"
                  :alt="`${form.title || '商品'} 图片 ${i + 1}`"
                  loading="lazy"
                />
                <button
                  type="button"
                  class="mm-edit__image-delete"
                  :disabled="imageLoading"
                  @click="deleteImage(img.id)"
                >
                  删除
                </button>
              </li>
            </ul>
            <div class="mm-edit__upload">
              <input
                ref="fileInput"
                type="file"
                accept="image/jpeg,image/png"
                multiple
                @change="onFilesSelected"
              />
              <MmButton
                variant="ghost"
                :loading="imageLoading"
                :disabled="!pendingFiles.length"
                @click="uploadImages"
              >
                上传所选图片
              </MmButton>
            </div>
            <p v-if="imageError" class="mm-edit__field-error" role="alert">
              {{ imageError }}
            </p>
          </template>
          <p v-else class="mm-edit__hint">
            新建商品需先「保存草稿」，之后即可在本页上传图片。
          </p>
        </MmCard>

        <!-- 库存调整：编辑模式独立入口 -->
        <MmCard
          v-if="isEdit && product"
          title="库存调整"
          class="mm-edit__section"
        >
          <p class="mm-edit__hint">
            当前可售 {{ product.stockAvailable }} 件。增减库存不会触发重新审核。
          </p>
          <div class="mm-edit__stock-row">
            <label class="mm-edit__field">
              <span class="mm-edit__label">调整量（正数增加 / 负数减少）</span>
              <input
                v-model.number="stockDelta"
                type="number"
                step="1"
                placeholder="如 2 或 -1"
              />
            </label>
            <MmButton
              variant="ghost"
              :loading="stockLoading"
              @click="adjustStock"
              >调整库存</MmButton
            >
          </div>
          <p v-if="stockMessage" class="mm-edit__ok" role="status">
            {{ stockMessage }}
          </p>
          <p v-if="stockError" class="mm-edit__field-error" role="alert">
            {{ stockError }}
          </p>
        </MmCard>

        <p v-if="saveError" class="mm-edit__field-error" role="alert">
          {{ saveError }}
        </p>
        <p v-if="saveMessage" class="mm-edit__ok" role="status">
          {{ saveMessage }}
        </p>
        <div class="mm-edit__actions">
          <template v-if="!isEdit">
            <MmButton
              variant="ghost"
              :loading="savingDraft"
              @click="save(false)"
              >保存草稿</MmButton
            >
            <p class="mm-edit__hint">
              保存草稿并上传实拍图片后，即可提交审核。
            </p>
          </template>
          <template v-else>
            <MmButton
              variant="ghost"
              :loading="savingDraft"
              @click="save(false)"
              >保存修改</MmButton
            >
            <MmButton
              v-if="
                product &&
                (product.status === 'DRAFT' || product.status === 'REJECTED')
              "
              :loading="savingSubmit"
              @click="saveAndSubmit"
            >
              保存并提交审核
            </MmButton>
          </template>
          <MmButton variant="ghost" @click="router.push(previousListPath('/seller/products'))"
            >返回我的商品</MmButton
          >
        </div>
      </form>
      <ProductRevisions
        v-if="productId"
        :key="productId"
        :product-id="productId"
      />
    </template>
  </div>
</template>

<script setup lang="ts">
import ItemImage from '../../shared/components/ItemImage.vue';
import MapPicker, {
  type SelectedAddress,
} from '../../shared/components/MapPicker.vue';
import ProductRevisions from './ProductRevisions.vue';
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';
import { useAuthStore } from '../../shared/stores/auth';
import { parseYuan } from '../../shared/moneyInput';
import { askConfirmation } from '../../shared/confirm';
import { useRoute, useRouter } from 'vue-router';
import { del, get, post, put, upload, type ApiError } from '../../shared/api';
import EmptyState from '../../shared/components/EmptyState.vue';
import MmButton from '../../shared/components/MmButton.vue';
import MmCard from '../../shared/components/MmCard.vue';
import MmInput from '../../shared/components/MmInput.vue';
import {
  CONDITION_TEXT,
  type Category,
  type Condition,
  type DeliveryMethod,
  type ProductDetail,
  type ProductUpdateRequest,
  type ProductUpsertRequest,
} from '../../shared/types';

const MAX_IMAGE_BYTES = 5 * 1024 * 1024;
const MAX_IMAGES = 9;
const IMAGE_TYPES = ['image/jpeg', 'image/png'];

import { previousListPath } from '../../shared/previousListPath';
const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const formElement = ref<HTMLFormElement | null>(null);
const draftNotice = ref('');
const draftFailure = ref(false);
const mapResetKey = ref(0);
let activeDraftKey = '';
let trackDraft = false;

const productId = computed(() => {
  const id = route.params.id;
  return typeof id === 'string' ? Number(id) : null;
});
const isEdit = computed(() => productId.value !== null);

interface CategoryOption {
  id: number;
  name: string;
}

const categoryOptions = ref<CategoryOption[]>([]);
const product = ref<ProductDetail | null>(null);
const pageLoading = ref(true);
const pageError = ref('');

const emptyForm = () => ({
  title: '',
  categoryId: '',
  description: '',
  condition: 'GOOD' as Condition,
  defects: '',
  priceYuan: '',
  stock: '1',
  region: '',
  deliveryMethods: ['EXPRESS'] as DeliveryMethod[],
  freightYuan: '0',
  returnPromise: '',
  latitude: null as number | null,
  longitude: null as number | null,
  shippingProvinces: [] as string[],
});
const form = reactive(emptyForm());
const limitShipping = ref(false),
  provinceOptions = ref<string[]>([]),
  provinceError = ref('');
function setPublicLocation(address: SelectedAddress) {
  form.latitude = address.latitude;
  form.longitude = address.longitude;
  form.region = address.region;
}

const fieldErrors = reactive({
  title: '',
  categoryId: '',
  priceYuan: '',
  stock: '',
  region: '',
  deliveryMethods: '',
  freightYuan: '',
  shippingProvinces: '',
});

const savingDraft = ref(false);
const savingSubmit = ref(false);
const saveError = ref('');
const saveMessage = ref('');

const fileInput = ref<HTMLInputElement | null>(null);
const pendingFiles = ref<File[]>([]);
const imageLoading = ref(false);
const imageError = ref('');

const stockDelta = ref<number | null>(null);
const stockLoading = ref(false);
const stockError = ref('');
const stockMessage = ref('');

function flattenCategories(
  tree: Category[],
  prefix: string,
  out: CategoryOption[],
) {
  for (const c of tree) {
    out.push({ id: c.id, name: prefix + c.name });
    if (c.children?.length)
      flattenCategories(c.children, prefix + c.name + ' / ', out);
  }
}

function fillForm(p: ProductDetail) {
  form.title = p.title;
  form.description = p.description ?? '';
  form.condition = p.condition;
  form.defects = p.defects ?? '';
  form.priceYuan = (p.priceCents / 100).toFixed(2);
  form.region = p.region;
  form.deliveryMethods = [...p.deliveryMethods];
  form.freightYuan = (p.freightCents / 100).toFixed(2);
  form.returnPromise = p.returnPromise ?? '';
  form.latitude = p.latitude ?? null;
  form.longitude = p.longitude ?? null;
  form.shippingProvinces = [...(p.shippingProvinces ?? [])];
  limitShipping.value = form.shippingProvinces.length > 0;
}

async function loadProduct(preserveInput = false) {
  if (productId.value === null) return;
  const p = await get<ProductDetail>(`/seller/products/${productId.value}`);
  product.value = p;
  if (preserveInput) return;
  fillForm(p);
  form.categoryId = String(p.categoryId ?? '');
}

onMounted(async () => {
  try {
    provinceOptions.value = await get<string[]>('/shipping-provinces');
  } catch (e) {
    provinceError.value = (e as ApiError).message || '配送省份列表加载失败';
  }
  try {
    const tree = await get<Category[]>('/categories');
    const out: CategoryOption[] = [];
    flattenCategories(tree, '', out);
    categoryOptions.value = out;
  } catch (e) {
    pageError.value = (e as ApiError).message || '分类加载失败';
    pageLoading.value = false;
    return;
  }
  if (isEdit.value) {
    try {
      await loadProduct();
    } catch (e) {
      pageError.value = (e as ApiError).message || '商品加载失败';
    }
  }
  restoreDraft();
  pageLoading.value = false;
});

const yuanToCents = parseYuan;
function persistDraft(): boolean {
  if (!trackDraft || !activeDraftKey) return false;
  try {
    localStorage.setItem(
      activeDraftKey,
      JSON.stringify({ schema: 1, form, limitShipping: limitShipping.value }),
    );
    draftFailure.value = false;
    draftNotice.value = '填写内容已自动保留在本机';
    return true;
  } catch {
    draftFailure.value = true;
    draftNotice.value =
      '浏览器未能保留内容，请勿关闭页面；请检查存储空间或浏览器隐私设置。';
    return false;
  }
}
function restoreDraft() {
  activeDraftKey = auth.me
    ? `maimai:product-form:v1:${auth.me.id}:${productId.value ?? 'new'}`
    : '';
  try {
    const saved = activeDraftKey ? localStorage.getItem(activeDraftKey) : null;
    if (saved) {
      const data = JSON.parse(saved);
      if (data.schema === 1 && data.form && typeof data.form === 'object') {
        const defaults = emptyForm();
        // Read only known field shapes; browser storage is not trusted as a request payload.
        for (const key of Object.keys(defaults) as (keyof typeof defaults)[]) {
          const value = data.form[key];
          if (
            typeof defaults[key] === 'string' &&
            (typeof value === 'string' || typeof value === 'number')
          )
            Object.assign(form, { [key]: String(value) });
        }
        form.deliveryMethods = Array.isArray(data.form.deliveryMethods)
          ? data.form.deliveryMethods.filter(
              (v: unknown) => v === 'EXPRESS' || v === 'MEETUP',
            )
          : defaults.deliveryMethods;
        form.shippingProvinces = Array.isArray(data.form.shippingProvinces)
          ? data.form.shippingProvinces.filter(
              (v: unknown) => typeof v === 'string',
            )
          : [];
        form.latitude =
          typeof data.form.latitude === 'number' &&
          Math.abs(data.form.latitude) <= 90
            ? data.form.latitude
            : null;
        form.longitude =
          typeof data.form.longitude === 'number' &&
          Math.abs(data.form.longitude) <= 180
            ? data.form.longitude
            : null;
        limitShipping.value = data.limitShipping === true;
        draftNotice.value = '已恢复上次未完成的内容，请核对后继续填写';
      }
    }
  } catch {
    draftNotice.value = '上次本机草稿暂时无法读取，可以重新填写';
  }
  trackDraft = true;
}
function removeSavedDraft() {
  try {
    if (activeDraftKey) localStorage.removeItem(activeDraftKey);
  } catch {
    /* Keep form available if storage is restricted. */
  }
}
watch(
  [form, limitShipping],
  () => {
    if (trackDraft) persistDraft();
  },
  { deep: true, flush: 'sync' },
);
async function clearForm() {
  if (
    !(await askConfirmation(
      '清空当前未发布内容并重新填写？已保存到「我的商品」的商品不受影响。',
    ))
  )
    return;
  trackDraft = false;
  Object.assign(form, emptyForm());
  mapResetKey.value++;
  limitShipping.value = false;
  pendingFiles.value = [];
  if (fileInput.value) fileInput.value.value = '';
  for (const key of Object.keys(fieldErrors) as (keyof typeof fieldErrors)[])
    fieldErrors[key] = '';
  saveError.value = '';
  saveMessage.value = '';
  imageError.value = '';
  removeSavedDraft();
  draftNotice.value = '已清空，可以重新填写';
  draftFailure.value = false;
  trackDraft = true;
  await nextTick();
  formElement.value?.querySelector<HTMLInputElement>('#product-title')?.focus();
}
async function focusFirstMissing() {
  await nextTick();
  const input = formElement.value?.querySelector<HTMLElement>(
    '[aria-invalid="true"]',
  );
  input?.focus({ preventScroll: true });
  input?.scrollIntoView({
    block: 'center',
    behavior: matchMedia('(prefers-reduced-motion: reduce)').matches
      ? 'instant'
      : 'smooth',
  });
}
function validate(): boolean {
  fieldErrors.title = form.title.trim() ? '' : '请填写标题';
  fieldErrors.categoryId = categoryOptions.value.some(
    (c) => String(c.id) === form.categoryId,
  )
    ? ''
    : '请选择分类';
  const price = yuanToCents(form.priceYuan);
  fieldErrors.priceYuan =
    price !== null && price >= 1
      ? ''
      : '价格须大于 0，最多保留两位小数，例如 25.00';
  fieldErrors.stock =
    !isEdit.value &&
    (!/^\d+$/.test(String(form.stock)) || Number(form.stock) > 2147483647)
      ? '库存须为 0～2147483647 的整数，例如 1；不能填写小数或负数'
      : '';
  fieldErrors.region = form.region.trim() ? '' : '请填写所在地区';
  fieldErrors.deliveryMethods =
    form.deliveryMethods.length > 0 ? '' : '请至少选择一种交付方式';
  const freight = yuanToCents(form.freightYuan);
  fieldErrors.freightYuan =
    freight !== null ? '' : '运费须为非负金额，最多两位小数；免运费请填 0';
  fieldErrors.shippingProvinces =
    limitShipping.value && !form.shippingProvinces.length
      ? '请选择配送省份，或取消限定配送'
      : '';
  return Object.values(fieldErrors).every((e) => !e);
}

function buildBasePayload() {
  return {
    title: form.title.trim(),
    categoryId: Number(form.categoryId),
    description: form.description,
    condition: form.condition,
    defects: form.defects.trim() || null,
    priceCents: yuanToCents(form.priceYuan) ?? 0,
    region: form.region.trim(),
    deliveryMethods: form.deliveryMethods,
    freightCents: yuanToCents(form.freightYuan) ?? 0,
    returnPromise: form.returnPromise.trim() || null,
    latitude: form.latitude,
    longitude: form.longitude,
    shippingProvinces: limitShipping.value ? [...form.shippingProvinces] : [],
  };
}

async function save(submit: boolean) {
  if (savingDraft.value || savingSubmit.value) return;
  const retained = persistDraft();
  if (!validate()) {
    saveError.value = '';
    saveMessage.value =
      !submit && retained
        ? '草稿已保留，已定位到首个待补充项目。下次进入可继续填写。'
        : '';
    await focusFirstMissing();
    return;
  }
  saveError.value = '';
  saveMessage.value = '';
  if (submit) savingSubmit.value = true;
  else savingDraft.value = true;
  try {
    if (!isEdit.value) {
      const payload: ProductUpsertRequest = {
        ...buildBasePayload(),
        stock: Number(form.stock),
        submit,
      };
      const res = await post<{ id: number }>('/seller/products', payload);
      trackDraft = false;
      removeSavedDraft();
      if (submit) {
        saveMessage.value = '已提交审核，可在「我的商品」查看进度';
        router.replace(`/seller/products`);
      } else {
        // 保存草稿后进入编辑模式，可继续上传图片
        router.replace(`/publish/${res.id}`);
      }
    } else {
      const payload: ProductUpdateRequest = buildBasePayload();
      await put(`/seller/products/${productId.value}`, payload);
      trackDraft = false;
      removeSavedDraft();
      await loadProduct();
      trackDraft = true;
      draftNotice.value = '完整内容已保存到我的商品';
      saveMessage.value =
        product.value?.status === 'CHANGES_REVIEW'
          ? '已保存，商品进入变更审核，审核期间暂停新成交'
          : '已保存';
    }
  } catch (e) {
    saveError.value = (e as ApiError).message || '保存失败，请稍后重试';
  } finally {
    if (isEdit.value) trackDraft = true;
    savingDraft.value = false;
    savingSubmit.value = false;
  }
}

async function saveAndSubmit() {
  if (savingDraft.value || savingSubmit.value) return;
  persistDraft();
  if (!validate() || productId.value === null) {
    await focusFirstMissing();
    return;
  }
  saveError.value = '';
  saveMessage.value = '';
  savingSubmit.value = true;
  try {
    await put(
      `/seller/products/${productId.value}`,
      buildBasePayload() satisfies ProductUpdateRequest,
    );
    await post(`/seller/products/${productId.value}/submit`);
    saveMessage.value = '已提交审核，可在「我的商品」查看进度';
    trackDraft = false;
    removeSavedDraft();
    await loadProduct();
    trackDraft = true;
  } catch (e) {
    saveError.value = (e as ApiError).message || '提交失败，请稍后重试';
  } finally {
    savingSubmit.value = false;
  }
}

function onFilesSelected(event: Event) {
  const files = Array.from((event.target as HTMLInputElement).files ?? []);
  imageError.value = '';
  const existing = product.value?.images.length ?? 0;
  if (existing + files.length > MAX_IMAGES) {
    imageError.value = `图片合计最多 ${MAX_IMAGES} 张（当前已有 ${existing} 张）`;
    pendingFiles.value = [];
    return;
  }
  for (const f of files) {
    if (!IMAGE_TYPES.includes(f.type)) {
      imageError.value = `「${f.name}」格式不支持，仅 JPG / PNG`;
      pendingFiles.value = [];
      return;
    }
    if (f.size > MAX_IMAGE_BYTES) {
      imageError.value = `「${f.name}」超过 5MB，请压缩后再上传`;
      pendingFiles.value = [];
      return;
    }
  }
  pendingFiles.value = files;
}

async function uploadImages() {
  if (productId.value === null || pendingFiles.value.length === 0) return;
  imageLoading.value = true;
  imageError.value = '';
  try {
    const data = new FormData();
    for (const f of pendingFiles.value) data.append('files', f);
    await upload(`/seller/products/${productId.value}/images`, data);
    pendingFiles.value = [];
    if (fileInput.value) fileInput.value.value = '';
    await loadProduct(true);
  } catch (e) {
    imageError.value = (e as ApiError).message || '上传失败，请稍后重试';
  } finally {
    imageLoading.value = false;
  }
}

async function deleteImage(imageId: number) {
  if (productId.value === null) return;
  imageLoading.value = true;
  imageError.value = '';
  try {
    await del(`/seller/products/${productId.value}/images/${imageId}`);
    await loadProduct(true);
  } catch (e) {
    imageError.value = (e as ApiError).message || '删除失败，请稍后重试';
  } finally {
    imageLoading.value = false;
  }
}

async function adjustStock() {
  if (productId.value === null || stockDelta.value === null) return;
  const delta = Number(stockDelta.value);
  if (!Number.isInteger(delta) || delta === 0) {
    stockError.value = '请输入非零整数调整量';
    return;
  }
  stockLoading.value = true;
  stockError.value = '';
  stockMessage.value = '';
  try {
    await put(`/seller/products/${productId.value}/stock`, { delta });
    stockMessage.value = `库存已调整（${delta > 0 ? '+' : ''}${delta}）`;
    stockDelta.value = null;
    await loadProduct(true);
  } catch (e) {
    stockError.value = (e as ApiError).message || '库存调整失败';
  } finally {
    stockLoading.value = false;
  }
}
watch(
  () => route.params.id,
  async (id, previous) => {
    if (id === previous) return;
    trackDraft = false;
    pageLoading.value = true;
    pageError.value = '';
    try {
      if (id) await loadProduct();
      else {
        product.value = null;
        Object.assign(form, emptyForm());
        limitShipping.value = false;
      }
      restoreDraft();
    } catch (e) {
      pageError.value = (e as ApiError).message;
    } finally {
      pageLoading.value = false;
    }
    if (!previous && id && !pageError.value) {
      await nextTick();
      saveMessage.value =
        '草稿已保存，请继续添加至少一张商品图片，再提交审核。';
      fileInput.value?.focus({ preventScroll: true });
      fileInput.value?.scrollIntoView({ block: 'center' });
    }
  },
);
</script>

<style scoped>
.mm-edit__draft-strip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 20px;
  margin-bottom: 20px;
  border: 1px solid var(--mm-border);
  border-radius: 10px;
  background: var(--mm-canvas);
}
.mm-edit__draft-strip p {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--mm-muted);
  line-height: 1.7;
}
.mm-edit__draft-strip button {
  flex-shrink: 0;
}
.mm-edit__field [aria-invalid='true'] {
  border-color: var(--mm-danger);
}
.mm-edit__field-error {
  line-height: 1.6;
}
@media (max-width: 600px) {
  .mm-edit__draft-strip {
    align-items: start;
    flex-direction: column;
  }
}

.mm-edit__province-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 8px;
  max-height: 240px;
  overflow: auto;
  padding: 12px;
  background: var(--mm-canvas);
  border-radius: 10px;
}
.mm-edit {
  max-width: 760px;
  margin: 0 auto;
  padding: var(--mm-space-4) var(--mm-space-4) var(--mm-space-6);
}

.mm-edit__heading {
  font-size: var(--mm-font-xl);
  margin-bottom: var(--mm-space-4);
}

.mm-edit__loading {
  color: var(--mm-muted);
  text-align: center;
  padding: var(--mm-space-6) 0;
}

.mm-edit__review-banner {
  background-color: #fbf2e4;
  color: var(--mm-warning);
  border: 1px solid var(--mm-warning);
  border-radius: var(--mm-radius-m);
  padding: var(--mm-space-3) var(--mm-space-4);
  margin-bottom: var(--mm-space-4);
  font-size: var(--mm-font-s);
  font-weight: 600;
}

.mm-edit__section {
  margin-bottom: var(--mm-space-4);
}

.mm-edit__fields {
  display: flex;
  flex-direction: column;
  gap: var(--mm-space-4);
}

.mm-edit__fields--row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: var(--mm-space-4);
}

.mm-edit__field {
  display: flex;
  flex-direction: column;
  gap: var(--mm-space-1);
}

.mm-edit__label {
  font-size: var(--mm-font-s);
  font-weight: 600;
  color: var(--mm-ink);
}

.mm-edit__field input,
.mm-edit__field select,
.mm-edit__field textarea {
  min-height: 40px;
  padding: var(--mm-space-2) var(--mm-space-3);
  border: 1px solid var(--mm-border);
  border-radius: var(--mm-radius-m);
  background-color: var(--mm-white);
}

.mm-edit__field textarea {
  resize: vertical;
}

.mm-edit__fieldset {
  border: none;
  padding: 0;
  margin: 0;
  display: flex;
  align-items: center;
  gap: var(--mm-space-4);
  flex-wrap: wrap;
}

.mm-edit__checkbox {
  display: inline-flex;
  align-items: center;
  gap: var(--mm-space-2);
}

.mm-edit__hint {
  font-size: var(--mm-font-s);
  color: var(--mm-muted);
}

.mm-edit__field-error {
  font-size: var(--mm-font-s);
  color: var(--mm-danger);
}

.mm-edit__ok {
  font-size: var(--mm-font-s);
  color: var(--mm-success);
}

.mm-edit__images {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(96px, 1fr));
  gap: var(--mm-space-3);
  margin: var(--mm-space-3) 0;
}

.mm-edit__image {
  display: flex;
  flex-direction: column;
  gap: var(--mm-space-1);
}

.mm-edit__image img {
  width: 100%;
  aspect-ratio: 1;
  object-fit: cover;
  border: 1px solid var(--mm-border);
  border-radius: var(--mm-radius-m);
}

.mm-edit__image-delete {
  border: 1px solid var(--mm-danger);
  background: none;
  color: var(--mm-danger);
  border-radius: var(--mm-radius-s);
  font-size: var(--mm-font-s);
  padding: var(--mm-space-1) 0;
}

.mm-edit__upload {
  display: flex;
  align-items: center;
  gap: var(--mm-space-3);
  flex-wrap: wrap;
}

.mm-edit__stock-row {
  display: flex;
  align-items: flex-end;
  gap: var(--mm-space-3);
  flex-wrap: wrap;
  margin-top: var(--mm-space-3);
}

.mm-edit__stock-row .mm-edit__field {
  flex: 1;
  min-width: 180px;
}

.mm-edit__actions {
  display: flex;
  gap: var(--mm-space-3);
  flex-wrap: wrap;
}
</style>
