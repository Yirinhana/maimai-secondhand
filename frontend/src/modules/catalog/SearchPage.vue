<template>
  <div class="mm-search">
    <header class="mm-page-heading">
      <div>
        <p class="mm-eyebrow">FIND YOUR NEXT</p>
        <h1>发现好物</h1>
        <p>按分类、成色和交付方式，慢慢找到适合你的那一件。</p>
      </div>
    </header>
    <form
      class="mm-search__filters"
      aria-label="筛选条件"
      @submit.prevent="applyFilters"
    >
      <div class="mm-search__row">
        <label class="mm-search__field mm-search__field--grow">
          <span class="mm-search__label">关键词</span>
          <input
            v-model="form.keyword"
            type="search"
            placeholder="搜一搜：教材、数码……"
            maxlength="50"
          />
        </label>
        <label class="mm-search__field">
          <span class="mm-search__label">分类</span>
          <select v-model="form.categoryId">
            <option value="">全部分类</option>
            <option
              v-for="opt in categoryOptions"
              :key="opt.id"
              :value="String(opt.id)"
            >
              {{ opt.name }}
            </option>
          </select>
        </label>
        <label class="mm-search__field">
          <span class="mm-search__label">成色</span>
          <select v-model="form.condition">
            <option value="">全部成色</option>
            <option
              v-for="(text, value) in CONDITION_TEXT"
              :key="value"
              :value="value"
            >
              {{ text }}
            </option>
          </select>
        </label>
        <label class="mm-search__field">
          <span class="mm-search__label">交付方式</span>
          <select v-model="form.deliveryMethod">
            <option value="">不限</option>
            <option
              v-for="(text, value) in DELIVERY_METHOD_TEXT"
              :key="value"
              :value="value"
            >
              {{ text }}
            </option>
          </select>
        </label>
      </div>
      <div class="mm-search__row">
        <label class="mm-search__field mm-search__field--price">
          <span class="mm-search__label">最低价（元）</span>
          <input
            v-model="form.minPrice"
            type="number"
            min="0"
            step="0.01"
            placeholder="0"
          />
        </label>
        <label class="mm-search__field mm-search__field--price">
          <span class="mm-search__label">最高价（元）</span>
          <input
            v-model="form.maxPrice"
            type="number"
            min="0"
            step="0.01"
            placeholder="不限"
          />
        </label>
        <label class="mm-search__field mm-search__field--grow">
          <span class="mm-search__label">地区</span>
          <input
            v-model="form.region"
            type="text"
            placeholder="如：成都市"
            maxlength="50"
          />
        </label>
        <label class="mm-search__field">
          <span class="mm-search__label">排序</span>
          <select v-model="form.sort">
            <option value="time_desc">最新发布</option>
            <option value="price_asc">价格从低到高</option>
            <option value="price_desc">价格从高到低</option>
            <option value="distance_asc" :disabled="!nearbyOrigin">
              离所选地点最近
            </option>
          </select>
        </label>
        <div class="mm-search__actions">
          <MmButton type="submit">筛选</MmButton>
          <MmButton variant="ghost" @click="resetFilters">重置</MmButton>
        </div>
      </div>
      <details class="mm-search__nearby">
        <summary>查找附近闲置</summary>
        <p class="mm-muted">
          主动选择搜索中心后，按商品公开交接区域的近似距离排序。距离不代表实际行车路线或运费；不会自动申请定位权限。
        </p>
        <MapPicker v-if="auth.me" @select="selectNearby" /><RouterLink
          v-else
          to="/login?redirect=/search"
          >登录后使用地图选点</RouterLink
        >
        <p v-if="nearbyOrigin" class="mm-muted">
          搜索中心：{{ nearbyOrigin.label }}
          <button type="button" @click="clearNearby">清除位置</button>
        </p>
      </details>
    </form>

    <p v-if="error" class="mm-search__error" role="alert">
      {{ error }}
      <MmButton variant="ghost" @click="load">重试</MmButton>
    </p>
    <p v-else-if="loading" class="mm-search__loading">搜索中……</p>
    <EmptyState
      v-else-if="products.length === 0"
      title="没有找到符合条件的商品"
      description="换个关键词或放宽筛选条件试试"
    />
    <template v-else>
      <p class="mm-search__total">共 {{ totalElements }} 件在售商品</p>
      <ul class="mm-search__grid">
        <li v-for="product in products" :key="product.id">
          <ProductCard :product="product" />
        </li>
      </ul>
      <MmPagination :page="page" :total-pages="totalPages" @change="goPage" />
    </template>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { get, type ApiError } from '../../shared/api';
import EmptyState from '../../shared/components/EmptyState.vue';
import MmButton from '../../shared/components/MmButton.vue';
import MmPagination from '../../shared/components/MmPagination.vue';
import ProductCard from '../../shared/components/ProductCard.vue';
import MapPicker, {
  type SelectedAddress,
} from '../../shared/components/MapPicker.vue';
import { useAuthStore } from '../../shared/stores/auth';
import {
  CONDITION_TEXT,
  DELIVERY_METHOD_TEXT,
  type Category,
  type Condition,
  type DeliveryMethod,
  type Page,
  type ProductSort,
  type ProductSummary,
} from '../../shared/types';

const PAGE_SIZE = 20;

const route = useRoute();
const router = useRouter();
const auth = useAuthStore(),
  nearbyOrigin = ref<{
    latitude: number;
    longitude: number;
    label: string;
  } | null>(null);
function selectNearby(address: SelectedAddress) {
  nearbyOrigin.value = {
    latitude: address.latitude,
    longitude: address.longitude,
    label: address.fullAddress,
  };
  form.sort = 'distance_asc';
  if (route.query.sort === 'distance_asc') {
    page.value = 0;
    void load();
  } else pushQuery(0);
}
function clearNearby() {
  nearbyOrigin.value = null;
  form.sort = 'time_desc';
  pushQuery(0);
}

interface CategoryOption {
  id: number;
  name: string;
}

const categoryOptions = ref<CategoryOption[]>([]);
const products = ref<ProductSummary[]>([]);
const totalElements = ref(0);
const totalPages = ref(0);
const page = ref(0);
const loading = ref(true);
const error = ref('');

const form = reactive({
  keyword: '',
  categoryId: '',
  condition: '',
  deliveryMethod: '',
  minPrice: '',
  maxPrice: '',
  region: '',
  sort: 'time_desc',
});

function queryString(key: string): string {
  const v = route.query[key];
  return typeof v === 'string' ? v : '';
}

function syncFormFromQuery() {
  form.keyword = queryString('keyword');
  form.categoryId = queryString('categoryId');
  form.condition = queryString('condition');
  form.deliveryMethod = queryString('deliveryMethod');
  form.minPrice = queryString('minPrice');
  form.maxPrice = queryString('maxPrice');
  form.region = queryString('region');
  form.sort = queryString('sort') || 'time_desc';
  const p = Number(queryString('page'));
  page.value = Number.isInteger(p) && p > 0 ? p : 0;
}

function yuanToCents(yuan: string): number | undefined {
  if (!yuan.trim()) return undefined;
  const n = Number(yuan);
  if (!Number.isFinite(n) || n < 0) return undefined;
  return Math.round(n * 100);
}

async function load() {
  loading.value = true;
  error.value = '';
  if (form.sort === 'distance_asc' && !nearbyOrigin.value) {
    products.value = [];
    error.value = '请重新选择附近搜索的中心地点';
    loading.value = false;
    return;
  }
  try {
    const data = await get<Page<ProductSummary>>('/products', {
      keyword: form.keyword || undefined,
      categoryId: form.categoryId ? Number(form.categoryId) : undefined,
      condition: (form.condition || undefined) as Condition | undefined,
      minPriceCents: yuanToCents(form.minPrice),
      maxPriceCents: yuanToCents(form.maxPrice),
      region: form.region || undefined,
      deliveryMethod: (form.deliveryMethod || undefined) as
        DeliveryMethod | undefined,
      sort: (form.sort || 'time_desc') as ProductSort,
      originLatitude:
        form.sort === 'distance_asc' ? nearbyOrigin.value?.latitude : undefined,
      originLongitude:
        form.sort === 'distance_asc'
          ? nearbyOrigin.value?.longitude
          : undefined,
      page: page.value,
      size: PAGE_SIZE,
    });
    products.value = data.content;
    totalElements.value = data.totalElements;
    totalPages.value = data.totalPages;
  } catch (e) {
    error.value = (e as ApiError).message || '搜索失败，请稍后重试';
  } finally {
    loading.value = false;
  }
}

function pushQuery(pageNo: number) {
  router.push({
    path: '/search',
    query: {
      keyword: form.keyword || undefined,
      categoryId: form.categoryId || undefined,
      condition: form.condition || undefined,
      deliveryMethod: form.deliveryMethod || undefined,
      minPrice: form.minPrice || undefined,
      maxPrice: form.maxPrice || undefined,
      region: form.region || undefined,
      sort: form.sort !== 'time_desc' ? form.sort : undefined,
      page: pageNo > 0 ? String(pageNo) : undefined,
    },
  });
}

function applyFilters() {
  pushQuery(0);
}

function resetFilters() {
  nearbyOrigin.value = null;
  form.keyword = '';
  form.categoryId = '';
  form.condition = '';
  form.deliveryMethod = '';
  form.minPrice = '';
  form.maxPrice = '';
  form.region = '';
  form.sort = 'time_desc';
  pushQuery(0);
}

function goPage(p: number) {
  pushQuery(p);
}

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

onMounted(async () => {
  try {
    const tree = await get<Category[]>('/categories');
    const out: CategoryOption[] = [];
    flattenCategories(tree, '', out);
    categoryOptions.value = out;
  } catch {
    // 分类加载失败不阻塞搜索，仅不提供分类下拉
  }
});

watch(
  () => route.query,
  () => {
    syncFormFromQuery();
    load();
  },
  { immediate: true },
);
</script>

<style scoped>
.mm-search__nearby {
  margin-top: 16px;
}
.mm-search__nearby summary {
  cursor: pointer;
  color: var(--mm-primary);
}
.mm-search {
  max-width: 1280px;
  margin: 0 auto;
  padding: var(--mm-space-4) var(--mm-space-4) var(--mm-space-6);
}

.mm-search__filters {
  background-color: var(--mm-white);
  border: 1px solid var(--mm-border);
  border-radius: var(--mm-radius-l);
  padding: var(--mm-space-4);
  display: flex;
  flex-direction: column;
  gap: var(--mm-space-3);
  margin-bottom: var(--mm-space-4);
}

.mm-search__row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mm-space-3);
  align-items: flex-end;
}

.mm-search__field {
  display: flex;
  flex-direction: column;
  gap: var(--mm-space-1);
  min-width: 140px;
}

.mm-search__field--grow {
  flex: 1;
  min-width: 180px;
}

.mm-search__field--price {
  max-width: 130px;
}

.mm-search__label {
  font-size: var(--mm-font-s);
  font-weight: 600;
  color: var(--mm-ink);
}

.mm-search__field input,
.mm-search__field select {
  min-height: 40px;
  padding: 0 var(--mm-space-3);
  border: 1px solid var(--mm-border);
  border-radius: var(--mm-radius-m);
  background-color: var(--mm-white);
}

.mm-search__actions {
  display: flex;
  gap: var(--mm-space-2);
}

.mm-search__total {
  font-size: var(--mm-font-s);
  color: var(--mm-muted);
  margin-bottom: var(--mm-space-3);
}

.mm-search__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(210px, 1fr));
  gap: var(--mm-space-4);
}

.mm-search__grid > li {
  min-width: 0;
}

.mm-search__loading {
  color: var(--mm-muted);
  padding: var(--mm-space-6) 0;
  text-align: center;
}

.mm-search__error {
  color: var(--mm-danger);
  padding: var(--mm-space-6) 0;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--mm-space-3);
}

@media (max-width: 768px) {
  .mm-search__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: var(--mm-space-3);
  }

  .mm-search__field {
    min-width: calc(50% - var(--mm-space-3));
    flex: 1;
  }
}
.mm-search > .mm-page-heading {
  margin-bottom: 23px;
}
.mm-search__filters {
  border-radius: 8px;
  border: 1px solid var(--mm-border);
  box-shadow: none;
  background: white;
}
.mm-search__label {
  font-size: 11px;
  color: var(--mm-muted);
  font-weight: 500;
}
.mm-search__filters input,
.mm-search__filters select {
  font-size: 13px;
  background: #fbfcf9;
  min-width: 0;
}
.mm-search__nearby {
  border-top: 1px solid var(--mm-border);
  padding-top: 13px;
  font-size: 12px;
}
.mm-search__nearby summary {
  cursor: pointer;
  color: #54614c;
}
.mm-search__total {
  font-size: 12px;
  border-bottom: 1px solid var(--mm-border);
  padding-bottom: 15px;
}
.mm-search__grid {
  gap: 29px 23px;
}
@media (max-width: 760px) {
  .mm-search__grid {
    gap: 23px 13px;
  }
  .mm-search__field {
    min-width: 0 !important;
    flex-basis: calc(50% - 8px) !important;
  }
  .mm-search__row {
    gap: 13px;
  }
  .mm-search__filters {
    padding: 17px;
  }
  .mm-search__field input,
  .mm-search__field select {
    width: 100%;
    min-width: 0;
  }
  .mm-search__actions {
    flex-wrap: wrap;
  }
  .mm-search__field--grow {
    flex-basis: 100% !important;
  }
}
</style>
