<script setup lang="ts">
import { askConfirmation } from '../../shared/confirm';
import { nextTick, onMounted, reactive, ref, watch } from 'vue';
import { parseYuan } from '../../shared/moneyInput';
import { useRoute, useRouter } from 'vue-router';
import { get, post, put, type ApiError } from '../../shared/api';
import { useAuthStore } from '../../shared/stores/auth';
import { formatTime } from '../../shared/format';
import { moderationText, type CommunityPage, type Demand } from './types';
import MmButton from '../../shared/components/MmButton.vue';
import MmPagination from '../../shared/components/MmPagination.vue';
import PriceText from '../../shared/components/PriceText.vue';
import DemandReplies from './DemandReplies.vue';
import ReportButton from './ReportButton.vue';
import UserAvatar from '../../shared/components/UserAvatar.vue';
const route = useRoute(),
  router = useRouter(),
  auth = useAuthStore(),
  mine = ref(route.query.mine === '1'),
  items = ref<Demand[]>([]),
  page = ref(0),
  pages = ref(0),
  loading = ref(false),
  busy = ref(false),
  error = ref(''),
  hint = ref(''),
  expanded = ref<number | null>(null),
  showForm = ref(false),
  editId = ref<number | null>(null);
const blank = () => ({
    title: '',
    description: '',
    budgetMin: '0',
    budgetMax: '100',
    region: '',
  }),
  form = ref(blank());
const formElement = ref<HTMLFormElement | null>(null);
const fieldErrors = reactive({
  title: '',
  description: '',
  budgetMin: '',
  budgetMax: '',
});
async function load() {
  loading.value = true;
  error.value = '';
  try {
    if (mine.value && !auth.me) {
      await router.push({
        path: '/login',
        query: { redirect: '/community/demands?mine=1' },
      });
      return;
    }
    if (route.query.demandId && !mine.value) {
      const demand = await get<Demand>(
        `/community/demands/${encodeURIComponent(String(route.query.demandId))}`,
      );
      items.value = [demand];
      pages.value = 1;
      expanded.value = demand.id;
      return;
    }
    const r = await get<CommunityPage<Demand>>(
      `/community/demands${mine.value ? '/me' : ''}`,
      { page: page.value, size: 12 },
    );
    items.value = r.items;
    pages.value = r.totalPages;
  } catch (e) {
    error.value = (e as ApiError).message;
  } finally {
    loading.value = false;
  }
}
function edit(item?: Demand) {
  editId.value = item?.id ?? null;
  form.value = item
    ? {
        title: item.title,
        description: item.description,
        budgetMin: (item.budgetMinCents / 100).toFixed(2),
        budgetMax: (item.budgetMaxCents / 100).toFixed(2),
        region: item.region || '',
      }
    : blank();
  Object.assign(fieldErrors, {
    title: '',
    description: '',
    budgetMin: '',
    budgetMax: '',
  });
  error.value = '';
  showForm.value = true;
}
async function submit() {
  if (busy.value) return;
  error.value = '';
  const min = parseYuan(form.value.budgetMin),
    max = parseYuan(form.value.budgetMax);
  fieldErrors.title = form.value.title.trim() ? '' : '请填写想要的物品';
  fieldErrors.description = form.value.description.trim()
    ? ''
    : '请补充具体需求';
  fieldErrors.budgetMin =
    min === null
      ? '最低预算须为非负金额，最多两位小数'
      : min > 100_000_000
        ? '最低预算不能超过 100 万元'
        : '';
  fieldErrors.budgetMax =
    max === null
      ? '最高预算须为非负金额，最多两位小数'
      : max > 100_000_000
        ? '最高预算不能超过 100 万元'
        : min !== null && max < min
          ? '最高预算不能低于最低预算'
          : '';
  if (Object.values(fieldErrors).some(Boolean)) {
    await nextTick();
    formElement.value
      ?.querySelector<HTMLInputElement>('[aria-invalid="true"]')
      ?.focus();
    return;
  }
  busy.value = true;
  try {
    const payload = {
      title: form.value.title,
      description: form.value.description,
      budgetMinCents: min,
      budgetMaxCents: max,
      region: form.value.region,
      categoryId: null,
    };
    if (editId.value) await put(`/community/demands/${editId.value}`, payload);
    else await post('/community/demands', payload);
    showForm.value = false;
    hint.value = '求购已提交审核，审核通过后将在求购广场展示。';
    mine.value = true;
    await load();
  } catch (e) {
    if ((e as ApiError).code === 'BUDGET_INVALID') {
      fieldErrors.budgetMax = (e as ApiError).message;
      await nextTick();
      formElement.value
        ?.querySelector<HTMLInputElement>('[aria-invalid="true"]')
        ?.focus();
    } else error.value = (e as ApiError).message;
  } finally {
    busy.value = false;
  }
}
async function close(id: number) {
  if (!(await askConfirmation('已找到合适物品，确认关闭求购？'))) return;
  busy.value = true;
  try {
    await post(`/community/demands/${id}/close`);
    await load();
  } catch (e) {
    error.value = (e as ApiError).message;
  } finally {
    busy.value = false;
  }
}
async function contact(id: number) {
  busy.value = true;
  try {
    const r = await post<{ id: number }>('/messages/conversations', {
      recipientId: id,
    });
    await router.push(`/messages/${r.id}`);
  } catch (e) {
    error.value = (e as ApiError).message;
  } finally {
    busy.value = false;
  }
}
watch(
  () => route.query.demandId,
  () => {
    page.value = 0;
    void load();
  },
);
watch(mine, () => {
  page.value = 0;
  void load();
});
onMounted(async () => {
  if (!auth.meLoaded) await auth.fetchMe();
  await load();
});
</script>
<template>
  <section class="mm-page mm-demand-board">
    <header class="mm-demand-board__intro">
      <p class="mm-eyebrow">THE WANTED BOARD</p>
      <h1>说说你在找什么。</h1>
      <p>一张求购帖，让手中的闲置和心里的需要相遇。</p>
      <span>求购广场</span>
    </header>
    <nav class="mm-actions">
      <MmButton :variant="!mine ? 'primary' : 'ghost'" @click="mine = false"
        >大家在找</MmButton
      ><MmButton :variant="mine ? 'primary' : 'ghost'" @click="mine = true"
        >我的求购</MmButton
      ><MmButton v-if="auth.me" variant="ghost" @click="edit()"
        >发布求购</MmButton
      ><RouterLink v-else to="/login">登录后发布</RouterLink>
    </nav>
    <p class="mm-muted" style="font-size: 12px">
      部分求购与回复由体验账号发布，用于展示交流流程。
    </p>
    <p v-if="error" class="mm-error" role="alert">{{ error }}</p>
    <p v-if="hint" class="mm-notice" role="status">{{ hint }}</p>
    <form
      v-if="showForm"
      ref="formElement"
      class="mm-panel mm-form"
      novalidate
      @submit.prevent="submit"
    >
      <h2>{{ editId ? '编辑求购' : '发布求购' }}</h2>
      <label
        >想要什么<input
          v-model="form.title"
          :aria-invalid="!!fieldErrors.title"
          :aria-describedby="
            fieldErrors.title ? 'demand-title-error' : undefined
          "
          @input="fieldErrors.title = ''"
          required
          maxlength="120"
        /><span
          v-if="fieldErrors.title"
          id="demand-title-error"
          class="mm-error"
          role="alert"
          >{{ fieldErrors.title }}</span
        ></label
      ><label
        >具体需求<textarea
          v-model="form.description"
          :aria-invalid="!!fieldErrors.description"
          :aria-describedby="
            fieldErrors.description ? 'demand-description-error' : undefined
          "
          @input="fieldErrors.description = ''"
          required
          maxlength="800"
        /><span
          v-if="fieldErrors.description"
          id="demand-description-error"
          class="mm-error"
          role="alert"
          >{{ fieldErrors.description }}</span
        >
      </label>
      <div class="mm-grid">
        <label
          >最低预算（元）<input
            v-model="form.budgetMin"
            :aria-invalid="!!fieldErrors.budgetMin"
            :aria-describedby="
              fieldErrors.budgetMin ? 'demand-budgetMin-error' : undefined
            "
            @input="fieldErrors.budgetMin = ''"
            type="text"
            inputmode="decimal"
            min="0"
            step="0.01"
            required
          /><span
            v-if="fieldErrors.budgetMin"
            id="demand-budgetMin-error"
            class="mm-error"
            role="alert"
            >{{ fieldErrors.budgetMin }}</span
          ></label
        ><label
          >最高预算（元）<input
            v-model="form.budgetMax"
            :aria-invalid="!!fieldErrors.budgetMax"
            :aria-describedby="
              fieldErrors.budgetMax ? 'demand-budgetMax-error' : undefined
            "
            @input="fieldErrors.budgetMax = ''"
            type="text"
            inputmode="decimal"
            min="0"
            step="0.01"
            required
          /><span
            v-if="fieldErrors.budgetMax"
            id="demand-budgetMax-error"
            class="mm-error"
            role="alert"
            >{{ fieldErrors.budgetMax }}</span
          ></label
        >
      </div>
      <label>地区<input v-model="form.region" maxlength="100" /></label>
      <div class="mm-actions">
        <MmButton type="submit" :loading="busy">提交审核</MmButton
        ><MmButton variant="ghost" @click="showForm = false">取消</MmButton>
      </div>
    </form>
    <p v-if="loading">加载中…</p>
    <article v-for="d in items" :key="d.id" class="mm-panel mm-demand-post">
      <div class="mm-actions">
        <h2>{{ d.title }}</h2>
        <span v-if="mine" class="mm-chip">{{
          d.isClosed ? '已关闭' : moderationText[d.status] || d.status
        }}</span>
      </div>
      <p style="white-space: pre-wrap">{{ d.description }}</p>
      <p>
        预算 <PriceText :cents="d.budgetMinCents" /> —
        <PriceText :cents="d.budgetMaxCents" /> · {{ d.region || '不限地区' }}
      </p>
      <div class="mm-demand-author">
        <UserAvatar
          :src="d.authorAvatarUrl"
          :nickname="d.authorNickname"
          :size="32"
        /><RouterLink :to="`/sellers/${d.authorId}`">{{
          d.authorNickname
        }}</RouterLink
        ><time>{{ formatTime(d.createdAt) }}</time>
      </div>
      <p v-if="mine && d.reviewReason">审核说明：{{ d.reviewReason }}</p>
      <div class="mm-actions">
        <RouterLink
          v-if="d.authorId === auth.me?.id && !d.isClosed"
          :to="{
            path: '/search',
            query: {
              categoryId: d.categoryId || undefined,
              minPrice: d.budgetMinCents / 100,
              maxPrice: d.budgetMaxCents / 100,
              region: d.region || undefined,
            },
          }"
          >按这份预算找货与设置提醒 →</RouterLink
        >
        <MmButton
          variant="ghost"
          @click="expanded = expanded === d.id ? null : d.id"
          >{{ expanded === d.id ? '收起回复' : '查看回复' }}</MmButton
        ><template v-if="d.authorId === auth.me?.id && !d.isClosed"
          ><MmButton variant="ghost" @click="edit(d)">编辑</MmButton
          ><MmButton variant="ghost" :disabled="busy" @click="close(d.id)"
            >关闭求购</MmButton
          ></template
        ><template v-else-if="d.authorId !== auth.me?.id"
          ><MmButton
            variant="ghost"
            :disabled="busy"
            @click="contact(d.authorId)"
            >联系发布者</MmButton
          ><ReportButton resource-type="DEMAND_POST" :resource-id="d.id"
        /></template>
      </div>
      <DemandReplies
        v-if="expanded === d.id"
        :demand-id="d.id"
        :closed="d.isClosed"
      />
    </article>
    <p v-if="!loading && !items.length" class="mm-panel mm-muted">
      暂无求购信息，试着发布你的需求吧。
    </p>
    <MmPagination
      :page="page"
      :total-pages="pages"
      @change="
        page = $event;
        load();
      "
    />
  </section>
</template>
<style scoped>
.mm-form [aria-invalid='true'] {
  border-color: var(--mm-danger);
}
.mm-form .mm-error {
  font-size: 13px;
  line-height: 1.6;
}
.mm-demand-author {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  color: var(--mm-muted);
  margin: 18px 0;
}
.mm-demand-author strong {
  color: var(--mm-ink);
  font-weight: 500;
}
.mm-demand-author time {
  margin-left: auto;
  font-size: 11px;
}

.mm-demand-board {
  max-width: 1130px;
  padding-top: 32px;
  gap: 23px;
}
.mm-demand-board__intro {
  position: relative;
  background: var(--mm-accent-soft);
  padding: 35px 36px;
  border-radius: 3px;
  border-bottom: 3px solid #879473;
}
.mm-demand-board__intro h1 {
  font-size: 34px;
  letter-spacing: -1px;
  margin: 12px 0;
}
.mm-demand-board__intro > p:not(.mm-eyebrow) {
  font-size: 13px;
  color: var(--mm-muted);
}
.mm-demand-board__intro > span {
  position: absolute;
  right: 30px;
  top: 30px;
  border: 1px solid #a4b092;
  border-radius: 50%;
  width: 68px;
  height: 68px;
  display: flex;
  justify-content: center;
  align-items: center;
  font-size: 12px;
  color: var(--mm-primary);
  transform: rotate(12deg);
}
.mm-demand-board > nav {
  padding: 0 0 20px;
  border-bottom: 1px solid var(--mm-border);
}
.mm-demand-board > nav .mm-button {
  font-size: 12px;
  min-height: 36px;
}
.mm-demand-post {
  position: relative;
  padding: 23px 26px 23px 76px;
  border: 0;
  border-bottom: 1px solid var(--mm-border);
  border-radius: 0;
  background: #fff;
}
.mm-demand-post::before {
  content: '求';
  position: absolute;
  left: 24px;
  top: 25px;
  color: var(--mm-primary);
  border: 1px solid #d4dcc9;
  background: var(--mm-canvas);
  border-radius: 4px;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;
}
.mm-demand-post h2 {
  font-size: 20px;
}
.mm-demand-post > p {
  font-size: 13px;
  overflow-wrap: anywhere;
  line-height: 1.85;
}
.mm-demand-post > .mm-actions:last-of-type {
  gap: 17px;
  margin-top: 6px;
}
.mm-demand-post .mm-button {
  font-size: 11px;
  min-height: 31px;
  padding: 0 10px;
}
.mm-demand-post > .mm-muted {
  font-size: 11px;
}
@media (max-width: 760px) {
  .mm-demand-board__intro {
    padding: 25px 22px;
  }
  .mm-demand-board__intro h1 {
    font-size: 27px;
  }
  .mm-demand-board__intro > span {
    display: none;
  }
  .mm-demand-post {
    padding: 18px 18px 18px 59px;
  }
  .mm-demand-post::before {
    left: 15px;
    top: 22px;
    width: 29px;
    height: 29px;
  }
  .mm-demand-post h2 {
    font-size: 17px;
  }
  .mm-demand-post > .mm-actions {
    gap: 10px;
  }
  .mm-demand-board > nav {
    gap: 9px;
  }
}
</style>
