<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue';
import { get, post, put, type ApiError } from '../../shared/api';
import MmButton from '../../shared/components/MmButton.vue';
interface CategoryItem {
  id: number;
  parentId: number | null;
  name: string;
  sort: number;
  status: 'ACTIVE' | 'DISABLED';
}
const items = ref<CategoryItem[]>([]),
  editing = ref<number | null>(null),
  showForm = ref(false),
  loading = ref(false),
  busy = ref(false),
  error = ref(''),
  hint = ref('');
const blank = () => ({
  parentId: '' as string,
  name: '',
  sort: 0,
  status: 'ACTIVE' as 'ACTIVE' | 'DISABLED',
  reason: '',
});
const form = ref(blank());
const editor = ref<HTMLFormElement | null>(null);
const parentChoices = computed(() =>
  items.value.filter((item) => item.id !== editing.value),
);
function categoryName(item: CategoryItem) {
  const names = [item.name],
    visited = new Set<number>([item.id]);
  let parentId = item.parentId;
  while (parentId !== null && !visited.has(parentId)) {
    const parent = items.value.find((c) => c.id === parentId);
    if (!parent) break;
    visited.add(parentId);
    names.unshift(parent.name);
    parentId = parent.parentId;
  }
  return names.join(' / ');
}
async function load() {
  loading.value = true;
  error.value = '';
  try {
    items.value = await get<CategoryItem[]>('/admin/categories');
  } catch (e) {
    error.value = (e as ApiError).message;
  } finally {
    loading.value = false;
  }
}
function edit(item?: CategoryItem) {
  editing.value = item?.id ?? null;
  form.value = item
    ? {
        parentId: item.parentId === null ? '' : String(item.parentId),
        name: item.name,
        sort: item.sort,
        status: item.status,
        reason: '',
      }
    : blank();
  showForm.value = true;
  hint.value = '';
  void nextTick(() => {
    editor.value?.scrollIntoView({ block: 'nearest' });
    editor.value?.querySelector('input')?.focus({ preventScroll: true });
  });
}
async function save() {
  busy.value = true;
  error.value = '';
  try {
    const payload = {
      parentId: form.value.parentId ? Number(form.value.parentId) : null,
      name: form.value.name.trim(),
      sort: Number(form.value.sort),
      status: form.value.status,
      reason: form.value.reason.trim(),
    };
    if (editing.value) await put(`/admin/categories/${editing.value}`, payload);
    else await post('/admin/categories', payload);
    showForm.value = false;
    hint.value = '分类已保存，停用分类及其子类不可用于新发布。';
    await load();
  } catch (e) {
    error.value = (e as ApiError).message;
  } finally {
    busy.value = false;
  }
}
onMounted(load);
</script>
<template>
  <section class="categories mm-stack">
    <header class="mm-page-heading">
      <div>
        <p class="mm-eyebrow">商品归类</p>
        <h1>分类维护</h1>
        <p>管理大类与子分类，让买家更快找到需要的物品。</p>
      </div>
      <div class="mm-actions">
        <MmButton variant="ghost" :disabled="loading" @click="load"
          >刷新</MmButton
        ><MmButton @click="edit()">新增分类</MmButton>
      </div>
    </header>
    <p class="categories__notice">
      停用会保留分类与历史商品；父分类停用后，子类也不可用于新发布或提交审核。
    </p>
    <p v-if="error" class="mm-error" role="alert">{{ error }}</p>
    <p v-if="hint" class="mm-success-feedback" role="status">{{ hint }}</p>
    <form
      v-if="showForm"
      ref="editor"
      class="category-editor"
      @submit.prevent="save"
    >
      <header>
        <h2>{{ editing ? '编辑分类' : '新增分类' }}</h2>
        <span>填写分类信息并说明本次变更原因</span>
      </header>
      <label
        >分类名称<input v-model="form.name" required maxlength="50"
      /></label>
      <label
        >所属父分类<select v-model="form.parentId">
          <option value="">顶级分类</option>
          <option
            v-for="item in parentChoices"
            :key="item.id"
            :value="String(item.id)"
          >
            {{ categoryName(item)
            }}{{ item.status === 'DISABLED' ? '（已停用）' : '' }}
          </option>
        </select></label
      >
      <label
        >排序值<input
          v-model.number="form.sort"
          type="number"
          min="0"
          step="1"
          required
      /></label>
      <label
        >状态<select v-model="form.status">
          <option value="ACTIVE">启用</option>
          <option value="DISABLED">停用</option>
        </select></label
      >
      <label class="category-editor__wide"
        >变更原因<textarea
          v-model="form.reason"
          required
          maxlength="500"
          rows="3"
        />
      </label>
      <footer class="category-editor__wide">
        <MmButton variant="ghost" :disabled="busy" @click="showForm = false"
          >取消</MmButton
        ><MmButton type="submit" :loading="busy">保存分类</MmButton>
      </footer>
    </form>
    <p v-if="loading" role="status">正在读取分类…</p>
    <div v-else class="category-list">
      <div class="category-list__head" aria-hidden="true">
        <span>分类与所属层级</span><span>使用状态</span><span>排序</span
        ><span>管理</span>
      </div>
      <article
        v-for="item in items"
        :key="item.id"
        class="category-row"
        :class="{ 'is-child': item.parentId !== null }"
      >
        <div class="category-row__name">
          <span class="category-row__kind">{{
            item.parentId === null ? '大类' : '子类'
          }}</span>
          <div>
            <h2>{{ item.name }}</h2>
            <p v-if="item.parentId !== null">{{ categoryName(item) }}</p>
          </div>
        </div>
        <span
          class="category-row__status"
          :class="{ 'is-disabled': item.status !== 'ACTIVE' }"
          >{{ item.status === 'ACTIVE' ? '使用中' : '已停用' }}</span
        >
        <span class="category-row__sort"
          ><span>排序 </span>{{ item.sort }}</span
        >
        <MmButton
          variant="ghost"
          :aria-label="`编辑${categoryName(item)}`"
          @click="edit(item)"
          >编辑</MmButton
        >
      </article>
      <div v-if="!items.length && !error" class="category-list__empty">
        <h2>还没有商品分类</h2>
        <p>先添加大类，再为它创建子分类。</p>
        <MmButton @click="edit()">添加第一个分类</MmButton>
      </div>
    </div>
  </section>
</template>
<style scoped>
.categories__notice {
  padding: 14px 18px;
  border-left: 3px solid var(--mm-zone-accent);
  background: var(--mm-zone-soft);
  border-radius: 8px;
  font-size: 14px;
  color: var(--mm-muted);
}
.category-editor {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
  padding: 24px;
  border: 1px solid var(--mm-zone-border);
  border-radius: 12px;
  background: white;
}
.category-editor > header,
.category-editor__wide {
  grid-column: 1/-1;
}
.category-editor > header {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 20px;
  align-items: center;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--mm-border);
}
.category-editor h2 {
  font-size: 19px;
}
.category-editor header span {
  font-size: 13px;
  color: var(--mm-muted);
}
.category-editor label {
  display: grid;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
}
.category-editor input,
.category-editor select,
.category-editor textarea {
  width: 100%;
  font-weight: 400;
}
.category-editor textarea {
  resize: vertical;
}
.category-editor footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding-top: 14px;
  border-top: 1px solid var(--mm-border);
}
.category-list {
  border: 1px solid var(--mm-zone-border);
  border-radius: 12px;
  background: white;
  overflow: hidden;
}
.category-list__head,
.category-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 100px 65px 72px;
  align-items: center;
  gap: 20px;
  padding: 16px 20px;
}
.category-list__head {
  background: var(--mm-zone-soft);
  color: var(--mm-muted);
  font-size: 12px;
  font-weight: 600;
}
.category-row + .category-row {
  border-top: 1px solid var(--mm-border);
}
.category-row:hover {
  background: var(--mm-zone-canvas);
}
.category-row__name {
  display: flex;
  gap: 12px;
  align-items: center;
  min-width: 0;
}
.category-row__name > div {
  min-width: 0;
  overflow-wrap: anywhere;
}
.category-row h2 {
  font-size: 15px;
}
.category-row__name p {
  color: var(--mm-muted);
  font-size: 12px;
  margin-top: 4px;
}
.category-row__kind {
  padding: 5px 7px;
  background: var(--mm-zone-soft);
  border: 1px solid var(--mm-zone-border);
  border-radius: 6px;
  flex-shrink: 0;
  font-size: 11px;
  color: var(--mm-zone-accent);
}
.category-row.is-child .category-row__kind {
  background: white;
  color: var(--mm-muted);
}
.category-row__status {
  justify-self: start;
  font-size: 12px;
  font-weight: 600;
  color: #28613c;
  background: #edf6ef;
  padding: 4px 8px;
  border-radius: 6px;
}
.category-row__status.is-disabled {
  color: #7c6754;
  background: #f1eee8;
}
.category-row__sort {
  font-size: 13px;
  color: var(--mm-muted);
  font-variant-numeric: tabular-nums;
}
.category-row__sort > span {
  display: none;
}
.category-row > button {
  justify-self: end;
}
.category-list__empty {
  display: grid;
  justify-items: center;
  gap: 12px;
  text-align: center;
  padding: 36px 20px;
}
.category-list__empty h2 {
  font-size: 18px;
}
.category-list__empty p {
  color: var(--mm-muted);
}
@media (max-width: 650px) {
  .category-editor {
    grid-template-columns: 1fr;
    padding: 16px;
  }
  .category-list__head {
    display: none;
  }
  .category-row {
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 12px;
    padding: 16px;
  }
  .category-row__name {
    grid-column: 1/-1;
  }
  .category-row__sort {
    grid-column: 1;
  }
  .category-row__sort > span {
    display: inline;
  }
  .category-row > button {
    grid-column: 2;
    grid-row: 2/4;
  }
  .category-row__status {
    grid-row: 2;
  }
}
</style>
