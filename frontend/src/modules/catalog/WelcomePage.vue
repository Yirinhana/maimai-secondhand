<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import WelcomeOpening from './components/WelcomeOpening.vue';
import HomeIntroduction from './components/HomeIntroduction.vue';
import { get } from '../../shared/api';
import type { Page, ProductSummary } from '../../shared/types';
import { rememberWelcome } from './welcomeSession';

const router = useRouter();
const products = ref<ProductSummary[]>([]);
const leaving = ref(false);
let exitTimer: ReturnType<typeof setTimeout> | undefined;
let disposed = false;

onMounted(async () => {
  rememberWelcome();
  try {
    const result = await get<Page<ProductSummary>>('/products', { size: 3 });
    if (!disposed) products.value = result.content;
  } catch {
    // The welcome story remains available without catalog data.
  }
});

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
  if (skip || window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    void router.replace('/');
    return;
  }
  leaving.value = true;
  exitTimer = setTimeout(() => void router.replace('/'), 850);
}

onUnmounted(() => {
  disposed = true;
  if (exitTimer) clearTimeout(exitTimer);
});
</script>

<template>
  <main
    id="main-content"
    class="welcome-page"
    :class="{ 'is-leaving': leaving }"
  >
    <WelcomeOpening @enter="enter" />
    <div class="welcome-page__story">
      <HomeIntroduction :products="products" @enter="enter" />
      <div class="welcome-page__finish">
        <p>下一件喜欢，正在等你。</p>
        <a href="/" @click="enter"
          >进入麦麦二手 <span aria-hidden="true">↗</span></a
        >
      </div>
    </div>
  </main>
</template>

<style scoped>
.welcome-page {
  background: #faf9f6;
  min-height: 100vh;
}
.welcome-page.is-leaving {
  animation: welcome-leave 850ms cubic-bezier(0.4, 0, 0.2, 1) both;
  pointer-events: none;
}
.welcome-page__story {
  max-width: 1280px;
  margin: auto;
  padding: 0 36px 70px;
}
.welcome-page__finish {
  padding: 92px 20px 30px;
  text-align: center;
}
.welcome-page__finish p {
  font-size: clamp(22px, 3vw, 36px);
  margin-bottom: 28px;
}
.welcome-page__finish a {
  display: inline-flex;
  gap: 60px;
  align-items: center;
  min-height: 54px;
  padding: 14px 26px;
  border: 1px solid #bbb7aa;
  color: #292b26;
  border-radius: 4px;
}
@keyframes welcome-leave {
  to {
    opacity: 0;
    filter: blur(6px);
  }
}
@media (max-width: 760px) {
  .welcome-page__story {
    padding: 0 20px 35px;
  }
  .welcome-page__finish {
    padding: 65px 0 25px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .welcome-page.is-leaving {
    animation: none;
  }
}
</style>
