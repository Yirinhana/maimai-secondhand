<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { get, type ApiError } from '../../shared/api'
import type { Page } from '../../shared/types'
import { categories, articleDate, type OfficialArticle } from './official'
import MmPagination from '../../shared/components/MmPagination.vue'
const route = useRoute()
const category = computed(() =>
  Object.hasOwn(categories, String(route.query.category))
    ? String(route.query.category)
    : '',
)
const articles = ref<OfficialArticle[]>([]),
  page = ref(0),
  totalPages = ref(0),
  loading = ref(true),
  error = ref('')
let sequence = 0
async function load() {
  const ticket = ++sequence
  loading.value = true
  error.value = ''
  try {
    const data = await get<Page<OfficialArticle>>('/official/articles', {
      category: category.value,
      page: page.value,
      size: 10,
    })
    if (ticket === sequence) {
      articles.value = data.content
      totalPages.value = data.totalPages
    }
  } catch (e) {
    if (ticket === sequence)
      error.value = (e as ApiError).message || '官方内容加载失败'
  } finally {
    if (ticket === sequence) loading.value = false
  }
}
watch(
  category,
  () => {
    page.value = 0
    void load()
  },
  { immediate: true },
)
function changePage(next: number) {
  page.value = next
  void load()
}
</script>
<template>
  <section class="official-page">
    <header class="official-masthead">
      <div>
        <p class="official-eyebrow">麦麦官方 · 信息与指南</p>
        <h1>每一笔交易，<br />都更明白一点。</h1>
        <p>了解平台的新消息，也读懂买卖闲置时需要留意的小事。</p>
      </div>
      <div class="official-stamp" aria-hidden="true">
        <span>麦麦</span><small>编辑部</small>
      </div>
    </header>
    <nav class="official-filter" aria-label="官方内容分类">
      <RouterLink
        :to="{ path: '/official' }"
        :aria-current="!category ? 'page' : undefined"
        >全部内容</RouterLink
      ><RouterLink
        v-for="(label, key) in categories"
        :key="key"
        :to="{ path: '/official', query: { category: key } }"
        :aria-current="category === key ? 'page' : undefined"
        >{{ label }}</RouterLink
      >
    </nav>
    <p v-if="loading" role="status" class="official-empty">正在整理内容…</p>
    <div v-else-if="error" role="alert" class="official-empty">
      <p>{{ error }}</p>
      <button @click="load">重新加载</button>
    </div>
    <div v-else-if="!articles.length" class="official-empty">
      这个栏目暂时没有已发布内容。
    </div>
    <div v-else class="official-content-grid">
      <div class="official-stories">
        <article
          v-for="(article, index) in articles"
          :key="article.id"
          class="official-story"
        >
          <span class="official-number" aria-hidden="true">{{
            String(page * 10 + index + 1).padStart(2, '0')
          }}</span>
          <div>
            <div class="official-meta">
              <span>{{ categories[article.category] }}</span
              ><time>{{ articleDate(article.publishedAt) }}</time>
            </div>
            <h2>
              <RouterLink :to="`/official/${article.slug}`">{{
                article.title
              }}</RouterLink>
            </h2>
            <p>{{ article.summary }}</p>
            <RouterLink class="official-read" :to="`/official/${article.slug}`"
              >阅读全文 <span aria-hidden="true">↗</span></RouterLink
            >
          </div>
        </article>
        <MmPagination
          :page="page"
          :total-pages="totalPages"
          @change="changePage"
        />
      </div>
      <aside class="official-note">
        <span>交易之前</span>
        <h2>先沟通清楚，<br />再放心下单。</h2>
        <p>
          核对商品成色、交付方式和运费，保留站内沟通记录。遇到问题，随时查看指南或联系人工客服。
        </p>
        <RouterLink to="/support">进入帮助中心 →</RouterLink
        ><RouterLink to="/policies">阅读交易与售后规则 →</RouterLink>
      </aside>
    </div>
  </section>
</template>
<style src="./official.css"></style>
