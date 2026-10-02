<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { askConfirmation } from '../../shared/confirm';
import SupportHandoff from './SupportHandoff.vue';
const handoffOpen = ref(false);
import { useTinaChat } from './useTinaChat';
import { plainReply } from './plainText';
import MaizaiMascot from '../../shared/components/MaizaiMascot.vue';
import { useMascotMotion } from '../../shared/useMascotMotion';
import { useEdgeDock } from './useEdgeDock';
const route = useRoute();
const {
  auth,
  assistant,
  faqs,
  turns,
  loading,
  sending,
  error,
  draft,
  enabled,
  pending,
  load,
  send,
  clear,
} = useTinaChat();
const dialog = ref<HTMLDialogElement | null>(null),
  launcher = ref<HTMLButtonElement | null>(null);
const transcript = ref<HTMLElement | null>(null),
  input = ref<HTMLTextAreaElement | null>(null);
const opened = ref(false),
  tab = ref<'chat' | 'faq'>('chat'),
  unread = ref(false);
const { animated, greeting, point, reset, greet } = useMascotMotion(
  launcher,
  launcher,
);
const dock = useEdgeDock(launcher);
const keyboardViewport = ref<{
  top: string;
  bottom: string;
  height: string;
}>();
const compactKeyboard = ref(false);
function fitKeyboardViewport() {
  const viewport = window.visualViewport;
  // The keyboard can shrink only the visual viewport on mobile Safari. Do not
  // reposition the dialog while the user is deliberately pinch-zooming.
  if (
    !opened.value ||
    !viewport ||
    viewport.scale > 1.01 ||
    window.innerHeight - viewport.height < 80
  ) {
    keyboardViewport.value = undefined;
    compactKeyboard.value = false;
    return;
  }
  keyboardViewport.value = {
    top: `${viewport.offsetTop + 12}px`,
    bottom: 'auto',
    height: `${Math.max(0, viewport.height - 24)}px`,
  };
  compactKeyboard.value = viewport.height < 480;
}
onMounted(() => {
  window.visualViewport?.addEventListener('resize', fitKeyboardViewport);
  window.visualViewport?.addEventListener('scroll', fitKeyboardViewport);
});
onBeforeUnmount(() => {
  window.visualViewport?.removeEventListener('resize', fitKeyboardViewport);
  window.visualViewport?.removeEventListener('scroll', fitKeyboardViewport);
});
function moveLauncher(event: PointerEvent) {
  dock.move(event);
  if (!dock.dragging.value) point(event);
}
function openLauncher(event: MouseEvent) {
  if (dock.click(event)) void open();
}
let previousFocus: HTMLElement | null = null;
async function open() {
  previousFocus = document.activeElement as HTMLElement | null;
  opened.value = true;
  unread.value = false;
  await nextTick();
  dialog.value?.showModal();
  fitKeyboardViewport();
  void load();
}
function close() {
  if (!opened.value) return;
  dialog.value?.close();
  opened.value = false;
  fitKeyboardViewport();
  if (previousFocus?.isConnected) previousFocus.focus();
  else launcher.value?.focus();
}
async function submit() {
  await send();
  await nextTick();
  if (opened.value) input.value?.focus();
}
function navigateTab(event: KeyboardEvent) {
  if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
  event.preventDefault();
  tab.value =
    event.key === 'Home'
      ? 'chat'
      : event.key === 'End'
        ? 'faq'
        : tab.value === 'chat'
          ? 'faq'
          : 'chat';
  void nextTick(() =>
    document.getElementById(`tina-${tab.value}-tab`)?.focus(),
  );
}
async function clearHistory() {
  if (sending.value || !turns.value.length) return;
  const owner = auth.me?.id;
  if (await askConfirmation('清空你与麦仔的全部对话记录？人工工单不受影响。')) {
    if (owner && owner === auth.me?.id) await clear();
  }
}
function handoff() {
  close();
}
watch(
  () => turns.value,
  async (value, old) => {
    if (
      !opened.value &&
      value.some(
        (turn) =>
          turn.status === 'COMPLETE' &&
          !old?.some(
            (previous) =>
              previous.id === turn.id && previous.status === 'COMPLETE',
          ),
      )
    )
      unread.value = true;
    await nextTick();
    transcript.value?.scrollTo({ top: transcript.value.scrollHeight });
  },
  { deep: true },
);
watch(
  () => auth.me?.id,
  () => {
    unread.value = false;
    handoffOpen.value = false;
    if (opened.value) void load();
  },
);
onBeforeUnmount(() => dialog.value?.close());
</script>

<template>
  <button
    ref="launcher"
    type="button"
    class="tina-launcher"
    :class="{
      'is-dragging': dock.dragging.value,
      'is-snapping': dock.snapping.value,
    }"
    :style="dock.style.value"
    aria-label="打开麦仔客服"
    aria-describedby="maizai-position-help"
    title="拖动麦仔，松手自动贴边"
    aria-haspopup="dialog"
    :aria-expanded="opened"
    aria-controls="tina-dialog"
    @click="openLauncher"
    @pointerdown="dock.down"
    @pointermove="moveLauncher"
    @pointerup="dock.up"
    @pointercancel="dock.cancel"
    @lostpointercapture="dock.cancel"
    @keydown="dock.keyboard"
    @pointerenter="greet"
    @pointerleave="reset"
    @focus="greet"
  >
    <MaizaiMascot
      class="tina-launcher__mascot"
      :greeting="greeting"
      :active="
        animated && !opened && !dock.dragging.value && !dock.snapping.value
      "
    />
    <span class="tina-launcher__label">麦仔客服</span
    ><span v-if="unread" class="tina-unread" aria-label="有新回复"></span>
  </button>
  <span id="maizai-position-help" class="mm-visually-hidden"
    >拖动时自由移动，松手后吸附到最近的左右边缘。键盘方向键调整位置，Home恢复右下角，回车打开客服。</span
  >
  <dialog
    id="tina-dialog"
    ref="dialog"
    class="tina-dialog"
    :class="{
      'tina-dialog--left': dock.position.value.edge === 'left',
      'tina-dialog--compact': compactKeyboard,
    }"
    :style="keyboardViewport"
    aria-labelledby="tina-heading"
    @cancel.prevent="close"
    @click="$event.target === dialog && close()"
  >
    <div class="tina-panel">
      <header class="tina-header">
        <MaizaiMascot class="tina-face tina-face--large" :active="false" />
        <div>
          <h2 id="tina-heading">麦仔 <span>AI 客服</span></h2>
          <p>麦麦二手 · 问清楚，再做决定</p>
        </div>
        <button
          type="button"
          class="tina-icon-button"
          aria-label="关闭麦仔客服"
          autofocus
          @click="close"
        >
          ×
        </button>
      </header>
      <div class="tina-tabs" role="tablist" aria-label="客服内容">
        <button
          id="tina-chat-tab"
          type="button"
          role="tab"
          aria-controls="tina-chat-panel"
          :aria-selected="tab === 'chat'"
          :tabindex="tab === 'chat' ? 0 : -1"
          @keydown="navigateTab"
          @click="tab = 'chat'"
        >
          问麦仔
        </button>
        <button
          id="tina-faq-tab"
          type="button"
          role="tab"
          aria-controls="tina-faq-panel"
          :aria-selected="tab === 'faq'"
          :tabindex="tab === 'faq' ? 0 : -1"
          @keydown="navigateTab"
          @click="tab = 'faq'"
        >
          常见问题
        </button>
        <button
          type="button"
          class="tina-refresh"
          :disabled="loading || sending"
          @click="load"
        >
          刷新
        </button>
      </div>
      <SupportHandoff
        v-if="handoffOpen"
        :turns="turns"
        @cancel="handoffOpen = false"
        @done="
          handoffOpen = false;
          handoff();
        "
      />
      <section
        v-else-if="tab === 'faq'"
        id="tina-faq-panel"
        class="tina-body"
        role="tabpanel"
        aria-labelledby="tina-faq-tab"
      >
        <p class="tina-caption">以下为平台规则说明，可直接查看。</p>
        <p v-if="loading && !faqs.length" role="status">正在读取常见问题…</p>
        <details v-for="faq in faqs" :key="faq.topic" class="tina-faq">
          <summary>{{ faq.title }}</summary>
          <p>{{ faq.answer }}</p>
        </details>
        <p v-if="!loading && !faqs.length" class="tina-caption">
          常见问题暂时没有加载成功，请点击刷新。
        </p>
      </section>
      <section
        v-else
        id="tina-chat-panel"
        ref="transcript"
        class="tina-body tina-transcript"
        role="tabpanel"
        aria-labelledby="tina-chat-tab"
      >
        <div v-if="!turns.length" class="tina-welcome">
          <span class="tina-eyebrow">你好，我是麦仔</span>
          <h3>关于麦麦，有什么想问的？</h3>
          <p>
            我可以解释交易规则、指引页面操作。涉及具体订单的处理，可以转人工工单。
          </p>
        </div>
        <div v-if="!auth.me" class="tina-state">
          <strong>登录后与麦仔对话</strong>
          <p>你的对话记录仅在自己的账号中显示，常见问题无需登录。</p>
          <RouterLink
            :to="{ path: '/login', query: { redirect: route.fullPath } }"
            @click="close"
            >去登录 →</RouterLink
          >
        </div>
        <div v-else-if="assistant && !assistant.enabled" class="tina-state">
          <strong>麦仔暂未接通</strong>
          <p>你可以先查看常见问题，或提交人工工单。接通后即可在这里交流。</p>
          <button type="button" @click="tab = 'faq'">查看常见问题 →</button>
        </div>
        <p v-if="loading" class="tina-caption" role="status">正在读取对话…</p>
        <p v-if="turns.length" class="tina-history-note">
          最近 {{ turns.length }} 条提问 · 当前账号的私密对话
        </p>
        <ol class="tina-messages" aria-label="与麦仔的对话记录">
          <li v-for="turn in turns" :key="turn.requestId">
            <div class="tina-message tina-message--user">
              <span class="tina-author">你</span>
              <p>{{ turn.question }}</p>
            </div>
            <div
              v-if="turn.status === 'COMPLETE'"
              class="tina-message tina-message--assistant"
            >
              <span class="tina-author">麦仔 · AI</span>
              <p>{{ plainReply(turn.answer) }}</p>
            </div>
            <div v-else-if="turn.status === 'FAILED'" class="tina-failure">
              <p>这条问题暂时没能得到回复，你可以重试或转人工。</p>
              <button
                type="button"
                :disabled="sending || !enabled || pending"
                @click="send(turn)"
              >
                重试这条问题
              </button>
            </div>
            <div v-else class="tina-thinking" role="status">
              <MaizaiMascot class="tina-face" :active="animated" />
              <div>
                <span>麦仔正在思考</span
                ><span class="tina-dots" aria-hidden="true"
                  ><i></i><i></i><i></i
                ></span>
                <small>正在整理答案，请稍候</small>
              </div>
            </div>
          </li>
        </ol>
      </section>
      <p v-if="error" class="tina-error" role="alert">{{ error }}</p>
      <form
        v-if="tab === 'chat' && auth.me && !handoffOpen"
        class="tina-compose"
        @submit.prevent="submit"
      >
        <label for="tina-question" class="mm-visually-hidden"
          >给麦仔的问题</label
        >
        <textarea
          id="tina-question"
          ref="input"
          v-model="draft"
          rows="2"
          maxlength="1000"
          :disabled="!enabled"
          :placeholder="enabled ? '说说你遇到的问题…' : 'AI 接通后即可提问'"
          @keydown.enter.exact="
            if (!$event.isComposing) {
              $event.preventDefault();
              submit();
            }
          "
          @keydown.ctrl.enter.prevent="submit"
          @keydown.meta.enter.prevent="submit"
        ></textarea>
        <div>
          <small>Enter 发送 · Shift + Enter 换行</small
          ><button
            type="submit"
            :disabled="!enabled || sending || pending || !draft.trim()"
          >
            {{ sending ? '回复中…' : '发送' }}
          </button>
        </div>
      </form>
      <footer class="tina-footer">
        <button
          v-if="auth.me"
          type="button"
          @click="handoffOpen = !handoffOpen"
        >
          {{ handoffOpen ? '返回对话' : '带着对话转人工' }}
        </button>
        <RouterLink to="/support" @click="handoff">帮助与人工工单 ↗</RouterLink
        ><button
          v-if="auth.me && turns.length"
          type="button"
          :disabled="sending || loading"
          @click="clearHistory"
        >
          清空对话
        </button>
        <p>
          {{
            assistant?.notice ||
            'AI 回复仅供参考，订单处理以平台流程和人工结果为准。'
          }}
        </p>
      </footer>
    </div>
  </dialog>
</template>

<style scoped>
.tina-launcher {
  --dock-safe-top: env(safe-area-inset-top, 0px);
  --dock-safe-right: env(safe-area-inset-right, 0px);
  --dock-safe-bottom: env(safe-area-inset-bottom, 0px);
  --dock-safe-left: env(safe-area-inset-left, 0px);
  position: fixed;
  right: max(18px, calc((100vw - 1380px) / 2));
  bottom: max(18px, env(safe-area-inset-bottom));
  z-index: 40;
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 86px;
  padding: 0;
  border: 0;
  border-radius: 20px;
  background: transparent;
  color: #393c32;
  cursor: grab;
  touch-action: none;
  user-select: none;
  -webkit-user-select: none;
}
.tina-launcher.is-dragging {
  cursor: grabbing;
}
.tina-launcher.is-snapping {
  transition:
    left 460ms cubic-bezier(0.2, 0.75, 0.2, 1),
    top 460ms cubic-bezier(0.2, 0.75, 0.2, 1);
}
.tina-launcher .tina-launcher__mascot {
  width: 72px;
  height: 66px;
  filter: drop-shadow(0 4px 5px #292b2614);
  transition:
    transform 180ms ease-out,
    filter 180ms ease-out;
}
.tina-launcher.is-dragging .tina-launcher__mascot {
  transform: translateY(-4px) scale(1.08);
  filter: drop-shadow(0 12px 10px #292b2638);
}
.tina-launcher__label {
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 1px;
  padding: 4px 9px;
  border: 1px solid #e0e2d5;
  border-radius: 20px;
  background: #fffef9;
  box-shadow: 0 3px 10px #292b260c;
}
.tina-face {
  width: 34px;
  height: 34px;
  flex: none;
}
.tina-face--large {
  width: 50px;
  height: 46px;
}
.tina-unread {
  position: absolute;
  right: 7px;
  top: 7px;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--mm-primary);
}
.tina-dialog {
  max-width: none;
  position: fixed;
  inset: auto max(18px, calc((100vw - 1380px) / 2))
    max(18px, env(safe-area-inset-bottom)) auto;
  width: min(450px, calc(100vw - 28px));
  height: min(690px, calc(100dvh - 40px));
  max-height: calc(100dvh - 24px);
  margin: 0;
  padding: 0;
  border: 1px solid #ded7cd;
  border-radius: 20px;
  background: #f7f6f2;
  color: var(--mm-ink);
  box-shadow: 0 18px 70px #25241f40;
  overflow: hidden;
}
.tina-dialog[open] {
  animation: tina-open 220ms cubic-bezier(0.2, 0.8, 0.2, 1);
}
.tina-dialog--left {
  left: max(18px, env(safe-area-inset-left));
  right: auto;
}
@keyframes tina-open {
  from {
    opacity: 0;
    transform: translateY(18px) scale(0.97);
  }
  to {
    opacity: 1;
    transform: none;
  }
}
.tina-dialog::backdrop {
  background: #24252222;
}
.tina-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}
.tina-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 20px 18px 16px;
  background: #fffdf9;
  flex: none;
}
.tina-header h2 {
  margin: 0;
  font-size: 20px;
}
.tina-header h2 span {
  font-size: 11px;
  font-weight: 400;
  margin-left: 6px;
  color: #73695a;
}
.tina-header p {
  font-size: 11px;
  color: #73695a;
  margin: 4px 0 0;
}
.tina-icon-button {
  margin-left: auto;
  width: 32px;
  height: 32px;
  border: 0;
  background: transparent;
  font-size: 27px;
  color: #5d5d50;
  cursor: pointer;
}
.tina-tabs {
  display: flex;
  padding: 0 18px;
  border-bottom: 1px solid var(--mm-border);
  gap: 20px;
  flex: none;
}
.tina-tabs button {
  padding: 13px 0 11px;
  color: var(--mm-muted);
  border: 0;
  border-bottom: 2px solid transparent;
  background: transparent;
  font-size: 13px;
  cursor: pointer;
}
.tina-tabs button[aria-selected='true'] {
  border-color: var(--mm-primary);
  color: var(--mm-primary);
  font-weight: 700;
}
.tina-tabs .tina-refresh {
  margin-left: auto;
  font-size: 11px;
}
.tina-body {
  overscroll-behavior: contain;
  flex: 1;
  overflow-y: auto;
  min-height: 0;
  padding: 20px 18px;
  overscroll-behavior: contain;
}
.tina-welcome {
  margin: 0 0 18px;
}
.tina-eyebrow {
  color: #8b744f;
  font-size: 11px;
}
.tina-welcome h3 {
  font-size: 19px;
  margin: 8px 0;
}
.tina-welcome p,
.tina-state p {
  font-size: 13px;
  line-height: 1.7;
  color: var(--mm-muted);
}
.tina-state {
  padding: 15px;
  background: #f1f1e9;
  border-radius: 10px;
  font-size: 13px;
}
.tina-state button {
  border: 0;
  background: none;
  color: var(--mm-primary);
  padding: 0;
  cursor: pointer;
}
.tina-caption,
.tina-history-note {
  font-size: 12px;
  color: var(--mm-muted);
  line-height: 1.6;
}
.tina-history-note {
  text-align: center;
  font-size: 10px;
  margin: 18px 0;
}
.tina-messages {
  list-style: none;
  margin: 0;
  padding: 0;
}
.tina-messages li {
  margin: 0 0 20px;
}
.tina-message {
  max-width: 94%;
  margin: 0 0 12px;
  padding: 14px 16px;
  border-radius: 18px;
  font-size: 14px;
  line-height: 1.85;
  animation: tina-message-in 180ms ease-out;
  overflow-wrap: anywhere;
}
.tina-message p {
  margin: 4px 0 0;
  white-space: pre-wrap;
}
.tina-author {
  font-size: 10px;
  color: #786e60;
}
.tina-message--user {
  margin-left: auto;
  background: #eee2d3;
  width: fit-content;
  border-bottom-right-radius: 3px;
}
.tina-message--assistant {
  background: white;
  border: 1px solid #e6e2d9;
  border-bottom-left-radius: 3px;
  box-shadow: 0 2px 5px #322d2005;
}
.tina-thinking {
  display: flex;
  align-items: center;
  gap: 11px;
  margin: 18px 0;
  font-size: 13px;
  color: #75634e;
}
.tina-thinking .tina-face {
  width: 30px;
  height: 30px;
  font-size: 20px;
}
.tina-thinking small {
  display: block;
  font-size: 11px;
  color: #8b857b;
  margin-top: 5px;
}
.tina-dots {
  display: inline-flex;
  gap: 4px;
  margin-left: 10px;
}
.tina-dots i {
  width: 4px;
  height: 4px;
  background: #b57642;
  border-radius: 50%;
  animation: tina-dot 1.2s ease-in-out infinite;
}
.tina-dots i:nth-child(2) {
  animation-delay: 0.15s;
}
.tina-dots i:nth-child(3) {
  animation-delay: 0.3s;
}
@keyframes tina-dot {
  0%,
  70%,
  100% {
    transform: translateY(0);
    opacity: 0.35;
  }
  35% {
    transform: translateY(-4px);
    opacity: 1;
  }
}
@keyframes tina-message-in {
  from {
    opacity: 0;
    transform: translateY(7px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}
@media (prefers-reduced-motion: reduce) {
  .tina-dialog[open],
  .tina-message,
  .tina-dots i {
    animation: none;
  }
}
.tina-failure {
  font-size: 12px;
  color: #875132;
}
.tina-failure button {
  background: transparent;
  color: var(--mm-primary);
  border: 1px solid #dac1b0;
  border-radius: 6px;
  padding: 5px 10px;
  cursor: pointer;
}
.tina-typing {
  font-size: 12px;
  color: var(--mm-muted);
}
.tina-typing span {
  margin-left: 8px;
  letter-spacing: 3px;
}
.tina-faq {
  border-bottom: 1px solid var(--mm-border);
  padding: 13px 0;
  font-size: 13px;
}
.tina-faq summary {
  cursor: pointer;
  font-weight: 600;
}
.tina-faq p {
  color: var(--mm-muted);
  line-height: 1.8;
  white-space: pre-wrap;
}
.tina-error {
  margin: 0;
  padding: 8px 18px;
  background: #fff0e7;
  color: #9a3c1a;
  font-size: 12px;
  flex: none;
}
.tina-compose {
  padding: 12px 18px 8px;
  border-top: 1px solid var(--mm-border);
  background: white;
  flex: none;
}
.tina-compose textarea {
  display: block;
  resize: none;
  width: 100%;
  min-height: 58px;
  max-height: 120px;
  border: 0;
  padding: 0;
  background: transparent;
  font: inherit;
  font-size: 13px;
  line-height: 1.6;
  color: var(--mm-ink);
}
.tina-compose textarea:focus {
  outline: none;
}
.tina-compose:focus-within {
  box-shadow: inset 0 2px #bd5019;
}
.tina-compose div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.tina-compose small {
  font-size: 10px;
  color: var(--mm-muted);
}
.tina-compose button {
  min-height: 44px;
  min-width: 64px;
  padding: 7px 17px;
  background: var(--mm-primary);
  color: white;
  border: 0;
  border-radius: 7px;
  font-size: 12px;
  cursor: pointer;
}
.tina-footer {
  padding: 10px 18px 13px;
  flex: none;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 7px;
}
.tina-footer a,
.tina-footer button {
  font-size: 11px;
}
.tina-footer button {
  border: 0;
  background: transparent;
  color: var(--mm-muted);
  cursor: pointer;
}
.tina-footer p {
  flex-basis: 100%;
  margin: 0;
  color: #857f73;
  font-size: 9px;
  line-height: 1.5;
}
.tina-panel button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.tina-panel button:focus-visible,
.tina-launcher:focus-visible {
  outline: 2px solid var(--mm-primary);
  outline-offset: 3px;
}
@media (max-width: 600px) {
  .tina-launcher {
    right: 10px;
    bottom: max(14px, env(safe-area-inset-bottom));
    width: 76px;
  }
  .tina-launcher .tina-launcher__mascot {
    width: 64px;
    height: 58px;
  }
  .tina-dialog {
    left: 10px;
    right: 10px;
    bottom: max(10px, env(safe-area-inset-bottom));
    width: calc(100% - 20px);
    height: min(720px, calc(100dvh - 24px));
    max-height: calc(100dvh - 24px);
    border-radius: 18px;
  }
  .tina-header {
    padding: 15px;
  }
  .tina-header .tina-face {
    width: 38px;
    height: 38px;
    font-size: 26px;
  }
  .tina-body {
    padding: 16px;
  }
  .tina-compose {
    padding: 10px 16px 7px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .tina-launcher,
  .tina-launcher .tina-launcher__mascot {
    transition: none !important;
  }
  .tina-launcher.is-dragging .tina-launcher__mascot {
    transform: none;
  }
  .tina-launcher:hover {
    transform: none;
  }
}
.tina-dialog--compact .tina-header {
  padding: 8px 14px;
}
.tina-dialog--compact .tina-header p,
.tina-dialog--compact .tina-footer p {
  display: none;
}
.tina-dialog--compact .tina-compose textarea {
  min-height: 44px;
}
.tina-dialog--compact .tina-footer {
  padding: 4px 14px;
}
@media (max-height: 480px) {
  .tina-header {
    padding: 8px 14px;
  }
  .tina-header p,
  .tina-footer p {
    display: none;
  }
  .tina-compose textarea {
    min-height: 44px;
  }
  .tina-footer {
    padding: 4px 14px;
  }
}
</style>
