<template>
  <div class="mm-home">
    <section class="mm-home__welcome" aria-labelledby="home-title">
      <div>
        <p class="mm-eyebrow">GOOD THINGS, AGAIN.</p>
        <h1 id="home-title">好东西，值得再相遇。</h1>
        <p class="mm-home__welcome-copy">
          逛逛新上架的闲置，找到适合你的下一件好物。
        </p>
      </div>
      <div class="mm-home__welcome-side">
        <div
          v-if="products.length"
          class="mm-home__peek"
          aria-hidden="true"
        >
          <ItemImage
            v-for="item in products.slice(0, 3)"
            :key="item.id"
            :src="item.coverImage"
            alt=""
            size="thumb"
            loading="eager"
          />
        </div>
        <RouterLink to="/welcome"
          >认识麦麦与麦仔 <MmIcon name="arrow"
        /></RouterLink>
      </div>
    </section>
    <div id="home-market" class="mm-home__market" tabindex="-1">
      <section
        class="mm-home__categories-section"
        aria-labelledby="mm-home-categories"
      >
        <div class="mm-home__section-head">
          <h2 id="mm-home-categories">按分类找一找</h2>
          <RouterLink to="/search"
            >全部分类 <MmIcon name="arrow"
          /></RouterLink>
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
          {{
            categoriesLoading
              ? '分类加载中…'
              : '暂无分类，可直接浏览全部闲置'
          }}
        </p>
      </section>
      <section class="mm-home__latest" aria-labelledby="mm-home-latest">
        <div class="mm-home__section-head">
          <div>
            <p class="mm-eyebrow">FRESH FINDS</p>
            <h2 id="mm-home-latest">最新上架</h2>
          </div>
          <RouterLink to="/search"
            >查看全部{{ total ? ` ${total} 件` : '' }}
            <MmIcon name="arrow"
          /></RouterLink>
        </div>
        <div v-if="productError" class="mm-home__retry" role="alert">
          <span>{{ productError }}</span
          ><MmButton variant="ghost" @click="loadProducts"
            >重新加载商品</MmButton
          >
        </div>
        <MmSkeleton v-else-if="loading" :count="5" label="好物加载中" />
        <EmptyState
          v-else-if="products.length === 0"
          title="还没有在售商品"
          description="从发布第一件闲置开始，让好物继续流转"
        />
        <ul v-else class="mm-home__grid">
          <li v-for="(product, index) in products" :key="product.id">
            <ProductCard :product="product" :priority="index < 4" />
          </li>
        </ul>
      </section>
    </div>
  </div>
</template>
<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { get, type ApiError } from '../../shared/api';
import type { Category, Page, ProductSummary } from '../../shared/types';
import ProductCard from '../../shared/components/ProductCard.vue';
import ItemImage from '../../shared/components/ItemImage.vue';
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
.mm-home {
  max-width: 1280px;
  margin: auto;
  padding: 0 28px 72px;
}
.mm-home__market {
  padding-top: 10px;
  scroll-margin-top: 24px;
}
.mm-home__market:focus {
  outline: none;
}
.mm-home__welcome {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  padding: 34px 36px;
  margin-top: 28px;
  border: 1px solid #e8dfd1;
  border-radius: 20px;
  background: #f0ebe1;
  align-items: center;
}
.mm-home__welcome h1 {
  font-size: clamp(28px, 3.5vw, 42px);
  line-height: 1.5;
  font-weight: 650;
  margin-top: 12px;
}
.mm-home__welcome-copy {
  color: var(--mm-muted);
  font-size: 14px;
  margin-top: 12px;
}
.mm-home__welcome-side > a {
  display: inline-flex;
  align-items: center;
  gap: 16px;
  min-height: 44px;
  color: var(--mm-muted);
  font-size: 12px;
  white-space: nowrap;
}
.mm-home__welcome-side {
  display: grid;
  justify-items: end;
  gap: 12px;
  flex-shrink: 0;
}
.mm-home__peek {
  display: flex;
  padding: 10px 6px;
}
.mm-home__peek img {
  width: 82px;
  height: 98px;
  border: 5px solid white;
  border-radius: 10px;
  object-fit: cover;
  box-shadow: 0 5px 16px #4e3b2320;
  transform: rotate(-8deg);
}
.mm-home__peek img + img {
  margin-left: -15px;
  transform: rotate(5deg) translateY(5px);
}
.mm-home__peek img:last-child {
  transform: rotate(13deg);
}
.mm-home__welcome .mm-icon {
  width: 17px;
  height: 17px;
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
  background: #fcf0e6;
}
.mm-home__category-icon {
  display: flex;
  color: #a44920;
  width: 34px;
  height: 34px;
  align-items: center;
  justify-content: center;
  background: #fbefe5;
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
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 28px 22px;
}
.mm-home__grid > li {
  min-width: 0;
}

@media (max-width: 1100px) {
  .mm-home__grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}
@media (max-width: 760px) {
  .mm-home {
    padding: 0 18px 44px;
  }
  .mm-home__market {
    padding-top: 0;
  }
  .mm-home__welcome {
    padding: 24px 20px;
    margin-top: 18px;
    align-items: flex-start;
    flex-direction: column;
    gap: 10px;
  }
  .mm-home__welcome-side {
    width: 100%;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
  }
  .mm-home__peek img {
    width: 43px;
    height: 52px;
    border-width: 3px;
  }
  .mm-home__welcome h1 {
    font-size: clamp(24px, 6.3vw, 34px);
  }
  .mm-home__welcome-copy {
    font-size: 12px;
    line-height: 1.8;
  }
  .mm-home__categories {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 9px;
  }
  .mm-home__categories a {
    flex-direction: column;
    padding: 12px 4px;
    gap: 6px;
    font-size: 12px;
  }
  .mm-home__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 22px 13px;
  }
  .mm-home__categories-section {
    padding: 24px 0 26px;
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
}
@media (prefers-reduced-motion: reduce) {
  .mm-home__categories a {
    transition: none;
  }
}
</style>
