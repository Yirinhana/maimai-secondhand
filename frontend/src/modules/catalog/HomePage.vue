<template>
  <div class="mm-home">
    <section class="mm-home__hero" aria-labelledby="home-title">
      <div class="mm-home__story">
        <p class="mm-eyebrow">
          <span class="mm-home__dot" />闲置流转 · 日常新发现
        </p>
        <h1 id="home-title">
          把喜欢的留下，<br />让闲置接着发光<span>。</span>
        </h1>
        <p class="mm-home__intro">
          一本读过的书，一件陪伴过的好物。<br />在麦麦，寻找适合自己的下一份喜欢。
        </p>
        <div class="mm-home__hero-actions">
          <RouterLink to="/search" class="mm-home__browse"
            >开始逛逛 <MmIcon name="arrow" /></RouterLink
          ><RouterLink to="/publish" class="mm-home__sell"
            >发布我的闲置 ↗</RouterLink
          >
        </div>
        <div class="mm-home__note">
          <MmIcon name="box" /><span>快递寄送 / 同城面交</span
          ><span class="mm-home__note-divider" /><span>认真描述，友好沟通</span>
        </div>
      </div>
      <div class="mm-home__feature">
        <div class="mm-home__feature-heading">
          <span>NEW ARRIVAL</span><span>最近上架 ↗</span>
        </div>
        <MmSkeleton
          v-if="loading"
          kind="rows"
          :count="1"
          label="正在寻找最近上架的好物"
          class="mm-home__feature-loading"
        />
        <RouterLink
          v-else-if="featured"
          :to="`/products/${featured.id}`"
          class="mm-home__feature-product"
          ><ItemImage
            :src="featured.coverImage"
            :alt="featured.title"
            loading="eager" />
          <div>
            <h2>{{ featured.title }}</h2>
            <small
              v-if="isDemoProductImage(featured.coverImage)"
              class="mm-home__demo"
              >演示示意图</small
            >
            <p><MmIcon name="pin" />{{ featured.region }}</p>
            <PriceText :cents="featured.priceCents" /></div
        ></RouterLink>
        <div v-else class="mm-home__feature-empty">
          <MmIcon name="bag" /><span>好物正在路上</span>
        </div>
        <RouterLink
          to="/official/buying-and-selling-guide"
          class="mm-home__guide"
          ><span>第一次来到麦麦？</span
          ><strong>先看看交易指南 <MmIcon name="arrow" /></strong
        ></RouterLink>
      </div>
    </section>
    <section
      class="mm-home__categories-section"
      aria-labelledby="mm-home-categories"
    >
      <div class="mm-home__section-head">
        <h2 id="mm-home-categories">按分类找一找</h2>
        <RouterLink to="/search">全部分类 <MmIcon name="arrow" /></RouterLink>
      </div>
      <div v-if="categoryError" class="mm-home__retry" role="alert">
        <span>{{ categoryError }}</span
        ><MmButton variant="ghost" @click="loadCategories"
          >重新加载分类</MmButton
        >
      </div>
      <ul v-else-if="categories.length" class="mm-home__categories">
        <li v-for="category in categories" :key="category.id">
          <RouterLink
            :to="{ path: '/search', query: { categoryId: category.id } }"
            ><span class="mm-home__category-icon"
              ><MmIcon :name="categoryIcon(category.name)" /></span
            ><span>{{ category.name }}</span></RouterLink
          >
        </li>
      </ul>
      <p v-else class="mm-muted">
        {{ categoriesLoading ? '分类加载中…' : '暂无分类，可直接浏览全部闲置' }}
      </p>
    </section>
    <section class="mm-home__latest" aria-labelledby="mm-home-latest">
      <div class="mm-home__section-head">
        <div>
          <p class="mm-eyebrow">FRESH FINDS</p>
          <h2 id="mm-home-latest">最新上架</h2>
        </div>
        <RouterLink to="/search"
          >查看全部{{ total ? ` ${total} 件` : '' }} <MmIcon name="arrow"
        /></RouterLink>
      </div>
      <div v-if="productError" class="mm-home__retry" role="alert">
        <span>{{ productError }}</span
        ><MmButton variant="ghost" @click="loadProducts">重新加载商品</MmButton>
      </div>
      <MmSkeleton v-else-if="loading" :count="5" label="好物加载中" />
      <EmptyState
        v-else-if="products.length === 0"
        title="还没有在售商品"
        description="从发布第一件闲置开始，让好物继续流转"
      />
      <ul v-else class="mm-home__grid">
        <li v-for="product in products" :key="product.id">
          <ProductCard :product="product" />
        </li>
      </ul>
    </section>
    <section class="mm-home__closing">
      <div>
        <p class="mm-eyebrow">SOMEONE IS LOOKING FOR IT</p>
        <h2>你手中的闲置，也许正被需要。</h2>
        <p>去求购广场看看，给旧物一个新去处。</p>
      </div>
      <RouterLink to="/community/demands"
        >看看大家在找什么 <MmIcon name="arrow"
      /></RouterLink>
    </section>
  </div>
</template>
<script setup lang="ts">
import { isDemoProductImage } from '../../shared/demoImages';
import { computed, onMounted, ref } from 'vue';
import { get, type ApiError } from '../../shared/api';
import type { Category, Page, ProductSummary } from '../../shared/types';
import ProductCard from '../../shared/components/ProductCard.vue';
import ItemImage from '../../shared/components/ItemImage.vue';
import PriceText from '../../shared/components/PriceText.vue';
import EmptyState from '../../shared/components/EmptyState.vue';
import MmIcon from '../../shared/components/MmIcon.vue';
import MmButton from '../../shared/components/MmButton.vue';
import MmSkeleton from '../../shared/components/MmSkeleton.vue';
const categories = ref<Category[]>([]),
  products = ref<ProductSummary[]>([]),
  total = ref(0),
  loading = ref(true),
  categoriesLoading = ref(true),
  categoryError = ref(''),
  productError = ref('');
const featured = computed(() => products.value[0]);
function categoryIcon(name: string) {
  if (/数码|电子|手机/.test(name)) return 'phone';
  if (/服饰|衣|鞋|包/.test(name)) return 'shirt';
  if (/书|教材/.test(name)) return 'book';
  if (/生活|家居/.test(name)) return 'home';
  if (/运动|户外/.test(name)) return 'sport';
  if (/美妆|个护/.test(name)) return 'heart';
  return 'box';
}
async function loadCategories() {
  categoriesLoading.value = true;
  categoryError.value = '';
  await get<Category[]>('/categories')
    .then((r) => {
      categories.value = r;
    })
    .catch((e) => {
      categoryError.value = (e as ApiError).message || '分类加载失败';
    })
    .finally(() => {
      categoriesLoading.value = false;
    });
}
async function loadProducts() {
  loading.value = true;
  productError.value = '';
  await get<Page<ProductSummary>>('/products', { size: 12 })
    .then((r) => {
      products.value = r.content;
      total.value = r.totalElements;
    })
    .catch((e) => {
      productError.value = (e as ApiError).message || '商品加载失败';
    })
    .finally(() => {
      loading.value = false;
    });
}
onMounted(async () => {
  await Promise.all([loadCategories(), loadProducts()]);
});
</script>
<style scoped>
.mm-home__demo {
  display: block;
  color: var(--mm-muted);
  font-size: 11px;
  margin-top: 6px;
}
.mm-home {
  max-width: 1280px;
  margin: auto;
  padding: 0 28px 64px;
}
.mm-home__hero {
  display: grid;
  grid-template-columns: 1.1fr 1fr;
  gap: 50px;
  align-items: center;
  padding: 42px 0 40px;
  border-bottom: 1px solid var(--mm-border);
}
.mm-home__dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #7c8c69;
  margin-right: 8px;
  vertical-align: 1px;
}
.mm-home__retry {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  align-items: center;
  border: 1px solid var(--mm-border);
  border-radius: 10px;
  background: white;
  padding: 24px;
  color: var(--mm-muted);
}
.mm-home__feature-loading {
  min-height: 250px;
  align-content: center;
}
.mm-home__feature-loading :deep(.mm-skeleton__item) {
  background: transparent;
  border: 0;
  padding: 0;
}
.mm-home__story h1 {
  font-size: clamp(32px, 3.6vw, 49px);
  line-height: 1.35;
  letter-spacing: -1.6px;
  font-weight: 850;
  margin: 19px 0 21px;
}
.mm-home__story h1 span {
  color: var(--mm-primary);
}
.mm-home__intro {
  color: var(--mm-muted);
  line-height: 1.9;
  font-size: 14px;
}
.mm-home__hero-actions {
  display: flex;
  gap: 25px;
  align-items: center;
  flex-wrap: wrap;
  margin-top: 29px;
}
.mm-home__browse {
  display: flex;
  gap: 25px;
  align-items: center;
  background: var(--mm-ink);
  color: white;
  border-radius: 7px;
  padding: 13px 23px;
  font-weight: 650;
  font-size: 14px;
}
.mm-home__browse:hover {
  text-decoration: none;
  background: #3f4539;
}
.mm-home__browse .mm-icon {
  transition: transform 0.18s;
}
.mm-home__browse:hover .mm-icon {
  transform: translateX(3px);
}
.mm-home__sell {
  color: var(--mm-ink);
  font-weight: 650;
  font-size: 14px;
}
.mm-home__note {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 9px;
  color: var(--mm-muted);
  font-size: 11px;
  margin-top: 27px;
}
.mm-home__note .mm-icon {
  width: 17px;
  height: 17px;
}
.mm-home__note-divider {
  height: 11px;
  width: 1px;
  background: var(--mm-border);
  margin: 0 5px;
}
.mm-home__feature {
  border-radius: 12px;
  background: #e9e4d9;
  min-width: 0;
  padding: 20px 24px 0;
}
.mm-home__feature-heading {
  display: flex;
  justify-content: space-between;
  color: #68695f;
  font-size: 10px;
  letter-spacing: 1.2px;
  border-bottom: 1px solid #d7d1c5;
  padding-bottom: 13px;
}
.mm-home__feature-product {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  align-items: center;
  min-height: 250px;
  color: var(--mm-ink);
}
.mm-home__feature-product img {
  width: 100%;
  aspect-ratio: 1;
  object-fit: cover;
  border-radius: 9px;
  transform: rotate(-4deg);
  box-shadow: 8px 12px 24px #33392b17;
}
.mm-home__feature-product > div {
  min-width: 0;
}
.mm-home__feature-product h2 {
  font-size: 21px;
  line-height: 1.5;
  overflow-wrap: anywhere;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.mm-home__feature-product p {
  color: var(--mm-muted);
  font-size: 12px;
  margin: 10px 0 15px;
  display: flex;
  align-items: center;
  gap: 4px;
}
.mm-home__feature-product p .mm-icon {
  width: 14px;
  height: 14px;
}
.mm-home__feature-product:hover {
  text-decoration: none;
}
.mm-home__feature-product:hover h2 {
  color: var(--mm-primary);
}
.mm-home__feature-product :deep(.mm-price) {
  font-size: 21px;
  color: var(--mm-ink);
}
.mm-home__feature-empty {
  min-height: 250px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  color: var(--mm-muted);
}
.mm-home__guide {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  border-top: 1px solid #d7d1c5;
  padding: 15px 0;
  color: var(--mm-ink);
  font-size: 12px;
}
.mm-home__guide > span {
  color: var(--mm-muted);
}
.mm-home__guide strong {
  display: flex;
  gap: 14px;
  align-items: center;
}
.mm-home__guide .mm-icon {
  width: 16px;
  height: 16px;
}
.mm-home__categories-section {
  padding: 32px 0 35px;
}
.mm-home__section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 25px;
}
.mm-home__section-head h2 {
  font-size: 23px;
  letter-spacing: -0.5px;
}
.mm-home__section-head > a {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--mm-muted);
  font-size: 12px;
}
.mm-home__section-head .mm-icon {
  width: 16px;
  height: 16px;
}
.mm-home__section-head .mm-eyebrow {
  margin-bottom: 7px;
}
.mm-home__categories {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(100px, 1fr));
  gap: 12px;
}
.mm-home__categories a {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: center;
  background: #fff;
  border: 1px solid var(--mm-border);
  padding: 13px 8px;
  border-radius: 8px;
  font-size: 13px;
  color: var(--mm-ink);
  transition:
    border-color 0.15s,
    background-color 0.15s;
}
.mm-home__categories a:hover {
  border-color: var(--mm-primary);
  text-decoration: none;
  background: #f5f5ed;
}
.mm-home__category-icon {
  display: flex;
  color: #676d5e;
  width: 34px;
  height: 34px;
  align-items: center;
  justify-content: center;
  background: #f0f1e9;
  border-radius: 8px;
}
.mm-home__category-icon .mm-icon {
  width: 20px;
  height: 20px;
}
.mm-home__latest {
  padding-top: 12px;
}
.mm-home__grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 28px 22px;
}
.mm-home__grid > li {
  min-width: 0;
}
.mm-home__closing {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  background: #ebece5;
  border-left: 4px solid #8b9576;
  padding: 29px 32px;
  margin-top: 52px;
}
.mm-home__closing h2 {
  font-size: 24px;
  margin: 7px 0;
}
.mm-home__closing p:not(.mm-eyebrow) {
  color: var(--mm-muted);
  font-size: 13px;
}
.mm-home__closing > a {
  display: flex;
  gap: 14px;
  align-items: center;
  color: var(--mm-ink);
  font-size: 13px;
  white-space: nowrap;
}
@media (max-width: 1100px) {
  .mm-home__hero {
    gap: 32px;
  }
  .mm-home__grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
  .mm-home__feature-product h2 {
    font-size: 17px;
  }
  .mm-home__feature-product {
    min-height: 220px;
  }
}
@media (max-width: 760px) {
  .mm-home {
    padding: 0 18px 36px;
  }
  .mm-home__hero {
    grid-template-columns: 1fr;
    padding: 24px 0;
    gap: 22px;
  }
  .mm-home__story h1 {
    font-size: 31px;
    margin: 12px 0;
    line-height: 1.3;
  }
  .mm-home__intro {
    font-size: 13px;
  }
  .mm-home__feature {
    padding: 16px 18px 0;
  }
  .mm-home__feature-product {
    min-height: 144px;
    grid-template-columns: 106px minmax(0, 1fr);
    gap: 22px;
  }
  .mm-home__feature-product img {
    max-height: 106px;
  }
  .mm-home__categories {
    display: flex;
    overflow-x: auto;
    gap: 10px;
    padding-bottom: 8px;
    scrollbar-width: thin;
    scroll-snap-type: x proximity;
  }
  .mm-home__categories li {
    flex: 0 0 86px;
    scroll-snap-align: start;
  }
  .mm-home__categories a {
    flex-direction: column;
    padding: 10px 5px;
    gap: 7px;
    font-size: 12px;
  }
  .mm-home__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 22px 13px;
  }
  .mm-home__categories-section {
    padding: 24px 0 20px;
  }
  .mm-home__hero-actions {
    margin-top: 20px;
    gap: 18px;
  }
  .mm-home__note {
    margin-top: 18px;
    font-size: 10px;
  }
  .mm-home__feature-loading {
    min-height: 144px;
  }
  .mm-home__section-head {
    margin-bottom: 19px;
  }
  .mm-home__section-head h2 {
    font-size: 21px;
  }
  .mm-home__section-head > a {
    font-size: 11px;
  }
  .mm-home__closing {
    padding: 22px;
    align-items: flex-start;
    flex-direction: column;
    margin-top: 34px;
  }
  .mm-home__closing h2 {
    font-size: 21px;
  }
  .mm-home__guide {
    font-size: 11px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .mm-home__browse .mm-icon,
  .mm-home__categories a {
    transition: none;
  }
}
</style>
