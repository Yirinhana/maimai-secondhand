<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { get, post, type ApiError } from '../../shared/api';
import { useAuthStore } from '../../shared/stores/auth';
import MmButton from '../../shared/components/MmButton.vue';
import AiActivity from './AiActivity.vue';
import { plainReply } from './plainText';
const props = defineProps<{
  stage: string;
  resourceId?: number;
  draft?: string;
  title?: string;
}>();
interface Result {
  id: number;
  status: string;
  answer: string | null;
  failureCode: string | null;
  model: string;
}
interface Turn {
  question: string;
  result: Result;
}
interface Request {
  stage: string;
  resourceId: number | null;
  question: string;
  draft: string;
  requestKey: string;
  previousRunId: number | null;
}
const auth = useAuthStore(),
  route = useRoute();
const open = ref(false),
  busy = ref(false),
  error = ref(''),
  question = ref(''),
  turns = ref<Turn[]>([]);
const pending = ref<Request | null>(null),
  pendingResult = ref<Result | null>(null),
  modelEnabled = ref<boolean | null>(null);
const input = ref<HTMLTextAreaElement>(),
  copied = ref<number | null>(null),
  history = ref<HTMLElement>();
let generation = 0,
  timer: ReturnType<typeof setTimeout> | undefined;
const suggestions = computed(() =>
  props.stage === 'LISTING' &&
  turns.value.at(-1)?.result.answer?.includes('你手边更接近哪一类')
    ? ['我有一些读完的书', '我有闲置耳机', '我有生活用品']
    : ({
        LISTING: [
          '我不知道卖什么',
          '帮我整理已有描述，不补写未知信息',
          '发布前最值得补充哪两点？',
        ],
        PRODUCT: [
          '联系卖家前，我该确认哪两件事？',
          '帮我看看这件物品是否适合我的用途',
        ],
        ORDER: ['我现在应该做什么？', '这笔订单的付款会真实扣款吗？'],
        AFTERSALE: [
          '下一步需要准备什么证据？',
          '双方暂时谈不拢，我可以怎么做？',
        ],
        REVIEW: ['怎样把真实体验写清楚？', '有问题想反馈，评价和售后怎么选？'],
        GUIDE: [
          '第一次买二手，从哪里开始？',
          '收到的东西有问题怎么办？',
          '我不知道卖什么',
        ],
      }[props.stage] ?? ['我现在应该做什么？']),
);
const intro = computed(() =>
  props.stage === 'LISTING'
    ? '没想好卖什么也可以问；已有草稿，我们就接着整理。'
    : '不必先懂流程，说说你现在想解决什么。',
);
const lastSuccess = computed(
  () =>
    [...turns.value].reverse().find((t) => t.result.status === 'SUCCEEDED')
      ?.result.id ?? null,
);
watch(
  () => [props.resourceId, props.stage, auth.me?.id],
  () => {
    generation++;
    clearTimeout(timer);
    busy.value = false;
    pending.value = null;
    pendingResult.value = null;
    turns.value = [];
    question.value = '';
    error.value = '';
  },
);
onBeforeUnmount(() => {
  generation++;
  clearTimeout(timer);
});
async function toggle() {
  open.value = !open.value;
  if (open.value && auth.me && modelEnabled.value === null) {
    try {
      modelEnabled.value = (
        await get<{ modelEnabled: boolean }>('/ai/workflows/availability')
      ).modelEnabled;
    } catch {
      /* Actual request owns its error feedback; common guidance remains available. */
    }
  }
}
async function scrollAnswer() {
  await nextTick();
  history.value?.scrollTo({
    top: history.value.scrollHeight,
    behavior: 'smooth',
  });
}
async function receive(value: Result, current: number) {
  if (current !== generation) return;
  pendingResult.value = value;
  if (value.status === 'RUNNING') {
    timer = setTimeout(async () => {
      try {
        await receive(await get<Result>('/ai/workflows/' + value.id), current);
      } catch {
        if (current === generation) {
          busy.value = false;
          error.value = '结果还没读取到。可以查询进度，不会重新调用分析。';
        }
      }
    }, 1800);
    return;
  }
  turns.value.push({
    question: pending.value?.question || '帮我看看从哪里开始',
    result: value,
  });
  if (turns.value.length > 8) turns.value.shift();
  busy.value = false;
  pending.value = null;
  pendingResult.value = null;
  if (value.status === 'FAILED')
    error.value =
      '这次分析没有完成。之前的答复和填写内容都还在，可以稍后再问，或转人工客服。';
  await scrollAnswer();
}
async function ask() {
  if (busy.value || pending.value) return;
  const current = generation;
  pending.value = {
    stage: props.stage,
    resourceId: props.resourceId ?? null,
    question: question.value.trim(),
    draft: (props.draft ?? '').slice(0, 1400),
    requestKey: crypto.randomUUID(),
    previousRunId: lastSuccess.value,
  };
  question.value = '';
  busy.value = true;
  error.value = '';
  copied.value = null;
  await scrollAnswer();
  try {
    await receive(await post<Result>('/ai/workflows', pending.value), current);
  } catch (e) {
    if (current === generation) {
      busy.value = false;
      error.value =
        (e as ApiError).message || '暂时无法读取答复，请查询这次请求的结果。';
    }
  }
}
async function recover() {
  if (busy.value || !pending.value) return;
  busy.value = true;
  error.value = '';
  const current = generation;
  try {
    await receive(
      pendingResult.value
        ? await get<Result>('/ai/workflows/' + pendingResult.value.id)
        : await post<Result>('/ai/workflows', pending.value),
      current,
    );
  } catch (e) {
    if (current === generation) {
      busy.value = false;
      error.value = (e as ApiError).message;
    }
  }
}
async function choose(text: string) {
  question.value = text;
  await nextTick();
  input.value?.focus();
}
async function copy(result: Result) {
  try {
    await navigator.clipboard.writeText(plainReply(result.answer));
    copied.value = result.id;
  } catch {
    error.value = '浏览器没有允许复制，可以直接选中答复文字复制。';
  }
}
</script>
<template>
  <section class="workflow-assistant" :aria-label="title || '麦仔流程助手'">
    <button
      type="button"
      class="workflow-assistant__toggle"
      :aria-expanded="open"
      @click="toggle"
    >
      <span class="workflow-assistant__mark" aria-hidden="true">麦</span
      ><span
        ><strong>{{ title || '这一步，让麦仔帮你看看' }}</strong
        ><small>{{ intro }}</small></span
      ><span aria-hidden="true">{{ open ? '−' : '+' }}</span>
    </button>
    <div v-if="open" class="workflow-assistant__body">
      <RouterLink
        v-if="!auth.me"
        :to="{ path: '/login', query: { redirect: route.fullPath } }"
        >登录后使用流程助手</RouterLink
      >
      <template v-else>
        <p class="workflow-assistant__notice">
          入门建议不调用模型；需要深度分析时，仅发送当前环节必要资料。请勿输入电话、地址或密码。
        </p>
        <p v-if="modelEnabled === false" class="mm-notice">
          深度分析暂未连接，入门指引仍可使用，商品和交易操作不受影响。
        </p>
        <div
          v-if="turns.length || pending"
          ref="history"
          class="workflow-assistant__history"
          role="log"
          aria-label="本次辅助对话"
          aria-relevant="additions"
        >
          <article
            v-for="turn in turns"
            :key="turn.result.id"
            class="workflow-assistant__turn"
          >
            <p class="workflow-assistant__question">
              <span>你</span>{{ turn.question }}
            </p>
            <div
              v-if="turn.result.status === 'SUCCEEDED'"
              class="workflow-assistant__answer"
            >
              <strong>麦仔</strong>
              <p>{{ plainReply(turn.result.answer) }}</p>
              <footer>
                <small>{{
                  turn.result.model === 'platform-guide'
                    ? '平台入门指引 · 未调用模型'
                    : 'AI 辅助建议 · 请核对后使用'
                }}</small
                ><button type="button" @click="copy(turn.result)">
                  {{ copied === turn.result.id ? '已复制' : '复制答复' }}
                </button>
              </footer>
            </div>
            <p v-else class="mm-muted">
              本次未取得答复，没有改变任何商品或交易状态。
            </p>
          </article>
          <p v-if="pending" class="workflow-assistant__question">
            <span>你</span>{{ pending.question || '帮我看看从哪里开始' }}
          </p>
        </div>
        <AiActivity
          :active="busy"
          :outcome="error ? 'error' : turns.length ? 'success' : 'idle'"
        />
        <p v-if="error" class="mm-error" role="alert">{{ error }}</p>
        <div
          class="workflow-assistant__suggestions"
          aria-label="常见问题，选择后可修改再发送"
        >
          <button
            v-for="text in suggestions"
            :key="text"
            type="button"
            :disabled="busy || !!pending"
            @click="choose(text)"
          >
            {{ text }}
          </button>
        </div>
        <form @submit.prevent="ask">
          <label
            >{{ turns.length ? '继续问麦仔' : '你想解决什么？'
            }}<textarea
              ref="input"
              v-model="question"
              :disabled="busy || !!pending"
              maxlength="700"
              rows="2"
              :placeholder="
                stage === 'LISTING'
                  ? '例如：还没想好卖什么，可以从哪里开始？'
                  : '例如：我现在应该先做哪一步？'
              "
              @keydown.ctrl.enter.prevent="ask"
            />
          </label>
          <div class="mm-actions">
            <MmButton v-if="pending && !busy" type="button" @click="recover"
              >查询这次请求</MmButton
            ><MmButton
              v-else
              type="submit"
              :loading="busy"
              :disabled="!!pending"
              >{{ turns.length ? '继续请教麦仔' : '请麦仔分析' }}</MmButton
            ><RouterLink
              :to="{
                path: '/support',
                query: {
                  title: title || '交易流程咨询',
                  body: '我在页面 ' + route.fullPath + ' 需要人工协助。',
                },
              }"
              >转人工客服</RouterLink
            >
          </div>
        </form>
        <small class="workflow-assistant__notice"
          >建议不会自动填入表单、发布商品或处理订单，下一步由你决定。</small
        >
      </template>
    </div>
  </section>
</template>
<style scoped>
.workflow-assistant {
  margin: 18px 0;
  border: 1px solid #dddccf;
  border-radius: 16px;
  background: #faf9f2;
  color: var(--mm-ink);
  overflow: hidden;
  min-width: 0;
}
.workflow-assistant__toggle {
  width: 100%;
  display: flex;
  gap: 12px;
  align-items: center;
  text-align: left;
  padding: 18px;
  border: 0;
  background: none;
  color: inherit;
  cursor: pointer;
}
.workflow-assistant__toggle > span:nth-child(2) {
  flex: 1;
  min-width: 0;
}
.workflow-assistant__toggle strong,
.workflow-assistant__toggle small {
  display: block;
}
.workflow-assistant__toggle small {
  margin-top: 5px;
  font-size: 13px;
  color: var(--mm-muted);
  line-height: 1.6;
}
.workflow-assistant__mark {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 38px;
  height: 38px;
  border-radius: 50%;
  background: #eae4cf;
  color: #675a2b;
  font-weight: 700;
}
.workflow-assistant__body {
  padding: 0 18px 18px;
  display: grid;
  gap: 14px;
}
.workflow-assistant__body label {
  display: grid;
  gap: 8px;
  font-size: 14px;
}
.workflow-assistant__body textarea {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid #d9d8cb;
  border-radius: 10px;
  padding: 12px;
  font: inherit;
  background: white;
  resize: vertical;
}
.workflow-assistant__body form {
  display: grid;
  gap: 12px;
}
.workflow-assistant__notice {
  color: var(--mm-muted);
  font-size: 12px;
  line-height: 1.8;
  margin: 0;
}
.workflow-assistant__history {
  display: grid;
  gap: 16px;
  max-height: 560px;
  overflow: auto;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
  padding: 3px;
}
.workflow-assistant__turn {
  display: grid;
  gap: 12px;
}
.workflow-assistant__question {
  justify-self: end;
  max-width: 90%;
  background: #ece8dc;
  border-radius: 14px 14px 3px 14px;
  padding: 12px 15px;
  margin: 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  line-height: 1.8;
}
.workflow-assistant__question span {
  display: block;
  font-size: 11px;
  color: var(--mm-muted);
}
.workflow-assistant__answer {
  padding: 17px;
  background: white;
  border: 1px solid #eeece4;
  border-radius: 14px 14px 14px 3px;
}
.workflow-assistant__answer > strong {
  font-size: 13px;
  color: #675a2b;
}
.workflow-assistant__answer p {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  line-height: 1.9;
  margin: 10px 0 14px;
}
.workflow-assistant__answer footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}
.workflow-assistant__answer small {
  color: var(--mm-muted);
  font-size: 11px;
}
.workflow-assistant__answer button,
.workflow-assistant__suggestions button {
  color: #63552b;
  background: white;
  border: 1px solid #ded8c7;
  border-radius: 8px;
  padding: 8px 12px;
  cursor: pointer;
  font: inherit;
  font-size: 12px;
  min-height: 36px;
}
.workflow-assistant__suggestions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.workflow-assistant button:focus-visible,
.workflow-assistant textarea:focus-visible {
  outline: 2px solid #8b702a;
  outline-offset: 3px;
}
.workflow-assistant button:disabled {
  opacity: 0.55;
  cursor: default;
}
@media (max-width: 600px) {
  .workflow-assistant__toggle {
    padding: 14px;
  }
  .workflow-assistant__body {
    padding: 0 14px 14px;
  }
  .workflow-assistant__body textarea {
    font-size: 16px;
  }
  .workflow-assistant__history {
    max-height: 460px;
  }
  .workflow-assistant__suggestions button {
    min-height: 44px;
    text-align: left;
  }
  .workflow-assistant__answer {
    padding: 13px;
  }
}
</style>
