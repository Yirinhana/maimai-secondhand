<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { get, post, put, type ApiError } from '../../shared/api'
import { useAuthStore } from '../../shared/stores/auth'
import type { Page } from '../../shared/types'
import { askConfirmation } from '../../shared/confirm'
import MmButton from '../../shared/components/MmButton.vue'
import MmPagination from '../../shared/components/MmPagination.vue'
import {
  categories,
  statuses,
  articleDate,
  type ArticleCategory,
  type ArticleStatus,
  type OfficialArticle,
} from './official'
const auth = useAuthStore(),
  canManage = computed(() =>
    auth.me?.roles.some((r) => ['OPERATOR', 'SUPER_ADMIN'].includes(r)),
  )
const articles = ref<OfficialArticle[]>([]),
  page = ref(0),
  totalPages = ref(0),
  filter = ref(''),
  loading = ref(false),
  busy = ref(false),
  error = ref(''),
  success = ref(''),
  editing = ref(false),
  articleId = ref<number | null>(null)
const form = reactive({
  slug: '',
  title: '',
  summary: '',
  body: '',
  category: 'NOTICE' as ArticleCategory,
  status: 'DRAFT' as ArticleStatus,
})
let loadSequence = 0
async function load() {
  if (!canManage.value) return
  const ticket = ++loadSequence
  loading.value = true
  error.value = ''
  try {
    const data = await get<Page<OfficialArticle>>('/admin/official/articles', {
      page: page.value,
      size: 12,
      status: filter.value,
    })
    if (ticket === loadSequence) {
      articles.value = data.content
      totalPages.value = data.totalPages
    }
  } catch (e) {
    if (ticket === loadSequence) error.value = (e as ApiError).message
  } finally {
    if (ticket === loadSequence) loading.value = false
  }
}
function edit(article?: OfficialArticle) {
  articleId.value = article?.id ?? null
  Object.assign(form, {
    slug: article?.slug ?? '',
    title: article?.title ?? '',
    summary: article?.summary ?? '',
    body: article?.body ?? '',
    category: article?.category ?? 'NOTICE',
    status: article?.status ?? 'DRAFT',
  })
  editing.value = true
  error.value = ''
  success.value = ''
}
async function save() {
  if (busy.value) return
  const payload = { ...form }
  if (
    payload.status === 'PUBLISHED' &&
    !(await askConfirmation(
      '确认发布这篇官方内容？保存后会立即在官方动态展示。',
    ))
  )
    return
  busy.value = true
  error.value = ''
  success.value = ''
  try {
    if (articleId.value)
      await put(`/admin/official/articles/${articleId.value}`, payload)
    else await post('/admin/official/articles', payload)
    editing.value = false
    success.value =
      payload.status === 'PUBLISHED'
        ? '官方内容已发布'
        : payload.status === 'WITHDRAWN'
          ? '官方内容已撤回'
          : '草稿已保存'
    await load()
  } catch (e) {
    error.value = (e as ApiError).message || '保存失败'
  } finally {
    busy.value = false
  }
}
function changePage(next: number) {
  page.value = next
  void load()
}
watch(canManage, (allowed) => {
  if (allowed) void load()
  else {
    loadSequence++
    articles.value = []
    totalPages.value = 0
    editing.value = false
    loading.value = false
  }
}, { immediate: true })
</script>
<template>
  <section class="official-admin">
    <header class="official-admin-heading">
      <div>
        <p class="official-eyebrow">平台内容工作台</p>
        <h1>官方内容管理</h1>
        <p>把平台公告和使用指南写清楚，发布给每一位用户。</p>
      </div>
      <MmButton v-if="canManage" @click="edit()">新建官方内容</MmButton>
    </header>
    <p v-if="!canManage" role="alert">只有运营与超级管理员可以管理官方内容。</p>
    <template v-else
      ><p v-if="error" role="alert" class="mm-error">{{ error }}</p>
      <p v-if="success" role="status">{{ success }}</p>
      <form v-if="editing" class="official-editor" @submit.prevent="save">
        <div class="official-editor-title">
          <h2>{{ articleId ? '编辑官方内容' : '撰写新内容' }}</h2>
          <button type="button" :disabled="busy" @click="editing = false">
            关闭编辑
          </button>
        </div>
        <div class="official-editor-fields">
          <label
            >标题<input v-model="form.title" required maxlength="120" /></label
          ><label
            >链接标识<input
              v-model="form.slug"
              required
              pattern="[a-z0-9]+(?:-[a-z0-9]+)*"
              maxlength="80"
              placeholder="例如：how-to-buy" /></label
          ><label
            >栏目<select v-model="form.category">
              <option
                v-for="(label, key) in categories"
                :key="key"
                :value="key"
              >
                {{ label }}
              </option>
            </select></label
          ><label
            >发布状态<select v-model="form.status">
              <option v-for="(label, key) in statuses" :key="key" :value="key">
                {{ label }}
              </option>
            </select></label
          >
        </div>
        <label
          >内容摘要<textarea
            v-model="form.summary"
            required
            maxlength="300"
            rows="2"
          /></label
        ><label
          >正文<textarea
            v-model="form.body"
            required
            maxlength="20000"
            rows="14"
            placeholder="使用清楚的文字与空行分段。请勿填写未经核实的服务承诺。"
          />
        </label>
        <p class="mm-muted">
          正文按纯文本展示。发布前请核对事实；撤回后不再公开显示，记录仍保留。
        </p>
        <MmButton type="submit" :loading="busy">保存内容</MmButton>
      </form>
      <div class="official-table-tools">
        <label
          >按状态查看
          <select v-model="filter" @change="changePage(0)">
            <option value="">全部状态</option>
            <option v-for="(label, key) in statuses" :key="key" :value="key">
              {{ label }}
            </option>
          </select></label
        >
      </div>
      <p v-if="loading" role="status">正在读取内容…</p>
      <div v-else class="official-admin-list">
        <article v-for="article in articles" :key="article.id">
          <div>
            <span
              >{{ categories[article.category] }} ·
              {{ article.status ? statuses[article.status] : '' }}</span
            >
            <h2>{{ article.title }}</h2>
            <p>{{ article.summary }}</p>
            <small>更新于 {{ articleDate(article.updatedAt) }}</small>
          </div>
          <div class="official-admin-actions">
            <RouterLink
              v-if="article.status === 'PUBLISHED'"
              :to="`/official/${article.slug}`"
              >查看公开页</RouterLink
            ><MmButton variant="ghost" @click="edit(article)"
              >编辑内容</MmButton
            >
          </div>
        </article>
        <p v-if="!articles.length">当前筛选下没有内容。</p>
      </div>
      <MmPagination :page="page" :total-pages="totalPages" @change="changePage"
    /></template>
  </section>
</template>
<style src="./official.css"></style>
