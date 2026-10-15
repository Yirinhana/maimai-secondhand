<script setup lang="ts">
import GovernanceNotice from '../../shared/components/GovernanceNotice.vue';
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
async function edit(item?: Demand) {
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
  await nextTick();
  formElement.value?.scrollIntoView({ block: 'start' });
  formElement.value
    ?.querySelector<HTMLInputElement>('input')
    ?.focus({ preventScroll: true });
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
      <div>
        <p class="mm-eyebrow">THE WANTED BOARD</p>
        <h1>求购社区</h1>
        <p>写下想找的物品、预算和要求，让合适的闲置找到你。</p>
      </div>
      <MmButton v-if="auth.me" @click="edit()">＋ 发布求购</MmButton>
      <RouterLink v-else class="mm-demand-board__login" to="/login"
        >登录后发布求购 →</RouterLink
      >
    </header>
    <nav class="mm-demand-board__tabs" aria-label="求购范围">
      <button type="button" :aria-pressed="!mine" @click="mine = false">
        大家在找
      </button>
      <button type="button" :aria-pressed="mine" @click="mine = true">
        我的求购
      </button>
    </nav>
    <p class="mm-muted" style="font-size: 12px">
      部分求购与回复由体验账号发布，用于展示交流流程。
    </p>
    <p v-if="error" class="mm-error" role="alert">{{ error }}</p>
    <p v-if="hint" class="mm-notice" role="status">{{ hint }}</p>
    <form
      v-if="showForm"
      ref="formElement"
      class="mm-panel mm-form mm-demand-form"
      novalidate
      @submit.prevent="submit"
    >
      <header class="mm-demand-form__heading">
        <div>
          <h2>{{ editId ? '编辑求购' : '发布求购' }}</h2>
          <p>描述越具体，越容易收到合适的回复。提交后会先审核。</p>
        </div>
        <span>填写需求 · 预算 · 地区</span>
      </header>
      <div class="mm-demand-form__layout">
        <div class="mm-demand-form__details">
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
        </div>
        <div class="mm-demand-form__preferences">
          <h3>预算与交付范围</h3>
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
          <label
            >地区<input
              v-model="form.region"
              maxlength="100"
              placeholder="如：上海市，不限地区可留空"
          /></label>
          <p class="mm-muted">
            可在需求中补充成色、配件和交付方式。不要公开手机号或详细住址。
          </p>
        </div>
      </div>
      <div class="mm-actions mm-demand-form__actions">
        <MmButton type="submit" :loading="busy">提交审核</MmButton
        ><MmButton variant="ghost" @click="showForm = false">取消</MmButton>
      </div>
      <GovernanceNotice />
    </form>
    <p v-if="loading">加载中…</p>
    <div class="mm-demand-board__posts">
      <article
        v-for="d in items"
        :key="d.id"
        class="mm-panel mm-demand-post"
        :class="{ 'is-expanded': expanded === d.id }"
      >
        <div class="mm-demand-post__heading">
          <h2>{{ d.title }}</h2>
          <span v-if="mine" class="mm-chip">{{
            d.isClosed ? '已关闭' : moderationText[d.status] || d.status
          }}</span>
        </div>
        <div class="mm-demand-post__facts">
          <div>
            <span>求购预算</span
            ><strong
              ><PriceText :cents="d.budgetMinCents" /> —
              <PriceText :cents="d.budgetMaxCents"
            /></strong>
          </div>
          <span class="mm-demand-post__region">{{
            d.region || '不限地区'
          }}</span>
        </div>
        <p class="mm-demand-post__description">{{ d.description }}</p>
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
        <div class="mm-actions mm-demand-post__actions">
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
            :aria-expanded="expanded === d.id"
            >{{ expanded === d.id ? '收起回复' : '查看与回复' }}</MmButton
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
            ><ReportButton
              class="mm-demand-post__report"
              resource-type="DEMAND_POST"
              :resource-id="d.id"
          /></template>
        </div>
        <DemandReplies
          v-if="expanded === d.id"
          :demand-id="d.id"
          :closed="d.isClosed"
        />
      </article>
    </div>
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
.mm-demand-board {
  max-width: 1200px;
  gap: 22px;
  padding-top: 30px;
}
.mm-demand-board__intro {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 24px;
  padding: 8px 0 24px;
  border-bottom: 1px solid var(--mm-border);
}
.mm-demand-board__intro h1 {
  font-size: 32px;
  margin: 6px 0;
  letter-spacing: -0.7px;
}
.mm-demand-board__intro p:not(.mm-eyebrow) {
  font-size: 14px;
  color: var(--mm-muted);
}
.mm-demand-board__intro > :last-child {
  flex-shrink: 0;
}
.mm-demand-board__login {
  display: inline-flex;
  align-items: center;
  min-height: 44px;
  padding: 10px 18px;
  background: var(--mm-primary);
  color: white;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 650;
}
.mm-demand-board__tabs {
  display: flex;
  gap: 4px;
  align-self: flex-start;
  padding: 5px;
  border: 1px solid var(--mm-border);
  border-radius: 12px;
  background: #eeede8;
}
.mm-demand-board__tabs button {
  border: 0;
  padding: 10px 24px;
  min-height: 44px;
  border-radius: 8px;
  background: transparent;
  color: var(--mm-muted);
  font-size: 14px;
  font-weight: 600;
}
.mm-demand-board__tabs button[aria-pressed='true'] {
  background: white;
  color: var(--mm-ink);
  box-shadow: 0 2px 5px #2425220d;
}
.mm-demand-form {
  width: 100%;
  max-width: none;
  border-top: 3px solid var(--mm-primary);
  padding: 26px;
  gap: 22px;
}
.mm-demand-form__heading {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  border-bottom: 1px solid var(--mm-border);
  padding-bottom: 18px;
}
.mm-demand-form__heading p,
.mm-demand-form__heading > span {
  font-size: 13px;
  color: var(--mm-muted);
  margin-top: 5px;
}
.mm-demand-form__heading > span {
  white-space: nowrap;
}
.mm-demand-form__layout {
  display: grid;
  grid-template-columns: minmax(0, 1.3fr) minmax(0, 1fr);
  gap: 28px;
}
.mm-demand-form__details,
.mm-demand-form__preferences {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}
.mm-demand-form__details textarea {
  min-height: 168px;
}
.mm-demand-form__preferences {
  background: var(--mm-canvas);
  padding: 20px;
  border-radius: 10px;
}
.mm-demand-form__preferences h3 {
  font-size: 15px;
}
.mm-demand-form__preferences .mm-grid {
  gap: 12px;
}
.mm-demand-form__actions {
  padding-top: 18px;
  border-top: 1px solid var(--mm-border);
}
.mm-demand-form [aria-invalid='true'] {
  border-color: var(--mm-danger);
}
.mm-demand-form .mm-error {
  font-size: 13px;
  line-height: 1.6;
}
.mm-demand-board__posts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
  align-items: start;
}
.mm-demand-post {
  min-width: 0;
  height: 100%;
  padding: 24px;
  gap: 16px;
  border: 1px solid var(--mm-border);
  border-radius: 14px;
  box-shadow: 0 3px 12px #24252204;
}
.mm-demand-post.is-expanded,
.mm-demand-post:has(.mm-demand-post__report form) {
  grid-column: 1 / -1;
}
.mm-demand-post__heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
.mm-demand-post h2 {
  font-size: 19px;
  line-height: 1.55;
  overflow-wrap: anywhere;
}
.mm-demand-post__facts {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  padding: 14px 16px;
  border-radius: 10px;
  background: var(--mm-accent-soft);
}
.mm-demand-post__facts > div {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.mm-demand-post__facts > div > span {
  font-size: 12px;
  color: var(--mm-muted);
}
.mm-demand-post__facts strong {
  font-size: 17px;
  font-variant-numeric: tabular-nums;
}
.mm-demand-post__region {
  font-size: 13px;
  color: var(--mm-muted);
}
.mm-demand-post__description {
  font-size: 14px;
  line-height: 1.85;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.mm-demand-author {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 9px;
  font-size: 13px;
  color: var(--mm-muted);
  margin-top: auto;
}
.mm-demand-author time {
  margin-left: auto;
  font-size: 12px;
}
.mm-demand-post__actions {
  border-top: 1px solid var(--mm-border);
  padding-top: 16px;
  gap: 10px;
}
.mm-demand-post__actions > a {
  flex-basis: 100%;
  font-size: 13px;
  min-height: 36px;
  display: flex;
  align-items: center;
}
.mm-demand-post__actions :deep(.mm-button) {
  font-size: 14px;
  min-height: 44px;
}
.mm-demand-post__report {
  margin-left: auto;
}
.mm-demand-post__report:has(form) {
  flex-basis: 100%;
}
.mm-demand-post__report :deep(> .mm-button) {
  border-color: transparent;
  background: transparent;
  color: var(--mm-muted);
  font-weight: 500;
}
.mm-demand-post__report :deep(form) {
  width: 100%;
  max-width: none;
  margin-top: 10px;
  padding: 16px;
  background: var(--mm-canvas);
  border-radius: 10px;
}
@media (max-width: 760px) {
  .mm-demand-board {
    padding-top: 22px;
  }
  .mm-demand-board__intro {
    align-items: flex-start;
    flex-wrap: wrap;
    gap: 16px;
  }
  .mm-demand-board__intro h1 {
    font-size: 27px;
  }
  .mm-demand-board__posts,
  .mm-demand-form__layout {
    grid-template-columns: minmax(0, 1fr);
  }
  .mm-demand-form,
  .mm-demand-post {
    padding: 18px;
  }
  .mm-demand-form__heading > span {
    display: none;
  }
  .mm-demand-form__preferences {
    padding: 16px;
  }
  .mm-demand-form__preferences .mm-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .mm-demand-post h2 {
    font-size: 18px;
  }
  .mm-demand-author time {
    flex-basis: 100%;
    margin-left: 41px;
  }
  .mm-demand-post__actions {
    gap: 8px;
  }
}
</style>
