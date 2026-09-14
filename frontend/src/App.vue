<template>
  <div class="mm-app">
    <ConfirmationDialog />
    <header class="mm-header">
      <div class="mm-header__inner">
        <RouterLink to="/" class="mm-header__logo" aria-label="麦麦二手首页">
          <img src="/brand/maimai-wordmark.svg" alt="麦麦二手" height="32" />
        </RouterLink>

        <form class="mm-header__search" role="search" @submit.prevent="onSearch">
          <label class="mm-visually-hidden" for="mm-header-search">搜索商品</label>
          <input
            id="mm-header-search"
            v-model="keyword"
            type="search"
            placeholder="搜索闲置好物"
            maxlength="50"
          />
          <button type="submit" aria-label="搜索">搜索</button>
        </form>

        <button
          class="mm-header__menu-toggle"
          :aria-expanded="menuOpen"
          aria-label="打开导航菜单"
          @click="menuOpen = !menuOpen"
        >
          ☰
        </button>

        <nav class="mm-header__nav" :class="{ 'is-open': menuOpen }" aria-label="主导航">
          <RouterLink to="/community/demands" class="mm-header__link">求购</RouterLink>
          <template v-if="auth.me">
            <RouterLink to="/publish" class="mm-header__link">发布闲置</RouterLink>
            <RouterLink to="/cart" class="mm-header__link">购物车</RouterLink>
            <RouterLink to="/orders" class="mm-header__link">订单</RouterLink>
            <RouterLink to="/messages" class="mm-header__link">私信</RouterLink>
            <RouterLink v-if="auth.isAdmin" to="/admin" class="mm-header__link">后台</RouterLink>
            <RouterLink to="/me" class="mm-header__link mm-header__user">
              {{ auth.me.nickname }}
            </RouterLink>
            <button class="mm-header__link mm-header__logout" @click="onLogout">退出</button>
          </template>
          <template v-else>
            <RouterLink to="/login" class="mm-header__link">登录</RouterLink>
            <RouterLink to="/register" class="mm-header__link">注册</RouterLink>
          </template>
        </nav>
      </div>
    </header>

    <main class="mm-main">
      <router-view />
    </main>

    <footer class="mm-footer">
      <RouterLink to="/support">帮助与客服</RouterLink>
      <span aria-hidden="true"> · </span><RouterLink to="/policies">用户协议与交易售后规则</RouterLink>
      <p>© 2026 麦麦二手 · 让闲置遇见新主人</p>
      <p>本地开发版本，数据与支付均为开发环境，非真实交易</p>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from './shared/stores/auth'
import ConfirmationDialog from './shared/components/ConfirmationDialog.vue'
import {answerConfirmation} from './shared/confirm'

const router = useRouter()
const auth = useAuthStore()

const keyword = ref('')
const menuOpen = ref(false)
onMounted(()=>{if(!auth.meLoaded)void auth.fetchMe()})
watch(()=>router.currentRoute.value.fullPath,()=>{menuOpen.value=false;answerConfirmation(false)})

function onSearch() {
  const kw = keyword.value.trim()
  if (!kw) return
  menuOpen.value = false
  router.push({ path: '/search', query: { keyword: kw } })
}

async function onLogout() {
  try {
    await auth.logout()
  } finally {
    menuOpen.value = false
    router.push('/')
  }
}
</script>

<style scoped>
.mm-app {display:flex;flex-direction:column;min-height:100vh}
.mm-header {
  background-color: var(--mm-white);
  border-bottom: 1px solid var(--mm-border);
  position: sticky;
  top: 0;
  z-index: 10;
}

.mm-header__inner {
  display: flex;
  align-items: center;
  gap: var(--mm-space-4);
  max-width: 1440px;
  margin: 0 auto;
  padding: var(--mm-space-3) var(--mm-space-4);
}

.mm-header__logo img {
  height: 32px;
  width: auto;
}

.mm-header__search {
  display: flex;
  flex: 1;
  max-width: 480px;
}

.mm-header__search input {
  flex: 1;
  min-width: 0;
  min-height: 38px;
  padding: 0 var(--mm-space-3);
  border: 1px solid var(--mm-border);
  border-right: none;
  border-radius: var(--mm-radius-m) 0 0 var(--mm-radius-m);
}

.mm-header__search button {
  min-height: 38px;
  padding: 0 var(--mm-space-4);
  border: none;
  border-radius: 0 var(--mm-radius-m) var(--mm-radius-m) 0;
  background-color: var(--mm-primary);
  color: var(--mm-white);
  font-weight: 600;
}

.mm-header__search button:hover {
  background-color: var(--mm-primary-hover);
}

.mm-header__nav {
  display: flex;
  align-items: center;
  gap: var(--mm-space-3);
  margin-left: auto;
}

.mm-header__link {
  color: var(--mm-ink);
  font-size: var(--mm-font-base);
  white-space: nowrap;
  background: none;
  border: none;
  padding: 0;
}

.mm-header__link:hover {
  color: var(--mm-primary);
  text-decoration: none;
}

.mm-header__user {
  font-weight: 600;
  color: var(--mm-primary);
}

.mm-header__menu-toggle {
  display: none;
  background: none;
  border: 1px solid var(--mm-border);
  border-radius: var(--mm-radius-m);
  font-size: var(--mm-font-l);
  padding: var(--mm-space-1) var(--mm-space-3);
  color: var(--mm-ink);
}

.mm-main {
  flex: 1;
  width: 100%;
}

.mm-footer {
  border-top: 1px solid var(--mm-border);
  padding: var(--mm-space-5) var(--mm-space-4);
  text-align: center;
  font-size: var(--mm-font-s);
  color: var(--mm-muted);
  background-color: var(--mm-white);
}

@media (max-width: 1100px) {
  .mm-header__inner {
    flex-wrap: wrap;
  }

  .mm-header__search {
    order: 3;
    flex-basis: 100%;
    max-width: none;
  }

  .mm-header__menu-toggle {
    display: block;
    margin-left: auto;
  }

  .mm-header__nav {
    display: none;
    order: 4;
    flex-basis: 100%;
    flex-direction: column;
    align-items: stretch;
    gap: 0;
  }

  .mm-header__nav.is-open {
    display: flex;
  }

  .mm-header__nav .mm-header__link {
    padding: var(--mm-space-3) var(--mm-space-2);
    border-top: 1px solid var(--mm-border);
    text-align: left;
  }
}
</style>
