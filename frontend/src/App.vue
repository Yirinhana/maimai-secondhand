<template>
  <div class="mm-app" :class="`mm-app--${section}`">
    <ConfirmationDialog />
    <a href="#main-content" class="mm-skip">跳到正文</a>
    <header ref="headerRoot" class="mm-header">
      <div class="mm-header__inner">
        <RouterLink to="/" class="mm-brand" aria-label="麦麦二手首页"
          ><img src="/brand/maimai-symbol.svg" alt="" /><span
            >麦麦二手<small>MAIMAI MARKET</small></span
          ></RouterLink
        >
        <form
          class="mm-header__search"
          role="search"
          @submit.prevent="onSearch"
        >
          <MmIcon name="search" /><label
            class="mm-visually-hidden"
            for="mm-header-search"
            >搜索商品</label
          ><input
            id="mm-header-search"
            ref="searchInput"
            v-model="keyword"
            type="search"
            placeholder="找到你的下一件心头好"
            maxlength="50"
          /><kbd class="mm-header__search-key" aria-hidden="true">/</kbd
          ><button type="submit" aria-label="搜索">搜索</button>
        </form>
        <div class="mm-header__actions">
          <RouterLink to="/cart" class="mm-header__cart" aria-label="购物车"
            ><MmIcon name="cart" /><span>购物车</span></RouterLink
          >
          <div v-if="auth.me" ref="userRoot" class="mm-user">
            <button
              ref="userTrigger"
              class="mm-user__trigger"
              :aria-expanded="userOpen"
              aria-controls="mm-user-panel"
              aria-haspopup="menu"
              aria-label="打开个人菜单"
              @click="toggleUser"
              @keydown.down.prevent="openUser"
            >
              <UserAvatar
                :src="auth.me.avatarUrl"
                :nickname="auth.me.nickname"
              /><MmIcon name="chevron" />
            </button>
            <div
              v-if="userOpen"
              id="mm-user-panel"
              class="mm-user__panel"
              role="menu"
              @keydown="menuKeydown"
            >
              <div class="mm-user__identity">
                <UserAvatar
                  :src="auth.me.avatarUrl"
                  :nickname="auth.me.nickname"
                  :size="44"
                />
                <div>
                  <strong>{{ auth.me.nickname }}</strong
                  ><span>我的麦麦</span>
                </div>
              </div>
              <RouterLink role="menuitem" to="/"
                ><MmIcon name="grid" />首页</RouterLink
              >
              <RouterLink role="menuitem" to="/me"
                ><MmIcon name="user" />个人中心</RouterLink
              ><RouterLink role="menuitem" to="/orders"
                ><MmIcon name="bag" />我买到的</RouterLink
              ><RouterLink role="menuitem" to="/seller/products"
                ><MmIcon name="box" />卖家工作台</RouterLink
              ><RouterLink role="menuitem" to="/me/community"
                ><MmIcon name="heart" />收藏与关注</RouterLink
              ><RouterLink role="menuitem" to="/messages"
                ><MmIcon name="message" />私信</RouterLink
              ><RouterLink v-if="auth.isAdmin" role="menuitem" to="/admin"
                ><MmIcon name="grid" />管理后台</RouterLink
              >
              <button
                role="menuitem"
                class="mm-user__logout"
                :disabled="loggingOut"
                @click="onLogout"
              >
                {{ loggingOut ? '正在退出…' : '退出登录' }}
              </button>
              <p v-if="logoutError" class="mm-error" role="alert">
                {{ logoutError }}
              </p>
            </div>
          </div>
          <RouterLink v-else to="/login" class="mm-header__login"
            >登录 / 注册</RouterLink
          >
          <button
            ref="navTrigger"
            class="mm-header__menu-toggle"
            :aria-expanded="menuOpen"
            aria-controls="mm-main-nav"
            aria-label="打开导航菜单"
            @click="toggleNavigation"
          >
            <MmIcon :name="menuOpen ? 'close' : 'menu'" />
          </button>
        </div>
      </div>
      <div class="mm-header__nav-wrap">
        <nav
          id="mm-main-nav"
          ref="navRoot"
          class="mm-header__nav"
          :class="{ 'is-open': menuOpen }"
          aria-label="主导航"
        >
          <RouterLink
            v-for="item in primaryNav"
            :key="item.to"
            :to="item.to"
            :class="{ 'is-active': section === item.section }"
            :aria-current="section === item.section ? 'page' : undefined"
            >{{ item.label }}</RouterLink
          ><RouterLink
            v-if="auth.isAdmin"
            to="/admin"
            :class="{ 'is-active': section === 'admin' }"
            >管理后台</RouterLink
          ><RouterLink to="/publish" class="mm-header__publish"
            ><MmIcon name="plus" />发布闲置</RouterLink
          >
        </nav>
      </div>
    </header>
    <div v-if="route.name !== 'home' && section !== 'auth'" class="mm-location">
      <div class="mm-location__inner">
        <nav aria-label="当前位置" class="mm-breadcrumb">
          <RouterLink to="/">首页</RouterLink><span>/</span
          ><span>{{ sectionConfig.label }}</span
          ><template v-if="title !== sectionConfig.label"
            ><span>/</span><strong>{{ title }}</strong></template
          >
        </nav>
        <nav
          v-if="sectionConfig.links.length"
          aria-label="分区导航"
          class="mm-section-nav"
        >
          <RouterLink
            v-for="link in sectionConfig.links"
            :key="link.to"
            :to="link.to"
            :class="{ 'is-active': isSectionLinkActive(link.to) }"
            :aria-current="isSectionLinkActive(link.to) ? 'page' : undefined"
            >{{ link.label }}</RouterLink
          >
        </nav>
      </div>
    </div>
    <main
      id="main-content"
      tabindex="-1"
      class="mm-main"
      :class="`mm-main--${section}`"
    >
      <router-view />
    </main>
    <footer class="mm-footer">
      <div class="mm-footer__top">
        <div>
          <RouterLink to="/" class="mm-footer__brand">麦麦二手</RouterLink>
          <p>旧物的新故事，从这里开始。</p>
        </div>
        <nav aria-label="网站信息">
          <RouterLink to="/official">麦麦官方</RouterLink
          ><RouterLink to="/support">帮助与客服</RouterLink
          ><RouterLink to="/policies">用户协议与交易售后规则</RouterLink>
        </nav>
      </div>
      <div class="mm-footer__bottom">
        <span>© 2026 麦麦二手</span
        ><span>课程项目体验版 · 真实支付暂未接通</span>
      </div>
    </footer>
    <button
      v-if="showBackToTop"
      class="mm-back-top"
      type="button"
      aria-label="回到顶部"
      @click="backToTop"
    >
      <MmIcon name="arrow" /><span>顶部</span>
    </button>
  </div>
</template>
<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from './shared/stores/auth';
import ConfirmationDialog from './shared/components/ConfirmationDialog.vue';
import UserAvatar from './shared/components/UserAvatar.vue';
import MmIcon from './shared/components/MmIcon.vue';
import { answerConfirmation } from './shared/confirm';
import {
  activeDetailNavigation,
  pageInfo,
  sectionInfo,
} from './shared/navigation';
const route = useRoute(),
  router = useRouter(),
  auth = useAuthStore();
const keyword = ref(''),
  menuOpen = ref(false),
  userOpen = ref(false),
  loggingOut = ref(false),
  logoutError = ref('');
const userRoot = ref<HTMLElement | null>(null),
  userTrigger = ref<HTMLButtonElement | null>(null);
const headerRoot = ref<HTMLElement | null>(null),
  navRoot = ref<HTMLElement | null>(null),
  navTrigger = ref<HTMLButtonElement | null>(null),
  searchInput = ref<HTMLInputElement | null>(null),
  showBackToTop = ref(false);
async function toggleNavigation() {
  menuOpen.value = !menuOpen.value;
  userOpen.value = false;
  if (menuOpen.value) {
    await nextTick();
    navRoot.value?.querySelector<HTMLElement>('a')?.focus();
  }
}
function updateScroll() {
  showBackToTop.value = window.scrollY > 640;
}
function backToTop() {
  window.scrollTo({
    top: 0,
    behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches
      ? 'instant'
      : 'smooth',
  });
  searchInput.value?.focus({ preventScroll: true });
}
const info = computed(() => {
  const base = pageInfo[String(route.name)] ?? {
    title: String(route.meta.title ?? '页面'),
    section: route.path.startsWith('/admin') ? 'admin' : 'market',
  };
  const context = activeDetailNavigation.value;
  if (
    base.section === 'transaction' &&
    context?.routeKey === route.fullPath &&
    context.userId === auth.me?.id
  )
    return { ...base, section: context.section };
  return base;
});
const section = computed(() => info.value.section),
  title = computed(() => info.value.title),
  sectionConfig = computed(
    () => sectionInfo[section.value] ?? sectionInfo.market!,
  );
const primaryNav = [
  { to: '/', label: '逛逛闲置', section: 'market' },
  { to: '/community/demands', label: '求购社区', section: 'community' },
  { to: '/orders', label: '买家交易', section: 'buyer' },
  { to: '/seller/products', label: '卖家工作台', section: 'seller' },
  { to: '/messages', label: '消息', section: 'messages' },
  { to: '/official', label: '麦麦官方', section: 'official' },
];
function isSectionLinkActive(to: string) {
  const target = router.resolve(to);
  return (
    target.path === route.path &&
    (target.query.mine ?? '') === (route.query.mine ?? '')
  );
}
async function openUser() {
  userOpen.value = true;
  menuOpen.value = false;
  await nextTick();
  userRoot.value?.querySelector<HTMLElement>('[role="menuitem"]')?.focus();
}
function toggleUser() {
  if (userOpen.value) userOpen.value = false;
  else void openUser();
}
function outside(event: PointerEvent) {
  if (userRoot.value && !userRoot.value.contains(event.target as Node))
    userOpen.value = false;
  if (menuOpen.value && !headerRoot.value?.contains(event.target as Node))
    menuOpen.value = false;
}
function focusOutside(event: FocusEvent) {
  if (
    userOpen.value &&
    userRoot.value &&
    !userRoot.value.contains(event.target as Node)
  )
    userOpen.value = false;
  if (menuOpen.value && !headerRoot.value?.contains(event.target as Node))
    menuOpen.value = false;
}
function escape(event: KeyboardEvent) {
  if (
    event.key === '/' &&
    !event.ctrlKey &&
    !event.metaKey &&
    !event.altKey &&
    !(
      event.target instanceof HTMLElement &&
      event.target.closest(
        'input,textarea,select,[contenteditable="true"],[role="textbox"]',
      )
    ) &&
    !document.querySelector('dialog[open], [role="dialog"]')
  ) {
    event.preventDefault();
    searchInput.value?.focus();
    return;
  }
  if (event.key === 'Escape') {
    if (userOpen.value) {
      userOpen.value = false;
      userTrigger.value?.focus();
    }
    if (menuOpen.value) {
      menuOpen.value = false;
      navTrigger.value?.focus();
    }
  }
}
function menuKeydown(event: KeyboardEvent) {
  const items = Array.from(
    userRoot.value?.querySelectorAll<HTMLElement>('[role="menuitem"]') ?? [],
  );
  const index = items.indexOf(document.activeElement as HTMLElement);
  let next = index;
  if (event.key === 'ArrowDown') next = (index + 1) % items.length;
  else if (event.key === 'ArrowUp')
    next = (index - 1 + items.length) % items.length;
  else if (event.key === 'Home') next = 0;
  else if (event.key === 'End') next = items.length - 1;
  else return;
  event.preventDefault();
  items[next]?.focus();
}
function onSearch() {
  const kw = keyword.value.trim();
  void router.push({ path: '/search', query: { keyword: kw || undefined } });
}
async function onLogout() {
  loggingOut.value = true;
  logoutError.value = '';
  try {
    await auth.logout();
    userOpen.value = false;
    await router.push('/');
  } catch {
    logoutError.value = '退出失败，请稍后重试';
  } finally {
    loggingOut.value = false;
  }
}
onMounted(() => {
  if (!auth.meLoaded) void auth.fetchMe();
  document.addEventListener('pointerdown', outside);
  document.addEventListener('keydown', escape);
  document.addEventListener('focusin', focusOutside);
  window.addEventListener('scroll', updateScroll, { passive: true });
  updateScroll();
});
onUnmounted(() => {
  document.removeEventListener('pointerdown', outside);
  document.removeEventListener('keydown', escape);
  document.removeEventListener('focusin', focusOutside);
  window.removeEventListener('scroll', updateScroll);
});
watch(
  () => [route.path, route.query.keyword] as const,
  ([, value]) => {
    if (route.path === '/search')
      keyword.value = typeof value === 'string' ? value : '';
  },
  { immediate: true },
);
watch(
  () => route.fullPath,
  () => {
    menuOpen.value = false;
    userOpen.value = false;
    answerConfirmation(false);
  },
);
watch(
  title,
  () => {
    document.title = `${title.value} · 麦麦二手`;
  },
  { immediate: true },
);
</script>
<style scoped>
.mm-app {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}
.mm-skip {
  position: absolute;
  left: 12px;
  top: -60px;
  z-index: 100;
  background: #242421;
  color: #fff;
  padding: 10px;
}
.mm-skip:focus {
  top: 8px;
}
.mm-header {
  background: #fff;
  border-bottom: 1px solid var(--mm-border);
  position: relative;
  z-index: 30;
}
.mm-header__inner {
  max-width: 1280px;
  margin: auto;
  min-height: 94px;
  display: flex;
  align-items: center;
  gap: 48px;
  padding: 18px 28px;
}
.mm-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: none;
  color: var(--mm-ink);
  font-size: 25px;
  font-weight: 850;
  letter-spacing: -1px;
  line-height: 1.3;
}
.mm-brand:hover {
  text-decoration: none;
}
.mm-brand img {
  width: 41px;
  height: 41px;
}
.mm-brand small {
  display: block;
  font-size: 8px;
  letter-spacing: 2px;
  font-weight: 650;
  margin-top: 3px;
}
.mm-header__search {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  border: 1px solid var(--mm-border);
  border-radius: 8px;
  background: var(--mm-canvas);
  padding: 5px 6px 5px 15px;
  min-width: 0;
}
.mm-header__search:focus-within {
  border-color: #b6bbae;
  box-shadow: 0 0 0 3px #eceee7;
}
.mm-header__search-key {
  border: 1px solid var(--mm-border);
  border-radius: 4px;
  min-width: 20px;
  padding: 0 5px;
  text-align: center;
  font: 12px/22px inherit;
  color: var(--mm-muted);
}
.mm-header__search:focus-within .mm-header__search-key {
  visibility: hidden;
}
.mm-header__search > .mm-icon {
  width: 19px;
  color: var(--mm-muted);
}
.mm-header__search input {
  border: 0;
  outline: 0;
  min-width: 0;
  width: 100%;
  height: 34px;
  background: transparent;
  font-size: 14px;
}
.mm-header__search button {
  background: var(--mm-ink);
  color: #fff;
  border: 0;
  border-radius: 5px;
  padding: 9px 21px;
  white-space: nowrap;
  font-weight: 600;
}
.mm-header__actions {
  display: flex;
  align-items: center;
  gap: 25px;
  flex: none;
}
.mm-header__cart {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--mm-ink);
  font-size: 14px;
  min-height: 44px;
}
.mm-header__login {
  font-size: 14px;
  color: var(--mm-ink);
  font-weight: 650;
  white-space: nowrap;
}
.mm-header__menu-toggle {
  display: none;
  background: none;
  border: 0;
  padding: 6px;
}
.mm-header__nav-wrap {
  border-top: 1px solid #f3f1ed;
}
.mm-header__nav {
  max-width: 1280px;
  margin: auto;
  display: flex;
  align-items: center;
  gap: 33px;
  padding: 0 28px;
  min-height: 50px;
}
.mm-header__nav > a {
  position: relative;
  color: var(--mm-muted);
  font-weight: 600;
  font-size: 14px;
  padding: 14px 0;
  white-space: nowrap;
}
.mm-header__nav > a:hover {
  text-decoration: none;
  color: var(--mm-ink);
}
.mm-header__nav > a.is-active {
  color: var(--mm-ink);
}
.mm-header__nav > a.is-active::after {
  content: '';
  position: absolute;
  height: 3px;
  background: var(--mm-primary);
  left: 0;
  right: 0;
  bottom: -1px;
}
.mm-header__nav .mm-header__publish {
  margin-left: auto;
  color: var(--mm-primary);
  display: flex;
  align-items: center;
  gap: 5px;
}
.mm-header__publish .mm-icon {
  width: 17px;
}
.mm-user {
  position: relative;
}
.mm-user__trigger {
  display: flex;
  align-items: center;
  gap: 7px;
  border: 0;
  padding: 0;
  background: transparent;
  min-height: 44px;
}
.mm-user__trigger > .mm-icon {
  width: 14px;
}
.mm-user__panel {
  position: absolute;
  right: 0;
  top: calc(100% + 15px);
  width: 250px;
  max-width: calc(100vw - 32px);
  padding: 10px;
  background: #fff;
  border: 1px solid var(--mm-border);
  border-radius: 12px;
  box-shadow: 0 12px 44px #29221c1f;
  z-index: 50;
}
.mm-user__identity {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 10px 9px 18px;
  margin-bottom: 7px;
  border-bottom: 1px solid var(--mm-border);
}
.mm-user__identity > div {
  min-width: 0;
}
.mm-user__identity strong {
  display: block;
  overflow-wrap: anywhere;
}
.mm-user__identity > div > span {
  display: block;
  color: var(--mm-muted);
  font-size: 12px;
}
.mm-user__panel > a,
.mm-user__panel > button {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 10px 11px;
  border: 0;
  border-radius: 6px;
  background: none;
  color: var(--mm-ink);
  font-size: 14px;
  text-align: left;
}
.mm-user__panel > a:hover,
.mm-user__panel > button:hover {
  background: var(--mm-canvas);
  text-decoration: none;
}
.mm-user__panel .mm-icon {
  width: 18px;
  height: 18px;
  color: var(--mm-muted);
}
.mm-user__panel > .mm-user__logout {
  border-top: 1px solid var(--mm-border);
  margin-top: 7px;
  color: var(--mm-danger);
  border-radius: 0;
}
.mm-location {
  background: #fff;
  border-bottom: 1px solid var(--mm-border);
}
.mm-location__inner {
  max-width: 1280px;
  margin: auto;
  padding: 14px 28px 0;
}
.mm-breadcrumb {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
  font-size: 12px;
  color: var(--mm-muted);
  padding-bottom: 14px;
}
.mm-breadcrumb a {
  color: var(--mm-muted);
}
.mm-breadcrumb strong {
  font-weight: 500;
  color: var(--mm-ink);
}
.mm-section-nav {
  display: flex;
  gap: 25px;
  overflow-x: auto;
  padding: 0 0 1px;
}
.mm-section-nav > a {
  white-space: nowrap;
  color: var(--mm-muted);
  font-size: 13px;
  padding: 8px 0 12px;
}
.mm-section-nav > a.is-active {
  color: var(--mm-primary);
  font-weight: 700;
}
.mm-main {
  flex: 1;
  width: 100%;
  min-width: 0;
}
.mm-main:focus {
  outline: none;
}
.mm-back-top {
  position: fixed;
  right: max(18px, calc((100vw - 1380px) / 2));
  bottom: 26px;
  z-index: 15;
  width: 48px;
  min-height: 58px;
  border: 1px solid var(--mm-border);
  border-radius: 10px;
  background: white;
  color: var(--mm-muted);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  box-shadow: 0 3px 14px #2425220b;
}
.mm-back-top .mm-icon {
  transform: rotate(-90deg);
  width: 18px;
  height: 18px;
}
.mm-back-top span {
  font-size: 11px;
}
.mm-back-top:hover {
  color: var(--mm-ink);
  border-color: #b8bfb1;
}
.mm-main--messages {
  background: #eef1ef;
}
.mm-main--admin {
  background: #f0f1f2;
}
.mm-main--market {
  background: #faf9f6;
}
.mm-main--auth {
  background: #f4f0e8;
}
.mm-footer {
  background: #fff;
  border-top: 1px solid var(--mm-border);
  padding: 38px max(28px, calc((100vw - 1224px) / 2));
  margin-top: auto;
}
.mm-footer__top,
.mm-footer__bottom {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  align-items: center;
}
.mm-footer__brand {
  font-size: 21px;
  font-weight: 800;
  color: var(--mm-ink);
}
.mm-footer__top p {
  color: var(--mm-muted);
  font-size: 13px;
  margin-top: 5px;
}
.mm-footer nav {
  display: flex;
  gap: 25px;
  flex-wrap: wrap;
  font-size: 13px;
}
.mm-footer nav a {
  color: var(--mm-ink);
}
.mm-footer__bottom {
  font-size: 11px;
  color: var(--mm-muted);
  border-top: 1px solid var(--mm-border);
  margin-top: 25px;
  padding-top: 18px;
}
@media (max-width: 1000px) {
  .mm-header__inner {
    gap: 24px;
  }
  .mm-header__nav {
    gap: 23px;
  }
  .mm-header__actions {
    gap: 15px;
  }
  .mm-header__cart span {
    display: none;
  }
  .mm-brand {
    font-size: 22px;
  }
  .mm-brand img {
    width: 36px;
    height: 36px;
  }
}
@media (max-width: 760px) {
  .mm-header__inner {
    padding: 11px 16px;
    min-height: 0;
    flex-wrap: wrap;
    gap: 10px;
  }
  .mm-header__actions {
    margin-left: auto;
    gap: 6px;
  }
  .mm-brand {
    font-size: 21px;
  }
  .mm-brand img {
    width: 33px;
    height: 33px;
  }
  .mm-header__menu-toggle {
    display: grid;
    place-items: center;
    width: 40px;
    height: 44px;
  }
  .mm-header__search {
    order: 3;
    flex-basis: 100%;
  }
  .mm-header__nav {
    display: none;
    flex-wrap: wrap;
    gap: 0;
    padding: 8px 18px 14px;
  }
  .mm-header__nav.is-open {
    display: flex;
  }
  .mm-header__nav > a {
    flex-basis: 50%;
    padding: 12px 10px;
    font-size: 14px;
  }
  .mm-header__nav > a.is-active::after {
    display: none;
  }
  .mm-header__nav > a.is-active {
    background: var(--mm-accent-soft);
    border-radius: 6px;
    color: var(--mm-primary);
  }
  .mm-header__nav .mm-header__publish {
    margin-left: 0;
  }
  .mm-header__cart {
    display: flex;
    justify-content: center;
    width: 36px;
  }
  .mm-header__search-key {
    display: none;
  }
  .mm-header__login {
    font-size: 12px;
  }
  .mm-back-top {
    bottom: 104px;
    right: 12px;
    width: 42px;
    min-height: 48px;
  }
  .mm-location__inner {
    padding: 13px 18px 0;
  }
  .mm-section-nav {
    gap: 23px;
  }
  .mm-footer {
    padding: 27px 18px;
  }
  .mm-footer__top,
  .mm-footer__bottom {
    align-items: flex-start;
    flex-direction: column;
  }
  .mm-footer nav {
    gap: 12px 20px;
  }
  .mm-footer__bottom {
    gap: 7px;
  }
}
</style>
