<template>
  <div class="mm-home">
    <section class="mm-home__hero" aria-labelledby="home-title">
      <div class="mm-home__story">
        <p class="mm-eyebrow">闲置流转 · 日常新发现</p>
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
        <RouterLink
          v-if="featured"
          :to="`/products/${featured.id}`"
          class="mm-home__feature-product"
          ><ItemImage :src="featured.coverImage" :alt="featured.title" />
          <div>
            <h2>{{ featured.title }}</h2>
            <p>{{ featured.region }}</p>
            <PriceText :cents="featured.priceCents" /></div
        ></RouterLink>
        <div v-else class="mm-home__feature-empty">
          <MmIcon name="bag" /><span>好物正在路上</span>
        </div>
        <RouterLink to="/official" class="mm-home__guide"
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
      <p v-if="categoryError" class="mm-error" role="alert">
        {{ categoryError }}
      </p>
      <ul v-else-if="categories.length" class="mm-home__categories">
        <li v-for="(category, index) in categories" :key="category.id">
          <RouterLink
            :to="{ path: '/search', query: { categoryId: category.id } }"
            ><span class="mm-home__category-icon"
              ><MmIcon
                :name="
                  categoryIcons[index % categoryIcons.length] ?? 'box'
                " /></span
            ><span>{{ category.name }}</span></RouterLink
          >
        </li>
      </ul>
      <p v-else class="mm-muted">分类加载中…</p>
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
      <p v-if="productError" class="mm-error" role="alert">
        {{ productError }}
      </p>
      <p v-else-if="loading" class="mm-muted">好物加载中…</p>
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
import { computed, onMounted, ref } from 'vue';
import { get, type ApiError } from '../../shared/api';
import type { Category, Page, ProductSummary } from '../../shared/types';
import ProductCard from '../../shared/components/ProductCard.vue';
import ItemImage from '../../shared/components/ItemImage.vue';
import PriceText from '../../shared/components/PriceText.vue';
import EmptyState from '../../shared/components/EmptyState.vue';
import MmIcon from '../../shared/components/MmIcon.vue';
const categories = ref<Category[]>([]),
  products = ref<ProductSummary[]>([]),
  total = ref(0),
  loading = ref(true),
  categoryError = ref(''),
  productError = ref('');
const featured = computed(() => products.value[0]),
  categoryIcons = ['box', 'bag', 'book', 'grid', 'pin', 'heart', 'box'];
onMounted(async () => {
  await Promise.all([
    get<Category[]>('/categories')
      .then((r) => {
        categories.value = r;
      })
      .catch((e) => {
        categoryError.value = (e as ApiError).message || '分类加载失败';
      }),
    get<Page<ProductSummary>>('/products', { size: 12 })
      .then((r) => {
        products.value = r.content;
        total.value = r.totalElements;
      })
      .catch((e) => {
        productError.value = (e as ApiError).message || '商品加载失败';
      })
      .finally(() => {
        loading.value = false;
      }),
  ]);
});
</script>
<style scoped>
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
  padding: 52px 0 48px;
  border-bottom: 1px solid var(--mm-border);
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
  padding: 15px 8px;
  border-radius: 8px;
  font-size: 13px;
  color: var(--mm-ink);
  transition: border-color 0.15s;
}
.mm-home__categories a:hover {
  border-color: var(--mm-primary);
  text-decoration: none;
}
.mm-home__category-icon {
  display: flex;
  color: #676d5e;
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
    padding: 31px 0;
    gap: 28px;
  }
  .mm-home__story h1 {
    font-size: 35px;
    margin: 15px 0;
  }
  .mm-home__intro {
    font-size: 13px;
  }
  .mm-home__feature {
    padding: 16px 18px 0;
  }
  .mm-home__feature-product {
    min-height: 210px;
    grid-template-columns: 1fr 1fr;
  }
  .mm-home__feature-product img {
    max-height: 160px;
  }
  .mm-home__categories {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 9px;
  }
  .mm-home__categories a {
    flex-direction: column;
    padding: 13px 5px;
    gap: 7px;
    font-size: 12px;
  }
  .mm-home__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 22px 13px;
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
</style>
