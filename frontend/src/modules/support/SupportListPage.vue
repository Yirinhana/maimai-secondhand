<script setup lang="ts">
import GovernanceNotice from '../../shared/components/GovernanceNotice.vue';
import { computed, nextTick, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { get, post, type ApiError } from '../../shared/api';
import { formatTime } from '../../shared/format';
import { useAuthStore } from '../../shared/stores/auth';
import type { CommunityPage } from '../community/types';
import { ticketStatus, type Ticket, type Faq } from './types';
import MmButton from '../../shared/components/MmButton.vue';
import MmPagination from '../../shared/components/MmPagination.vue';
const route = useRoute(),
  router = useRouter(),
  admin = computed(() => route.path.startsWith('/admin/')),
  items = ref<Ticket[]>([]),
  faqs = ref<Faq[]>([]),
  page = ref(0),
  pages = ref(0),
  status = ref('OPEN'),
  error = ref(''),
  loading = ref(false),
  busy = ref(false),
  form = ref({ title: '', body: '', orderNo: '' }),
  showForm = ref(false);
const auth = useAuthStore();
const formElement = ref<HTMLFormElement | null>(null);
async function toggleForm() {
  showForm.value = !showForm.value;
  if (!showForm.value) return;
  await nextTick();
  formElement.value?.scrollIntoView({ block: 'start' });
  formElement.value
    ?.querySelector<HTMLInputElement>('input')
    ?.focus({ preventScroll: true });
}
if (
  typeof route.query.title === 'string' &&
  typeof route.query.body === 'string'
) {
  form.value.title = route.query.title.slice(0, 100);
  form.value.body = route.query.body.slice(0, 2000);
  showForm.value = true;
}
async function load() {
  if (!auth.me) return;
  loading.value = true;
  error.value = '';
  try {
    const r = await get<CommunityPage<Ticket>>(
      admin.value ? '/support/admin/tickets' : '/support/tickets',
      {
        page: page.value,
        size: 15,
        ...(admin.value ? { status: status.value } : {}),
      },
    );
    items.value = r.items;
    pages.value = r.totalPages;
  } catch (e) {
    error.value = (e as ApiError).message;
  } finally {
    loading.value = false;
  }
}
async function create() {
  busy.value = true;
  error.value = '';
  try {
    const ticket = await post<Ticket>('/support/tickets', {
      title: form.value.title,
      body: form.value.body,
      orderNo: form.value.orderNo.trim() || null,
    });
    await router.push(`/support/tickets/${ticket.id}`);
  } catch (e) {
    error.value = (e as ApiError).message;
  } finally {
    busy.value = false;
  }
}
onMounted(async () => {
  if (!auth.meLoaded) await auth.fetchMe();
  if (auth.me) await load();
  if (!admin.value) {
    try {
      faqs.value = await get<Faq[]>('/support/faq');
    } catch {
      /* 工单入口保留独立错误反馈 */
    }
  }
});
</script>
<template>
  <section
    class="mm-support"
    :class="admin ? 'mm-stack mm-support--admin' : 'mm-page'"
  >
    <header class="mm-support__heading">
      <div>
        <p class="mm-eyebrow">{{ admin ? 'SUPPORT DESK' : 'HELP CENTER' }}</p>
        <h1>{{ admin ? '客服工作台' : '帮助与客服' }}</h1>
        <p>
          {{
            admin
              ? '先接管工单后处理，所有回复保留记录。'
              : '先找答案，有需要时再联系人工客服，处理进度留在这里。'
          }}
        </p>
      </div>
      <template v-if="!admin">
        <RouterLink
          v-if="!auth.me"
          class="mm-support__login"
          :to="{ path: '/login', query: { redirect: route.fullPath } }"
          >登录后联系人工客服 →</RouterLink
        >
        <MmButton
          v-else
          :aria-expanded="showForm"
          aria-controls="support-create-form"
          @click="toggleForm"
          >{{ showForm ? '收起工单表单' : '＋ 联系人工客服' }}</MmButton
        >
      </template>
    </header>
    <label v-if="admin" class="mm-support__filter"
      >处理状态
      <select
        v-model="status"
        @change="
          page = 0;
          load();
        "
      >
        <option value="OPEN">待接管</option>
        <option value="IN_PROGRESS">处理中</option>
        <option value="CLOSED">已关闭</option>
      </select>
    </label>
    <p v-if="error" class="mm-error" role="alert">{{ error }}</p>
    <div class="mm-support__layout">
      <div v-if="!admin" class="mm-support__main">
        <section
          class="mm-support__section"
          aria-labelledby="support-faq-title"
        >
          <header class="mm-support__section-heading">
            <div>
              <h2 id="support-faq-title">常见问题</h2>
              <p>付款、交付与售后，按问题查找答案。</p>
            </div>
            <RouterLink to="/policies">查看平台规则 →</RouterLink>
          </header>
          <details v-for="faq in faqs" :key="faq.topic" class="mm-support__faq">
            <summary>{{ faq.title }}</summary>
            <p>{{ faq.answer }}</p>
          </details>
          <p v-if="!faqs.length" class="mm-support__empty">
            常见问题暂不可用，可提交人工工单。
          </p>
        </section>
        <form
          v-if="showForm && auth.me"
          id="support-create-form"
          ref="formElement"
          class="mm-support__section mm-form mm-support__form"
          @submit.prevent="create"
        >
          <header class="mm-support__section-heading">
            <div>
              <h2>提交人工工单</h2>
              <p>描述遇到的问题，有订单时填写订单号，方便客服核实。</p>
            </div>
          </header>
          <div class="mm-support__fields">
            <label
              >问题标题<input
                v-model="form.title"
                required
                maxlength="100"
                placeholder="简要说明需要帮助的问题"
            /></label>
            <label
              >关联订单号（可选）<input
                v-model="form.orderNo"
                maxlength="64"
                placeholder="可从订单详情复制"
            /></label>
          </div>
          <label
            >问题描述<textarea
              v-model="form.body"
              required
              maxlength="2000"
              placeholder="说明发生了什么、已尝试的操作，以及希望如何处理"
            />
          </label>
          <p class="mm-muted">请勿在留言中填写密码、验证码或支付密钥。</p>
          <div class="mm-support__form-actions">
            <MmButton type="submit" :loading="busy">提交工单</MmButton
            ><MmButton variant="ghost" @click="showForm = false"
              >暂不提交</MmButton
            >
          </div>
          <GovernanceNotice />
        </form>
      </div>
      <section
        class="mm-support__section mm-support__tickets"
        aria-labelledby="support-tickets-title"
      >
        <header class="mm-support__section-heading">
          <div>
            <h2 id="support-tickets-title">
              {{ admin ? '处理队列' : '我的工单' }}
            </h2>
            <p>
              {{
                admin
                  ? '查看问题详情后接管并回复。'
                  : '查看客服回复和每个问题的处理进度。'
              }}
            </p>
          </div>
        </header>
        <template v-if="auth.me">
          <p v-if="loading" class="mm-support__empty" role="status">
            正在读取工单…
          </p>
          <RouterLink
            v-for="ticket in items"
            :key="ticket.id"
            :to="`/support/tickets/${ticket.id}`"
            class="mm-support__ticket"
          >
            <div class="mm-support__ticket-head">
              <strong>{{ ticket.title }}</strong
              ><span
                class="mm-support__status"
                :class="`is-${ticket.status.toLowerCase()}`"
                >{{ ticketStatus[ticket.status] }}</span
              >
            </div>
            <div class="mm-support__ticket-foot">
              <time
                >{{ admin ? ticket.ownerNickname + ' · ' : ''
                }}{{ formatTime(ticket.updatedAt) }}</time
              ><span>查看详情 →</span>
            </div>
          </RouterLink>
          <p v-if="!loading && !items.length" class="mm-support__empty">
            {{
              admin
                ? '当前状态下没有工单。'
                : '还没有工单。提交问题后，可以在这里查看回复。'
            }}
          </p>
          <MmPagination
            :page="page"
            :total-pages="pages"
            @change="
              page = $event;
              load();
            "
          />
        </template>
        <div v-else class="mm-support__empty">
          <p>登录后可以提交问题并查看你的处理记录。</p>
          <RouterLink
            :to="{ path: '/login', query: { redirect: route.fullPath } }"
            >登录查看我的工单 →</RouterLink
          >
        </div>
      </section>
    </div>
  </section>
</template>
<style scoped>
.mm-support {
  max-width: 1200px;
}
.mm-support__heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 24px;
  padding: 8px 0 24px;
  border-bottom: 1px solid var(--mm-border);
}
.mm-support__heading h1 {
  margin: 6px 0;
  font-size: 30px;
}
.mm-support__heading p:not(.mm-eyebrow) {
  color: var(--mm-muted);
  font-size: 14px;
}
.mm-support__heading > :last-child {
  flex-shrink: 0;
}
.mm-support__login {
  display: inline-flex;
  align-items: center;
  min-height: 44px;
  padding: 10px 16px;
  border-radius: 10px;
  color: white;
  background: var(--mm-primary);
  font-weight: 600;
  font-size: 14px;
}
.mm-support__layout {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(0, 1fr);
  gap: 24px;
  align-items: start;
}
.mm-support__main {
  display: flex;
  flex-direction: column;
  gap: 24px;
  min-width: 0;
}
.mm-support__section {
  min-width: 0;
  padding: 24px;
  background: white;
  border: 1px solid var(--mm-border);
  border-radius: 14px;
}
.mm-support__section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 14px;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--mm-border);
}
.mm-support__section-heading h2 {
  font-size: 19px;
}
.mm-support__section-heading p {
  color: var(--mm-muted);
  font-size: 13px;
  margin-top: 4px;
}
.mm-support__section-heading a {
  font-size: 13px;
  font-weight: 600;
  padding: 5px 0;
}
.mm-support__faq {
  border-bottom: 1px solid var(--mm-border);
}
.mm-support__faq:last-child {
  border-bottom: 0;
}
.mm-support__faq summary {
  min-height: 52px;
  padding: 15px 4px;
  font-size: 14px;
  line-height: 1.6;
}
.mm-support__faq[open] summary {
  color: var(--mm-primary);
}
.mm-support__faq > p {
  white-space: pre-wrap;
  padding: 0 8px 20px 20px;
  line-height: 1.85;
  font-size: 14px;
  color: var(--mm-muted);
}
.mm-support__form {
  max-width: none;
  width: 100%;
  gap: 18px;
  border-top: 3px solid var(--mm-primary);
}
.mm-support__fields {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr);
  gap: 16px;
}
.mm-support__form textarea {
  min-height: 140px;
}
.mm-support__form-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding-top: 16px;
  border-top: 1px solid var(--mm-border);
}
.mm-support__ticket {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 18px 0;
  color: var(--mm-ink);
  border-bottom: 1px solid var(--mm-border);
}
.mm-support__ticket:hover {
  text-decoration: none;
  color: var(--mm-primary);
}
.mm-support__ticket-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}
.mm-support__ticket-head strong {
  font-size: 15px;
  overflow-wrap: anywhere;
  line-height: 1.6;
}
.mm-support__status {
  flex-shrink: 0;
  font-size: 12px;
  padding: 3px 8px;
  border: 1px solid var(--mm-border);
  border-radius: 6px;
  background: var(--mm-canvas);
  color: var(--mm-muted);
}
.mm-support__status.is-open {
  background: #fff1dd;
  color: #83510d;
  border-color: #ebd8b4;
}
.mm-support__status.is-in_progress {
  background: #edf3f7;
  color: #315c77;
  border-color: #cbdce6;
}
.mm-support__ticket-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  font-size: 12px;
  color: var(--mm-muted);
}
.mm-support__ticket-foot > span {
  font-weight: 600;
  color: var(--mm-primary);
}
.mm-support__empty {
  padding: 24px 0 8px;
  color: var(--mm-muted);
  font-size: 14px;
  line-height: 1.85;
}
.mm-support__empty a {
  display: inline-block;
  margin-top: 12px;
  padding: 8px 0;
}
.mm-support__filter {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  padding: 16px 18px;
  background: white;
  border: 1px solid var(--mm-border);
  border-radius: 12px;
  font-size: 14px;
}
.mm-support__filter select {
  min-width: 160px;
}
.mm-support--admin {
  max-width: none;
}
.mm-support--admin .mm-support__layout {
  grid-template-columns: minmax(0, 1fr);
}
@media (max-width: 900px) {
  .mm-support__layout {
    grid-template-columns: minmax(0, 1fr);
  }
  .mm-support__heading {
    flex-wrap: wrap;
    align-items: flex-start;
  }
}
@media (max-width: 600px) {
  .mm-support__section {
    padding: 18px;
  }
  .mm-support__heading h1 {
    font-size: 26px;
  }
  .mm-support__fields {
    grid-template-columns: minmax(0, 1fr);
  }
  .mm-support__ticket-head {
    flex-wrap: wrap;
  }
}
</style>
