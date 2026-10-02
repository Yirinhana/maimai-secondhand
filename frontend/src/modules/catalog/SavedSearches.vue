<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { get, post, patch, del } from '../../shared/api';
import { useAuthStore } from '../../shared/stores/auth';
import { askConfirmation } from '../../shared/confirm';
interface Criteria {
  keyword?: string;
  categoryId?: string | number;
  minPriceCents?: number;
  maxPriceCents?: number;
  region?: string;
  condition?: string;
  deliveryMethod?: string;
}
interface Saved extends Criteria {
  id: number;
  name: string;
  enabled: boolean;
}
const props = defineProps<{ criteria?: Criteria }>();
const auth = useAuthStore(),
  items = ref<Saved[]>([]),
  error = ref(''),
  name = ref(''),
  busy = ref(false),
  expanded = ref(false),
  notice = ref('');
async function load() {
  if (!auth.me) return;
  try {
    items.value = await get('/me/searches');
  } catch (e) {
    error.value = (e as Error).message;
  }
}
async function save() {
  busy.value = true;
  error.value = '';
  try {
    items.value = await post('/me/searches', {
      keyword: props.criteria?.keyword,
      region: props.criteria?.region,
      minPriceCents: props.criteria?.minPriceCents,
      maxPriceCents: props.criteria?.maxPriceCents,
      condition: props.criteria?.condition,
      deliveryMethod: props.criteria?.deliveryMethod,
      categoryId: props.criteria?.categoryId
        ? Number(props.criteria.categoryId)
        : null,
      name: name.value.trim() || props.criteria?.keyword || '我的找货条件',
    });
    name.value = '';
    notice.value = '找货条件已保存，新的匹配商品会通过站内通知提醒你。';
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    busy.value = false;
  }
}
async function toggle(item: Saved) {
  busy.value = true;
  try {
    await patch(`/me/searches/${item.id}`, { enabled: !item.enabled });
    item.enabled = !item.enabled;
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    busy.value = false;
  }
}
async function remove(item: Saved) {
  if (!(await askConfirmation(`移除「${item.name}」及其找货提醒？`))) return;
  busy.value = true;
  try {
    await del(`/me/searches/${item.id}`);
    await load();
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    busy.value = false;
  }
}
function query(s: Saved) {
  return {
    keyword: s.keyword || undefined,
    categoryId: s.categoryId || undefined,
    minPrice: s.minPriceCents == null ? undefined : s.minPriceCents / 100,
    maxPrice: s.maxPriceCents == null ? undefined : s.maxPriceCents / 100,
    region: s.region || undefined,
    condition: s.condition || undefined,
    deliveryMethod: s.deliveryMethod || undefined,
  };
}
onMounted(load);
</script>
<template>
  <section class="saved-searches">
    <button
      class="saved-toggle"
      :aria-expanded="expanded"
      @click="expanded = !expanded"
    >
      ♡ 帮我留意好物
      <span>{{
        items.length
          ? `已保存 ${items.length} 个条件`
          : '保存条件，有新货时提醒'
      }}</span>
    </button>
    <div v-if="expanded" class="mm-panel mm-stack">
      <p class="mm-muted">
        只提醒保存之后上架或更新的有货商品，每小时最多3条站内提醒。可以随时暂停或移除；不保存附近定位坐标。
      </p>
      <RouterLink v-if="!auth.me" to="/login?redirect=/search"
        >登录后保存找货条件</RouterLink
      ><template v-else
        ><form v-if="criteria" class="saved-form" @submit.prevent="save">
          <label
            >给当前筛选取个名字<input
              v-model="name"
              maxlength="80"
              :placeholder="
                criteria.keyword || '例如：预算500元的书桌'
              " /></label
          ><button type="submit" :disabled="busy">保存当前条件</button>
        </form>
        <p v-if="notice" role="status">{{ notice }}</p>
        <p v-if="!items.length" class="mm-muted">还没有保存过找货条件。</p>
        <article v-for="item in items" :key="item.id">
          <RouterLink :to="{ path: '/search', query: query(item) }"
            >{{ item.name }} →</RouterLink
          ><span>{{ item.enabled ? '提醒开启' : '提醒已暂停' }}</span
          ><button :disabled="busy" @click="toggle(item)">
            {{ item.enabled ? '暂停' : '开启' }}</button
          ><button :disabled="busy" @click="remove(item)">移除</button>
        </article></template
      >
      <p v-if="error" class="mm-error" role="alert">{{ error }}</p>
    </div>
  </section>
</template>
<style scoped>
.saved-searches {
  margin: 16px 0;
}
.saved-toggle {
  padding: 14px 18px;
  border: 1px solid var(--mm-border);
  border-radius: 12px;
  background: white;
  text-align: left;
  color: var(--mm-primary);
  cursor: pointer;
  width: 100%;
  font: inherit;
}
.saved-toggle span {
  font-size: 12px;
  margin-left: 12px;
  color: var(--mm-muted);
}
.saved-form {
  display: flex;
  align-items: end;
  flex-wrap: wrap;
  gap: 12px;
}
.saved-form label {
  display: grid;
  gap: 8px;
  flex: 1;
  min-width: 160px;
}
.saved-form input {
  padding: 10px;
  min-width: 0;
  width: 100%;
  box-sizing: border-box;
}
.saved-searches article {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  border-top: 1px solid var(--mm-border);
  padding: 12px 0;
}
.saved-searches article a {
  flex: 1;
  min-width: 140px;
  overflow-wrap: anywhere;
}
.saved-searches article span {
  font-size: 12px;
  color: var(--mm-muted);
}
.saved-searches button {
  min-height: 40px;
}
</style>
