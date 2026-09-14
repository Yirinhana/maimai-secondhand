<template>
  <div class="mm-search">
    <header class="mm-page-heading">
      <div>
        <p class="mm-eyebrow">FIND YOUR NEXT</p>
        <h1>发现好物</h1>
        <p>给闲置一次新的出发，也找到你需要的那一件。</p>
      </div>
    </header>

    <form
      class="mm-search__filters"
      aria-label="筛选条件"
      novalidate
      @submit.prevent="applyFilters"
    >
      <div class="mm-search__quick">
        <label class="mm-search__field mm-search__keyword"
          ><span>关键词</span
          ><input
            v-model="form.keyword"
            type="search"
            placeholder="搜一搜：教材、数码……"
            maxlength="50"
        /></label>
        <MmButton type="submit">筛选</MmButton>
        <button
          class="mm-search__toggle"
          type="button"
          :aria-expanded="advancedOpen"
          aria-controls="catalog-advanced"
          @click="advancedOpen = !advancedOpen"
        >
          高级筛选 <span aria-hidden="true">{{ advancedOpen ? '−' : '+' }}</span
          ><span v-if="advancedCount" class="mm-search__filter-count">{{
            advancedCount
          }}</span>
        </button>
      </div>
      <div
        v-show="advancedOpen"
        id="catalog-advanced"
        class="mm-search__advanced"
      >
        <div class="mm-search__fields">
          <label class="mm-search__field"
            ><span>分类</span
            ><select v-model="form.categoryId">
              <option value="">全部分类</option>
              <option
                v-for="opt in categoryOptions"
                :key="opt.id"
                :value="String(opt.id)"
              >
                {{ opt.name }}
              </option>
            </select></label
          >
          <label class="mm-search__field"
            ><span>成色</span
            ><select v-model="form.condition">
              <option value="">全部成色</option>
              <option
                v-for="(text, value) in CONDITION_TEXT"
                :key="value"
                :value="value"
              >
                {{ text }}
              </option>
            </select></label
          >
          <label class="mm-search__field"
            ><span>交付方式</span
            ><select v-model="form.deliveryMethod">
              <option value="">不限</option>
              <option
                v-for="(text, value) in DELIVERY_METHOD_TEXT"
                :key="value"
                :value="value"
              >
                {{ text }}
              </option>
            </select></label
          >
          <label class="mm-search__field"
            ><span>地区</span
            ><input
              v-model="form.region"
              type="text"
              placeholder="如：上海市"
              maxlength="50"
          /></label>
          <label class="mm-search__field"
            ><span>最低价（元）</span
            ><input
              v-model="form.minPrice"
              type="text"
              min="0"
              step="0.01"
              inputmode="decimal"
              placeholder="0"
              :aria-invalid="!!validationError"
              :aria-describedby="
                validationError ? 'catalog-filter-error' : undefined
              "
          /></label>
          <label class="mm-search__field"
            ><span>最高价（元）</span
            ><input
              v-model="form.maxPrice"
              type="text"
              min="0"
              step="0.01"
              inputmode="decimal"
              placeholder="不限"
              :aria-invalid="!!validationError"
              :aria-describedby="
                validationError ? 'catalog-filter-error' : undefined
              "
          /></label>
        </div>
        <p v-if="categoryError" class="mm-search__category-error">
          {{ categoryError }}
          <button type="button" @click="loadCategories">重新加载分类</button>
        </p>
        <details class="mm-search__nearby">
          <summary>查找附近闲置</summary>
          <p class="mm-muted">
            主动选择搜索中心后，按商品公开交接区域的近似距离排序。距离不代表实际路线或运费；不会自动申请定位权限。
          </p>
          <MapPicker v-if="auth.me" @select="selectNearby" /><RouterLink
            v-else
            :to="{ path: '/login', query: { redirect: route.fullPath } }"
            >登录后使用地图选点</RouterLink
          >
          <p v-if="nearbyOrigin" class="mm-muted">
            搜索中心：{{ nearbyOrigin.label }}
            <button type="button" @click="clearNearby">清除位置</button>
          </p>
        </details>
        <div class="mm-search__advanced-actions">
          <span>调整完成后点击筛选，应用新条件。</span
          ><MmButton type="submit">应用筛选</MmButton
          ><MmButton variant="ghost" @click="resetFilters">重置</MmButton>
        </div>
      </div>
      <p
        v-if="validationError"
        id="catalog-filter-error"
        class="mm-search__validation"
        role="alert"
      >
        {{ validationError }}
      </p>
    </form>

    <section class="mm-search__results" aria-labelledby="catalog-results-title">
      <div class="mm-search__toolbar">
        <div>
          <h2 id="catalog-results-title" ref="resultsHeading" tabindex="-1">
            {{ applied.keyword ? '搜索结果' : '在售好物' }}
          </h2>
          <p aria-live="polite">
            {{
              loading
                ? '正在查找…'
                : error
                  ? '暂未获取结果'
                  : `共 ${totalElements} 件在售商品`
            }}
          </p>
        </div>
        <label class="mm-search__sort"
          ><span>排序</span
          ><select aria-label="排序" :value="applied.sort" @change="changeSort">
            <option value="time_desc">最新发布</option>
            <option value="price_asc">价格从低到高</option>
            <option value="price_desc">价格从高到低</option>
            <option value="distance_asc" :disabled="!nearbyOrigin">
              离所选地点最近
            </option>
          </select></label
        >
      </div>
      <div
        v-if="chips.length"
        class="mm-search__applied"
        aria-label="已应用筛选"
      >
        <span class="mm-search__applied-label">已筛选</span
        ><button
          v-for="chip in chips"
          :key="chip.key"
          class="mm-search__chip"
          type="button"
          :aria-label="`移除筛选：${chip.label}`"
          @click="removeFilter(chip.key)"
        >
          {{ chip.label }} <span aria-hidden="true">×</span></button
        ><button class="mm-search__clear" type="button" @click="resetFilters">
          清除全部
        </button>
      </div>
      <MmSkeleton
        v-if="loading"
        kind="cards"
        :count="8"
        label="正在加载搜索结果"
      />
      <EmptyState
        v-else-if="error"
        title="暂时无法显示商品"
        :description="error"
        ><div class="mm-search__recovery">
          <MmButton @click="load">重试</MmButton
          ><MmButton variant="ghost" @click="resetFilters">重置筛选</MmButton>
        </div></EmptyState
      >
      <EmptyState
        v-else-if="products.length === 0"
        title="没有找到符合条件的商品"
        description="试着放宽价格范围、移除一个条件，或重新看看全部闲置。"
        ><MmButton variant="ghost" @click="resetFilters"
          >重置筛选</MmButton
        ></EmptyState
      >
      <template v-else
        ><ul class="mm-search__grid">
          <li v-for="product in products" :key="product.id">
            <ProductCard :product="product" />
          </li>
        </ul>
        <MmPagination :page="page" :total-pages="totalPages" @change="goPage"
      /></template>
    </section>
  </div>
</template>

<script setup lang="ts">
import {
  computed,
  nextTick,
  onMounted,
  onScopeDispose,
  reactive,
  ref,
  watch,
} from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { get, type ApiError } from '../../shared/api';
import EmptyState from '../../shared/components/EmptyState.vue';
import MmButton from '../../shared/components/MmButton.vue';
import MmPagination from '../../shared/components/MmPagination.vue';
import MmSkeleton from '../../shared/components/MmSkeleton.vue';
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

interface Filters {
  keyword: string;
  categoryId: string;
  condition: string;
  deliveryMethod: string;
  minPrice: string;
  maxPrice: string;
  region: string;
  sort: string;
}
type FilterKey = keyof Filters;
const blankFilters = (): Filters => ({
  keyword: '',
  categoryId: '',
  condition: '',
  deliveryMethod: '',
  minPrice: '',
  maxPrice: '',
  region: '',
  sort: 'time_desc',
});
const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const form = reactive(blankFilters());
const applied = ref(blankFilters());
const advancedOpen = ref(window.matchMedia('(min-width: 900px)').matches);
const nearbyOrigin = ref<{
  latitude: number;
  longitude: number;
  label: string;
} | null>(null);
const categoryOptions = ref<{ id: number; name: string }[]>([]);
const categoryError = ref('');
const products = ref<ProductSummary[]>([]);
const totalElements = ref(0);
const totalPages = ref(0);
const page = ref(0);
const loading = ref(true);
const error = ref('');
const validationError = ref('');
const resultsHeading = ref<HTMLElement | null>(null);
let requestSequence = 0;
let disposed = false;
const advancedCount = computed(
  () =>
    [
      'categoryId',
      'condition',
      'deliveryMethod',
      'minPrice',
      'maxPrice',
      'region',
    ].filter((key) => applied.value[key as FilterKey]).length +
    (applied.value.sort === 'distance_asc' ? 1 : 0),
);
const chips = computed(() => {
  const f = applied.value;
  const result: { key: FilterKey; label: string }[] = [];
  if (f.keyword) result.push({ key: 'keyword', label: `关键词：${f.keyword}` });
  if (f.categoryId)
    result.push({
      key: 'categoryId',
      label: `分类：${categoryOptions.value.find((option) => String(option.id) === f.categoryId)?.name ?? f.categoryId}`,
    });
  if (f.condition)
    result.push({
      key: 'condition',
      label: CONDITION_TEXT[f.condition as Condition] ?? f.condition,
    });
  if (f.deliveryMethod)
    result.push({
      key: 'deliveryMethod',
      label:
        DELIVERY_METHOD_TEXT[f.deliveryMethod as DeliveryMethod] ??
        f.deliveryMethod,
    });
  if (f.minPrice)
    result.push({ key: 'minPrice', label: `最低 ¥${f.minPrice}` });
  if (f.maxPrice)
    result.push({ key: 'maxPrice', label: `最高 ¥${f.maxPrice}` });
  if (f.region) result.push({ key: 'region', label: `地区：${f.region}` });
  if (f.sort === 'distance_asc')
    result.push({
      key: 'sort',
      label: nearbyOrigin.value
        ? `附近：${nearbyOrigin.value.label}`
        : '附近：请重新选点',
    });
  return result;
});
function validate(filters: Filters) {
  for (const value of [filters.minPrice, filters.maxPrice]) {
    if (
      value &&
      (!/^\d+(\.\d{1,2})?$/.test(value) ||
        !Number.isSafeInteger(Math.round(Number(value) * 100)))
    )
      return '价格请输入大于或等于 0 的金额，最多保留两位小数。';
  }
  if (
    filters.minPrice &&
    filters.maxPrice &&
    Number(filters.minPrice) > Number(filters.maxPrice)
  )
    return '最低价不能高于最高价，请调整后再筛选。';
  return '';
}
function queryString(key: string) {
  return typeof route.query[key] === 'string'
    ? (route.query[key] as string)
    : '';
}
function syncFromQuery() {
  const values = blankFilters();
  for (const key of Object.keys(values) as FilterKey[])
    values[key] = queryString(key) || (key === 'sort' ? 'time_desc' : '');
  applied.value = values;
  Object.assign(form, values);
  validationError.value = '';
  const requestedPage = Number(queryString('page'));
  page.value =
    Number.isSafeInteger(requestedPage) && requestedPage > 0
      ? requestedPage
      : 0;
}
async function load() {
  const sequence = ++requestSequence;
  const filters = { ...applied.value };
  const origin = nearbyOrigin.value ? { ...nearbyOrigin.value } : null;
  loading.value = true;
  error.value = '';
  const invalid = validate(filters);
  if (invalid || (filters.sort === 'distance_asc' && !origin)) {
    error.value =
      invalid ||
      '附近搜索的地点只保留在本次页面中，请展开高级筛选重新选点，或移除附近条件。';
    products.value = [];
    totalElements.value = 0;
    totalPages.value = 0;
    loading.value = false;
    return;
  }
  try {
    const data = await get<Page<ProductSummary>>('/products', {
      keyword: filters.keyword || undefined,
      categoryId: filters.categoryId ? Number(filters.categoryId) : undefined,
      condition: (filters.condition || undefined) as Condition | undefined,
      deliveryMethod: (filters.deliveryMethod || undefined) as
        DeliveryMethod | undefined,
      minPriceCents: filters.minPrice
        ? Math.round(Number(filters.minPrice) * 100)
        : undefined,
      maxPriceCents: filters.maxPrice
        ? Math.round(Number(filters.maxPrice) * 100)
        : undefined,
      region: filters.region || undefined,
      sort: filters.sort as ProductSort,
      originLatitude:
        filters.sort === 'distance_asc' ? origin?.latitude : undefined,
      originLongitude:
        filters.sort === 'distance_asc' ? origin?.longitude : undefined,
      page: page.value,
      size: 20,
    });
    if (sequence !== requestSequence) return;
    products.value = data.content;
    totalElements.value = data.totalElements;
    totalPages.value = data.totalPages;
  } catch (cause) {
    if (sequence === requestSequence)
      error.value = (cause as ApiError).message || '搜索失败，请稍后重试';
  } finally {
    if (sequence === requestSequence) loading.value = false;
  }
}
function navigate(filters: Filters, pageNo = 0) {
  const query: Record<string, string> = {};
  for (const key of Object.keys(filters) as FilterKey[])
    if (filters[key] && !(key === 'sort' && filters[key] === 'time_desc'))
      query[key] = filters[key];
  if (pageNo > 0) query.page = String(pageNo);
  const target = { path: '/search', query };
  if (router.resolve(target).fullPath === route.fullPath) {
    syncFromQuery();
    void load();
  } else void router.push(target);
}
function applyFilters() {
  const filters = Object.fromEntries(
    Object.entries(form).map(([key, value]) => [key, String(value).trim()]),
  ) as unknown as Filters;
  validationError.value = validate(filters);
  if (validationError.value) {
    advancedOpen.value = true;
    return;
  }
  navigate(filters);
}
function resetFilters() {
  nearbyOrigin.value = null;
  validationError.value = '';
  navigate(blankFilters());
}
function removeFilter(key: FilterKey) {
  if (key === 'sort') nearbyOrigin.value = null;
  navigate({ ...applied.value, [key]: key === 'sort' ? 'time_desc' : '' });
}
function changeSort(event: Event) {
  navigate({
    ...applied.value,
    sort: (event.target as HTMLSelectElement).value,
  });
}
async function goPage(next: number) {
  const target = {
    path: '/search',
    query: { ...route.query, page: next > 0 ? String(next) : undefined },
  };
  const expectedPath = router.resolve(target).fullPath;
  const failure = await router.push(target);
  if (failure) return;
  await nextTick();
  if (route.fullPath !== expectedPath) return;
  resultsHeading.value?.scrollIntoView({ block: 'start', behavior: 'auto' });
  resultsHeading.value?.focus({ preventScroll: true });
}
function selectNearby(address: SelectedAddress) {
  nearbyOrigin.value = {
    latitude: address.latitude,
    longitude: address.longitude,
    label: address.fullAddress,
  };
  form.sort = 'distance_asc';
  applyFilters();
}
function clearNearby() {
  nearbyOrigin.value = null;
  navigate({ ...applied.value, sort: 'time_desc' });
}
async function loadCategories() {
  categoryError.value = '';
  try {
    const tree = await get<Category[]>('/categories');
    if (disposed) return;
    const options: { id: number; name: string }[] = [];
    const flatten = (nodes: Category[], prefix = '') => {
      for (const node of nodes) {
        options.push({ id: node.id, name: prefix + node.name });
        if (node.children?.length)
          flatten(node.children, prefix + node.name + ' / ');
      }
    };
    flatten(tree);
    categoryOptions.value = options;
  } catch {
    if (!disposed) categoryError.value = '分类暂时未加载，仍可按其他条件搜索。';
  }
}
onMounted(loadCategories);
watch(
  () => route.query,
  () => {
    syncFromQuery();
    void load();
  },
  { immediate: true },
);
onScopeDispose(() => {
  disposed = true;
  requestSequence++;
});
</script>

<style scoped>
.mm-search {
  max-width: 1280px;
  margin: 0 auto;
  padding: 20px 24px 64px;
}
.mm-search > .mm-page-heading {
  margin-bottom: 26px;
}
.mm-search__filters {
  background: var(--mm-white);
  border: 1px solid var(--mm-border);
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 30px;
}
.mm-search__quick {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  align-items: end;
  gap: 12px;
}
.mm-search__field {
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
}
.mm-search__field input,
.mm-search__field select,
.mm-search__sort select {
  width: 100%;
  min-width: 0;
  min-height: 44px;
  padding: 9px 12px;
  border: 1px solid var(--mm-border);
  border-radius: 6px;
  background: var(--mm-white);
  font-size: 14px;
  color: var(--mm-ink);
}
.mm-search__field input:focus-visible,
.mm-search__field select:focus-visible,
.mm-search__sort select:focus-visible,
.mm-search button:focus-visible,
.mm-search summary:focus-visible {
  outline: 2px solid var(--mm-primary);
  outline-offset: 3px;
}
.mm-search__quick > :deep(.mm-button),
.mm-search__toggle {
  min-height: 44px;
}
.mm-search__toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 9px;
  padding: 0 12px;
  color: var(--mm-ink);
  background: #f4f2ee;
  border: 1px solid transparent;
  border-radius: 6px;
  font-size: 14px;
}
.mm-search__filter-count {
  display: inline-grid;
  place-items: center;
  min-width: 20px;
  height: 20px;
  border-radius: 50%;
  font-size: 12px;
  background: var(--mm-ink);
  color: white;
}
.mm-search__advanced {
  border-top: 1px solid var(--mm-border);
  margin-top: 20px;
  padding-top: 20px;
}
.mm-search__fields {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
}
.mm-search__nearby {
  margin-top: 20px;
  border-top: 1px dashed var(--mm-border);
  padding-top: 16px;
}
.mm-search__nearby summary {
  cursor: pointer;
  font-size: 14px;
  font-weight: 600;
}
.mm-search__nearby > p {
  margin: 12px 0;
  font-size: 13px;
  line-height: 1.8;
  overflow-wrap: anywhere;
}
.mm-search__nearby button,
.mm-search__category-error button {
  color: var(--mm-primary);
  background: none;
  border: 0;
  text-decoration: underline;
}
.mm-search__advanced-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 20px;
}
.mm-search__advanced-actions > span {
  flex: 1;
  font-size: 12px;
  color: var(--mm-muted);
}
.mm-search__category-error,
.mm-search__validation {
  font-size: 13px;
  margin-top: 14px;
  line-height: 1.6;
}
.mm-search__validation {
  color: var(--mm-danger);
}
.mm-search__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}
.mm-search__toolbar h2 {
  scroll-margin-top: 20px;
  font-size: 21px;
  margin: 0 0 3px;
}
.mm-search__toolbar p {
  color: var(--mm-muted);
  font-size: 13px;
}
.mm-search__sort {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  font-size: 13px;
}
.mm-search__sort > span {
  white-space: nowrap;
}
.mm-search__sort select {
  width: 176px;
}
.mm-search__applied {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 20px;
}
.mm-search__applied-label {
  color: var(--mm-muted);
  font-size: 12px;
  margin-right: 2px;
}
.mm-search__chip {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  max-width: 100%;
  text-align: left;
  overflow-wrap: anywhere;
  background: #eeeae3;
  color: var(--mm-ink);
  border: 1px solid transparent;
  border-radius: 5px;
  padding: 8px 10px;
  font-size: 12px;
}
.mm-search__chip > span {
  font-size: 17px;
  line-height: 1;
  flex-shrink: 0;
}
.mm-search__clear {
  background: none;
  border: 0;
  padding: 8px 5px;
  color: var(--mm-muted);
  font-size: 12px;
  text-decoration: underline;
}
.mm-search__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(210px, 1fr));
  gap: 28px 20px;
}
.mm-search__grid > li {
  min-width: 0;
}
.mm-search__recovery {
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: 10px;
}
@media (max-width: 700px) {
  .mm-search {
    padding: 12px 16px 40px;
  }
  .mm-search > .mm-page-heading {
    margin-bottom: 20px;
  }
  .mm-search__filters {
    padding: 14px;
    margin-bottom: 24px;
  }
  .mm-search__quick {
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 10px;
  }
  .mm-search__toggle {
    grid-column: 1 / -1;
    justify-content: space-between;
    min-height: 40px;
  }
  .mm-search__filter-count {
    margin-right: auto;
  }
  .mm-search__fields {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 16px 10px;
  }
  .mm-search__advanced-actions > span {
    flex-basis: 100%;
  }
  .mm-search__advanced-actions > :deep(.mm-button) {
    flex: 1;
  }
  .mm-search__toolbar {
    gap: 10px;
  }
  .mm-search__toolbar h2 {
    font-size: 19px;
  }
  .mm-search__sort {
    gap: 6px;
  }
  .mm-search__sort > span {
    display: none;
  }
  .mm-search__sort select {
    width: 158px;
    padding-left: 8px;
    font-size: 13px;
  }
  .mm-search__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 22px 12px;
  }
}
</style>
