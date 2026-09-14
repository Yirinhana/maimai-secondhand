<template>
  <div class="mm-home">
    <section class="mm-home__hero">
      <h1 class="mm-home__slogan">让闲置再次流转</h1>
      <p class="mm-home__intro">少一件闲置，多一种可能。认真描述，安心沟通，让好物继续被喜欢。</p>
      <form class="mm-home__search" role="search" @submit.prevent="onSearch">
        <label class="mm-visually-hidden" for="mm-home-search">搜索商品</label>
        <input
          id="mm-home-search"
          v-model="keyword"
          type="search"
          placeholder="搜一搜：教材、数码、生活用品……"
          maxlength="50"
        />
        <MmButton type="submit">搜索</MmButton>
      </form>
      <nav class="mm-home__quick" aria-label="首页快捷入口"><RouterLink to="/search">发现好物 →</RouterLink><RouterLink to="/community/demands">看看大家在找什么 →</RouterLink><RouterLink to="/publish">发布我的闲置 →</RouterLink></nav>
    </section>

    <section class="mm-home__section" aria-labelledby="mm-home-categories">
      <h2 id="mm-home-categories" class="mm-home__heading">商品分类</h2>
      <p v-if="categoryError" class="mm-home__error" role="alert">{{ categoryError }}</p>
      <ul v-else-if="categories.length" class="mm-home__categories">
        <li v-for="category in categories" :key="category.id">
          <RouterLink
            class="mm-home__category"
            :to="{ path: '/search', query: { categoryId: category.id } }"
          >
            {{ category.name }}
          </RouterLink>
        </li>
      </ul>
      <p v-else class="mm-home__loading">分类加载中……</p>
    </section>

    <section class="mm-home__section" aria-labelledby="mm-home-latest">
      <h2 id="mm-home-latest" class="mm-home__heading">最新上架</h2>
      <p v-if="productError" class="mm-home__error" role="alert">{{ productError }}</p>
      <EmptyState
        v-else-if="!loading && products.length === 0"
        title="还没有在售商品"
        description="成为第一个发布闲置的人吧"
      />
      <ul v-else class="mm-home__grid">
        <li v-for="product in products" :key="product.id">
          <RouterLink class="mm-product" :to="`/products/${product.id}`">
            <div class="mm-product__cover">
              <ItemImage
                v-if="product.coverImage"
                :src="product.coverImage"
                :alt="product.title"
                loading="lazy"
              />
              <span v-else class="mm-product__no-cover">暂无图片</span>
            </div>
            <div class="mm-product__info">
              <h3 class="mm-product__title">{{ product.title }}</h3>
              <div class="mm-product__meta">
                <PriceText :cents="product.priceCents" />
                <MmTag :text="CONDITION_TEXT[product.condition]" tone="primary" />
              </div>
              <p class="mm-product__region">{{ product.region }}</p>
            </div>
          </RouterLink>
        </li>
      </ul>
    </section>
  </div>
</template>

<script setup lang="ts">
import ItemImage from '../../shared/components/ItemImage.vue'
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { get, type ApiError } from '../../shared/api'
import MmButton from '../../shared/components/MmButton.vue'
import MmTag from '../../shared/components/MmTag.vue'
import PriceText from '../../shared/components/PriceText.vue'
import EmptyState from '../../shared/components/EmptyState.vue'
import { CONDITION_TEXT, type Category, type Page, type ProductSummary } from '../../shared/types'

const router = useRouter()

const keyword = ref('')
const categories = ref<Category[]>([])
const products = ref<ProductSummary[]>([])
const loading = ref(true)
const categoryError = ref('')
const productError = ref('')

function onSearch() {
  const kw = keyword.value.trim()
  if (!kw) return
  router.push({ path: '/search', query: { keyword: kw } })
}

onMounted(async () => {
  try {
    categories.value = await get<Category[]>('/categories')
  } catch (e) {
    categoryError.value = (e as ApiError).message || '分类加载失败'
  }
  try {
    const page = await get<Page<ProductSummary>>('/products', { size: 12 })
    products.value = page.content
  } catch (e) {
    productError.value = (e as ApiError).message || '商品加载失败'
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.mm-home {
  max-width: 1440px;
  margin: 0 auto;
  padding: 0 var(--mm-space-4) var(--mm-space-6);
}

.mm-home__hero {
  padding: var(--mm-space-6) 0 var(--mm-space-5);
  text-align: center;
  background:radial-gradient(ellipse at 15% 10%,#e7ddff 0,transparent 50%),radial-gradient(ellipse at 90% 90%,#ece5fc 0,transparent 60%);
  border-radius:0 0 32px 32px;
}
.mm-home__intro {color:var(--mm-muted);margin:0 auto 22px;max-width:580px;padding:0 16px}
.mm-home__quick {display:flex;justify-content:center;flex-wrap:wrap;gap:12px 26px;margin-top:22px;font-size:14px}

.mm-home__slogan {
  font-size: var(--mm-font-xxl);
  color: var(--mm-ink);
  margin-bottom: var(--mm-space-4);
}

.mm-home__search {
  display: flex;
  gap: var(--mm-space-2);
  max-width: 560px;
  margin: 0 auto;
}

.mm-home__search input {
  flex: 1;
  min-width: 0;
  min-height: 44px;
  padding: 0 var(--mm-space-4);
  border: 1px solid var(--mm-border);
  border-radius: var(--mm-radius-m);
  background-color: var(--mm-white);
}

.mm-home__section {
  margin-top: var(--mm-space-5);
}

.mm-home__heading {
  font-size: var(--mm-font-xl);
  margin-bottom: var(--mm-space-3);
}

.mm-home__categories {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(100px, 1fr));
  gap: var(--mm-space-3);
}

.mm-home__category {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 48px;
  background-color: var(--mm-white);
  border: 1px solid var(--mm-border);
  border-radius: var(--mm-radius-m);
  color: var(--mm-ink);
  font-weight: 600;
}

.mm-home__category:hover {
  border-color: var(--mm-primary);
  color: var(--mm-primary);
  text-decoration: none;
}

.mm-home__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: var(--mm-space-4);
}

.mm-home__grid > li {
  min-width: 0;
}

.mm-product {
  display: block;
  background-color: var(--mm-white);
  border: 1px solid var(--mm-border);
  border-radius: var(--mm-radius-l);
  overflow: hidden;
  color: var(--mm-ink);
  height: 100%;
}

.mm-product:hover {
  text-decoration: none;
  border-color: var(--mm-primary);
}

.mm-product__cover {
  aspect-ratio: 1;
  background-color: var(--mm-canvas);
  display: flex;
  align-items: center;
  justify-content: center;
}

.mm-product__cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.mm-product__no-cover {
  color: var(--mm-muted);
  font-size: var(--mm-font-s);
}

.mm-product__info {
  padding: var(--mm-space-3);
  display: flex;
  flex-direction: column;
  gap: var(--mm-space-2);
  overflow-wrap: anywhere;
}

.mm-product__title {
  font-size: var(--mm-font-base);
  font-weight: 600;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.mm-product__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--mm-space-2);
}

.mm-product__region {
  font-size: var(--mm-font-s);
  color: var(--mm-muted);
}

.mm-home__loading,
.mm-home__error {
  color: var(--mm-muted);
  padding: var(--mm-space-4) 0;
}

.mm-home__error {
  color: var(--mm-danger);
}

@media (max-width: 768px) {
  .mm-home__slogan {
    font-size: var(--mm-font-xl);
  }

  .mm-home__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: var(--mm-space-3);
  }
}
</style>
