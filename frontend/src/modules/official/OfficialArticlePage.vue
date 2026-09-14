<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { get, type ApiError } from '../../shared/api'
import { categories, articleDate, type OfficialArticle } from './official'
const route = useRoute(),
  article = ref<OfficialArticle | null>(null),
  loading = ref(true),
  error = ref('')
const paragraphs = computed(
  () => article.value?.body?.split(/\n\s*\n/).filter(Boolean) || [],
)
let sequence = 0
async function load() {
  const ticket = ++sequence
  loading.value = true
  error.value = ''
  article.value = null
  try {
    const result = await get<OfficialArticle>(
      `/official/articles/${encodeURIComponent(String(route.params.slug))}`,
    )
    if (ticket === sequence) article.value = result
  } catch (e) {
    if (ticket === sequence)
      error.value = (e as ApiError).message || '此内容暂不可用或已撤回'
  } finally {
    if (ticket === sequence) loading.value = false
  }
}
watch(
  () => route.params.slug,
  () => void load(),
  { immediate: true },
)
</script>
<template>
  <section class="official-page official-reader">
    <RouterLink class="official-back" to="/official">← 返回官方动态</RouterLink>
    <p v-if="loading" role="status">正在加载正文…</p>
    <div v-else-if="error" role="alert">
      <h1>内容暂不可用</h1>
      <p>{{ error }}</p>
    </div>
    <article v-else-if="article">
      <header>
        <div class="official-meta">
          <span>{{ categories[article.category] }}</span
          ><time>{{ articleDate(article.publishedAt) }}</time>
        </div>
        <h1>{{ article.title }}</h1>
        <p class="official-summary">{{ article.summary }}</p>
        <div class="official-byline">
          <span class="official-author-mark" aria-hidden="true">麦</span>
          <div>
            <strong>{{ article.publisher || '麦麦官方' }}</strong
            ><small>更新于 {{ articleDate(article.updatedAt) }}</small>
          </div>
        </div>
      </header>
      <div class="official-prose">
        <p v-for="(paragraph, index) in paragraphs" :key="index">
          {{ paragraph }}
        </p>
      </div>
      <footer>
        <p>有疑问，别着急。</p>
        <RouterLink to="/support">查看常见问题或提交人工工单 →</RouterLink>
      </footer>
    </article>
  </section>
</template>
<style src="./official.css"></style>
