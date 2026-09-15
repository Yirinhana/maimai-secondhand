<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import WelcomeOpening from './components/WelcomeOpening.vue';
import HomeIntroduction from './components/HomeIntroduction.vue';
import WelcomeMascot from './components/WelcomeMascot.vue';
import MmIcon from '../../shared/components/MmIcon.vue';
import { get } from '../../shared/api';
import type { Page, ProductSummary } from '../../shared/types';
import { rememberWelcome } from './welcomeSession';
import { useHomeMotion } from './useHomeMotion';

const router = useRouter();
const root = ref<HTMLElement | null>(null);
const mascot = ref<HTMLElement | null>(null);
const sceneRoot = ref<HTMLElement | null>(null);
const { animated, paused, reduced, greeting, point, reset, greet } =
  useHomeMotion(root, mascot);
const products = ref<ProductSummary[]>([]);
const savedStage: unknown = window.history.state?.maimaiWelcomeStage;
const stage = ref(
  typeof savedStage === 'number' &&
    Number.isInteger(savedStage) &&
    savedStage >= 0 &&
    savedStage < 4
    ? savedStage
    : 0,
);
const stages = ['初见', '发现', '了解', '流转'];
const direction = ref('scene-forward');
const leaving = ref(false);
const navigationError = ref('');
const nextLabel = computed(() =>
  stage.value === 0 ? '认识麦麦' : stage.value === 3 ? '开始逛逛' : '下一幕',
);
let exitTimer: ReturnType<typeof setTimeout> | undefined;
let disposed = false;
let restoreSceneFocus = false;

onMounted(async () => {
  rememberWelcome();
  try {
    const result = await get<Page<ProductSummary>>('/products', { size: 3 });
    if (!disposed) products.value = result.content;
  } catch {
    // Empty art keeps the introduction usable when product data is unavailable.
  }
});
async function go(index: number) {
  const target = Math.max(0, Math.min(stages.length - 1, index));
  if (target === stage.value || leaving.value) return;
  restoreSceneFocus = Boolean(
    sceneRoot.value?.contains(document.activeElement),
  );
  direction.value = target > stage.value ? 'scene-forward' : 'scene-backward';
  stage.value = target;
  try {
    // Preserve this scene when returning from a product; a new replay starts at 0.
    window.history.replaceState(
      { ...window.history.state, maimaiWelcomeStage: target },
      '',
    );
  } catch {
    // History persistence is optional; the scene is already available.
  }
  await nextTick();
  sceneRoot.value?.scrollTo({ top: 0 });
}
function sceneEntered() {
  if (restoreSceneFocus) sceneRoot.value?.focus({ preventScroll: true });
  restoreSceneFocus = false;
}
function keyboard(event: KeyboardEvent) {
  if (
    event.ctrlKey ||
    event.metaKey ||
    event.altKey ||
    event.shiftKey ||
    (event.target instanceof HTMLElement &&
      event.target.closest('input,textarea,select,[contenteditable="true"]'))
  )
    return;
  if (event.key === 'ArrowRight' || event.key === 'ArrowLeft') {
    event.preventDefault();
    void go(stage.value + (event.key === 'ArrowRight' ? 1 : -1));
  }
}
async function finish() {
  try {
    await router.replace('/');
  } catch {
    leaving.value = false;
    navigationError.value = '暂时未能打开首页，请再试一次。';
  }
}
function enter(event: MouseEvent, skip = false) {
  if (
    event.button !== 0 ||
    event.ctrlKey ||
    event.metaKey ||
    event.shiftKey ||
    event.altKey
  )
    return;
  event.preventDefault();
  if (leaving.value) return;
  rememberWelcome();
  if (skip || !animated.value) {
    void finish();
    return;
  }
  leaving.value = true;
  exitTimer = setTimeout(() => void finish(), 650);
}
onUnmounted(() => {
  disposed = true;
  if (exitTimer) clearTimeout(exitTimer);
});
</script>

<template>
  <main
    id="main-content"
    ref="root"
    class="welcome-page"
    :class="{ 'is-leaving': leaving, 'is-still': !animated }"
    @pointermove="point"
    @pointerdown="point"
    @pointerleave="reset"
    @keydown="keyboard"
  >
    <div class="welcome-atmosphere" aria-hidden="true">
      <span
        class="welcome-atmosphere__orbit welcome-atmosphere__orbit--one"
      ></span>
      <span
        class="welcome-atmosphere__orbit welcome-atmosphere__orbit--two"
      ></span>
      <span class="welcome-atmosphere__seed welcome-atmosphere__seed--one"
        >✳</span
      >
      <span
        class="welcome-atmosphere__seed welcome-atmosphere__seed--two"
      ></span>
    </div>
    <header class="welcome-header">
      <div class="welcome-header__brand">
        <img
          src="/brand/maimai-symbol.svg"
          alt="麦麦二手 Logo"
          width="40"
          height="40"
        /><span>MAIMAI MARKET</span>
      </div>
      <div class="welcome-header__actions">
        <button
          type="button"
          :aria-pressed="paused || reduced"
          :disabled="reduced"
          @click="paused = !paused"
        >
          <span aria-hidden="true">{{ paused || reduced ? '▷' : 'Ⅱ' }}</span
          >{{ reduced ? '静态模式' : paused ? '恢复互动' : '暂停互动' }}
        </button>
        <a href="/" @click="enter($event, true)"
          >跳过开场 <MmIcon name="arrow"
        /></a>
      </div>
    </header>

    <div
      ref="sceneRoot"
      class="welcome-scenes"
      role="region"
      aria-label="麦麦介绍"
      tabindex="-1"
    >
      <Transition :name="direction" mode="out-in" @after-enter="sceneEntered">
        <div :key="stage" class="welcome-scene">
          <WelcomeOpening v-if="stage === 0" @enter="enter" />
          <HomeIntroduction v-else :stage="stage" :products="products" />
        </div>
      </Transition>
    </div>

    <footer class="welcome-controls">
      <div class="welcome-companion">
        <button
          ref="mascot"
          class="welcome-companion__button"
          type="button"
          aria-label="和麦仔打招呼"
          @click="greet"
        >
          <WelcomeMascot :greeting="greeting" />
        </button>
        <div class="welcome-companion__words">
          <strong>{{ greeting ? '嗨，见到你真好。' : '麦仔陪你逛' }}</strong
          ><span>{{
            paused || reduced ? '点我，打个招呼' : '动动鼠标，也可以点我'
          }}</span>
        </div>
      </div>
      <nav class="welcome-progress" aria-label="开场进度">
        <button
          v-for="(name, index) in stages"
          :key="name"
          type="button"
          :aria-label="'第' + (index + 1) + '幕：' + name"
          :aria-current="stage === index ? 'step' : undefined"
          @click="go(index)"
        >
          <span></span><span>{{ name }}</span>
        </button>
      </nav>
      <div class="welcome-controls__actions">
        <button
          class="welcome-previous"
          type="button"
          aria-label="上一幕"
          :disabled="stage === 0"
          @click="go(stage - 1)"
        >
          <MmIcon name="arrow" />
        </button>
        <button
          v-if="stage < 3"
          class="welcome-next"
          type="button"
          @click="go(stage + 1)"
        >
          {{ nextLabel }} <MmIcon name="arrow" />
        </button>
        <a v-else class="welcome-next" href="/" @click="enter"
          >{{ nextLabel }} <MmIcon name="arrow"
        /></a>
      </div>
    </footer>
    <p class="mm-visually-hidden" role="status" aria-live="polite">
      第 {{ stage + 1 }} 幕，共 4 幕：{{ stages[stage] }}
    </p>
    <p v-if="navigationError" class="welcome-navigation-error" role="alert">
      {{ navigationError }}
    </p>
  </main>
</template>

<style scoped>
.welcome-page {
  height: 100svh;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  position: relative;
  isolation: isolate;
  overflow: hidden;
  background: #faf9f6;
  color: #292b26;
  --look-x: 0;
  --look-y: 0;
}
.welcome-page.is-leaving {
  animation: welcome-leave 650ms ease both;
  pointer-events: none;
}
.welcome-atmosphere {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
  z-index: -1;
  transform: translate(var(--drift-x, 0px), var(--drift-y, 0px));
  transition: transform 250ms ease-out;
}
.welcome-atmosphere__orbit {
  position: absolute;
  border: 1px solid #e5e3d8;
  border-radius: 50%;
  animation: atmosphere-breathe 12s ease-in-out infinite;
}
.welcome-atmosphere__orbit--one {
  width: 85vw;
  height: 65vw;
  right: -32vw;
  top: -50vw;
  transform: rotate(-20deg);
}
.welcome-atmosphere__orbit--two {
  width: 46vw;
  height: 46vw;
  left: -29vw;
  bottom: -31vw;
  animation-delay: -6s;
  border-width: 2px;
}
.welcome-atmosphere__seed {
  position: absolute;
  color: #dedfd2;
  animation: atmosphere-turn 24s linear infinite;
}
.welcome-atmosphere__seed--one {
  right: 6%;
  top: 34%;
  font-size: 58px;
  line-height: 1;
}
.welcome-atmosphere__seed--two {
  left: 7%;
  top: 26%;
  width: 13px;
  height: 13px;
  background: #e4e3d8;
  border-radius: 50%;
}
.welcome-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 22px clamp(24px, 4vw, 64px) 12px;
  z-index: 2;
}
.welcome-header__brand,
.welcome-header__actions {
  display: flex;
  align-items: center;
  gap: 16px;
}
.welcome-header__brand > span {
  font-size: 10px;
  letter-spacing: 2px;
  color: #77786e;
}
.welcome-header__actions {
  gap: 26px;
}
.welcome-header__actions button,
.welcome-header__actions a {
  display: inline-flex;
  align-items: center;
  gap: 11px;
  background: none;
  border: 0;
  min-height: 44px;
  padding: 0;
  font-size: 12px;
  color: #616457;
}
.welcome-header__actions button:disabled {
  opacity: 0.65;
  cursor: default;
}
.welcome-header .mm-icon {
  width: 17px;
  height: 17px;
}
.welcome-scenes {
  display: grid;
  min-height: 0;
  overflow: auto;
  overflow-x: hidden;
  scrollbar-width: thin;
  padding: 0 clamp(24px, 4vw, 64px);
  overscroll-behavior: contain;
}
.welcome-scenes:focus {
  outline: none;
}
.welcome-scene {
  align-self: center;
  min-width: 0;
  width: 100%;
}
.welcome-controls {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 25px;
  min-height: 130px;
  padding: 0 clamp(24px, 4vw, 64px) 20px;
  z-index: 2;
}
.welcome-companion {
  display: flex;
  align-items: center;
  gap: 7px;
}
.welcome-companion__button {
  border: 0;
  background: none;
  width: 108px;
  padding: 0;
  flex: none;
  cursor: pointer;
  border-radius: 50%;
}
.welcome-companion__words {
  display: flex;
  flex-direction: column;
  gap: 7px;
}
.welcome-companion__words strong {
  font-size: 11px;
  font-weight: 500;
}
.welcome-companion__words span {
  font-size: 10px;
  color: #8b8c80;
}
.welcome-progress {
  display: flex;
  align-items: center;
  gap: 10px;
}
.welcome-progress button {
  min-height: 48px;
  min-width: 34px;
  padding: 6px 2px;
  display: flex;
  align-items: center;
  flex-direction: column;
  justify-content: center;
  gap: 9px;
  border: 0;
  background: none;
  color: #949789;
  font-size: 10px;
}
.welcome-progress button > span:first-child {
  display: block;
  width: 6px;
  height: 6px;
  background: #cfd1c4;
  border-radius: 10px;
  transition:
    width 300ms,
    background-color 300ms;
}
.welcome-progress button[aria-current='step'] {
  color: #414937;
}
.welcome-progress button[aria-current='step'] > span:first-child {
  width: 24px;
  background: #656f54;
}
.welcome-controls__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
}
.welcome-controls__actions .mm-icon {
  width: 18px;
  height: 18px;
}
.welcome-previous {
  display: grid;
  place-items: center;
  min-width: 44px;
  min-height: 46px;
  border: 1px solid #dedfd3;
  border-radius: 50%;
  background: transparent;
  color: #656a59;
}
.welcome-previous .mm-icon {
  transform: rotate(180deg);
}
.welcome-previous:disabled {
  opacity: 0.35;
  cursor: default;
}
.welcome-next {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 30px;
  min-height: 46px;
  padding: 10px 18px;
  border: 1px solid #afb39f;
  background: transparent;
  color: #343b2b;
  border-radius: 4px;
  font-size: 12px;
}
.welcome-next:hover {
  text-decoration: none;
  background: #eaece1;
}
.welcome-page :deep(button:focus-visible),
.welcome-page :deep(a:focus-visible) {
  outline: 2px solid #7b8867;
  outline-offset: 4px;
}
.welcome-page.is-still :deep(*) {
  animation: none !important;
  transition: none !important;
}
.scene-forward-enter-active,
.scene-forward-leave-active,
.scene-backward-enter-active,
.scene-backward-leave-active {
  transition:
    opacity 270ms ease,
    transform 360ms cubic-bezier(0.2, 0.6, 0.2, 1);
}
.scene-forward-enter-from,
.scene-backward-leave-to {
  opacity: 0;
  transform: translateX(22px);
}
.scene-forward-leave-to,
.scene-backward-enter-from {
  opacity: 0;
  transform: translateX(-22px);
}
.welcome-navigation-error {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  background: #fff4e8;
  padding: 8px;
  text-align: center;
  font-size: 12px;
}
@keyframes atmosphere-breathe {
  0%,
  100% {
    scale: 1;
  }
  50% {
    scale: 1.035;
  }
}
@keyframes atmosphere-turn {
  to {
    rotate: 360deg;
  }
}
@keyframes welcome-leave {
  to {
    opacity: 0;
    filter: blur(5px);
  }
}
@media (max-width: 1000px) {
  .welcome-companion__words {
    display: none;
  }
  .welcome-controls {
    grid-template-columns: 1fr auto 1fr;
  }
}
@media (max-width: 760px) {
  .welcome-header {
    padding: 14px 20px 8px;
    gap: 10px;
  }
  .welcome-header__brand {
    gap: 9px;
  }
  .welcome-header__brand img {
    width: 34px;
    height: 34px;
  }
  .welcome-header__brand > span {
    display: none;
  }
  .welcome-header__actions {
    gap: 19px;
  }
  .welcome-header__actions button,
  .welcome-header__actions a {
    font-size: 11px;
    gap: 7px;
  }
  .welcome-scenes {
    padding: 0 24px;
  }
  .welcome-controls {
    grid-template-columns: 84px minmax(0, 1fr);
    gap: 0 10px;
    padding: 8px 24px 16px;
    min-height: 116px;
  }
  .welcome-companion {
    grid-row: 1 / 3;
    grid-column: 1;
  }
  .welcome-companion__button {
    width: 88px;
  }
  .welcome-progress {
    grid-column: 2;
    grid-row: 1;
    justify-content: flex-end;
    gap: 5px;
  }
  .welcome-progress button {
    min-width: 36px;
    min-height: 37px;
    flex-direction: row;
    padding: 4px 0;
  }
  .welcome-progress button > span:last-child {
    display: none;
  }
  .welcome-controls__actions {
    grid-column: 2;
    grid-row: 2;
    gap: 10px;
  }
  .welcome-previous {
    min-height: 42px;
    min-width: 42px;
  }
  .welcome-next {
    min-height: 42px;
    padding: 9px 14px;
    gap: 26px;
    font-size: 11px;
  }
  .welcome-atmosphere__orbit--one {
    width: 140vw;
    height: 130vw;
    right: -85vw;
    top: -92vw;
  }
  .welcome-atmosphere__seed--one {
    right: 4%;
    top: 20%;
    font-size: 32px;
  }
  .welcome-atmosphere__seed--two {
    left: 7%;
    top: 67%;
    width: 9px;
    height: 9px;
  }
}
@media (max-height: 550px) and (min-width: 761px) {
  .welcome-header {
    padding-top: 8px;
    padding-bottom: 4px;
  }
  .welcome-controls {
    min-height: 82px;
    padding-bottom: 6px;
  }
  .welcome-companion__button {
    width: 80px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .welcome-page,
  .welcome-page :deep(*) {
    animation: none !important;
    transition: none !important;
  }
}
</style>
