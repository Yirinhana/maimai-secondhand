<template>
  <div class="mm-auth">
    <AuthIntro />
    <MmCard
      :title="switching ? '切换账号' : '登录麦麦二手'"
      class="mm-auth__card"
    >
      <p class="mm-auth__lead">
        {{
          switching
            ? '选择本机登录过的账号，验证密码后即可切换。'
            : '欢迎回来，继续看看你的闲置与交易。'
        }}
      </p>
      <section
        v-if="deviceAccounts.length"
        class="mm-remembered"
        aria-label="本机登录过的账号"
      >
        <div class="mm-remembered__heading">
          <strong>本机账号</strong><span>最多保留 5 个</span>
        </div>
        <div
          v-for="account in deviceAccounts"
          :key="account.id"
          class="mm-remembered__row"
          :class="{ 'is-selected': form.email === account.email }"
        >
          <button
            type="button"
            class="mm-remembered__choose"
            :disabled="submitting"
            :aria-label="`选择账号 ${account.email}`"
            @click="chooseAccount(account)"
          >
            <UserAvatar
              :src="account.avatarUrl"
              :nickname="account.nickname"
              :size="38"
            />
            <span
              ><strong
                >{{ account.nickname
                }}<small v-if="auth.me?.id === account.id">当前</small></strong
              ><span>{{ account.email }}</span></span
            >
          </button>
          <button
            type="button"
            class="mm-remembered__forget"
            :disabled="submitting"
            :aria-label="`移除本机记录 ${account.email}`"
            @click="forgetAccount(account.id)"
          >
            移除
          </button>
        </div>
        <p>只记住头像、昵称和邮箱，不保存密码。移除记录不会注销账号。</p>
        <button
          type="button"
          class="mm-remembered__other"
          :disabled="submitting"
          @click="chooseOther"
        >
          使用其他账号
        </button>
      </section>
      <form
        ref="formElement"
        class="mm-auth__form"
        :aria-busy="submitting"
        novalidate
        @submit.prevent="onSubmit"
      >
        <fieldset class="mm-auth__fields" :disabled="submitting">
          <MmInput
            v-model="form.email"
            @update:model-value="
              errors.email = '';
              formError = '';
            "
            label="邮箱"
            type="email"
            placeholder="you@example.com"
            autocomplete="email"
            :error="errors.email"
          />
          <MmInput
            v-model="form.password"
            @update:model-value="
              errors.password = '';
              formError = '';
            "
            label="密码"
            type="password"
            placeholder="请输入密码"
            autocomplete="current-password"
            :error="errors.password"
          />
          <p v-if="formError" class="mm-auth__error" role="alert">
            {{ formError }}
          </p>
          <MmButton type="submit" :loading="submitting">{{
            switching ? '切换并登录' : '登录'
          }}</MmButton>
          <RouterLink
            v-if="switching && auth.me"
            :to="auth.isAdmin ? '/admin' : auth.isSeller ? '/seller' : '/'"
            >返回当前账号</RouterLink
          >
          <div class="mm-auth__links">
            <RouterLink to="/register">还没有账号？去注册</RouterLink>
            <RouterLink to="/forgot">忘记密码</RouterLink>
          </div>
        </fieldset>
      </form>
    </MmCard>
  </div>
</template>

<script setup lang="ts">
import AuthIntro from '../../shared/components/AuthIntro.vue';
import { computed, nextTick, onUnmounted, reactive, ref } from 'vue';
import { useRoute } from 'vue-router';
import MmButton from '../../shared/components/MmButton.vue';
import MmCard from '../../shared/components/MmCard.vue';
import MmInput from '../../shared/components/MmInput.vue';
import type { ApiError } from '../../shared/api';
import { useAuthStore } from '../../shared/stores/auth';
import UserAvatar from '../../shared/components/UserAvatar.vue';
import {
  deviceAccounts,
  forgetAccount,
  type DeviceAccount,
} from '../../shared/stores/deviceAccounts';

const route = useRoute();
const auth = useAuthStore();
const switching = computed(() => route.query.switch === '1');

const form = reactive({ email: '', password: '' });
const errors = reactive({ email: '', password: '' });
const formError = ref('');
const submitting = ref(false);
const formElement = ref<HTMLFormElement | null>(null);
let disposed = false;
async function chooseAccount(account: DeviceAccount) {
  form.email = account.email;
  form.password = '';
  errors.email = '';
  errors.password = '';
  formError.value = '';
  await nextTick();
  formElement.value
    ?.querySelector<HTMLInputElement>('[autocomplete="current-password"]')
    ?.focus();
}
async function chooseOther() {
  form.email = '';
  form.password = '';
  errors.email = '';
  errors.password = '';
  formError.value = '';
  await nextTick();
  formElement.value
    ?.querySelector<HTMLInputElement>('[autocomplete="email"]')
    ?.focus();
}
async function focusInvalidField() {
  await nextTick();
  formElement.value
    ?.querySelector<HTMLInputElement>('[aria-invalid="true"]')
    ?.focus();
}

onUnmounted(() => {
  disposed = true;
});

function validate(): boolean {
  errors.email = form.email.trim() ? '' : '请输入邮箱';
  if (!errors.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) {
    errors.email = '邮箱格式不正确';
  }
  errors.password = form.password ? '' : '请输入密码';
  return !errors.email && !errors.password;
}

async function onSubmit() {
  if (submitting.value) return;
  formError.value = '';
  if (!validate()) {
    await focusInvalidField();
    return;
  }
  submitting.value = true;
  try {
    await auth.login({ email: form.email.trim(), password: form.password });
    if (disposed) return;
    const redirect =
      typeof route.query.redirect === 'string'
        ? route.query.redirect
        : auth.isAdmin
          ? '/admin'
          : auth.isSeller
            ? '/seller'
            : '/';
    let destination = '/';
    try {
      const url = new URL(redirect, window.location.origin);
      if (redirect.startsWith('/') && url.origin === window.location.origin)
        destination = url.pathname + url.search + url.hash;
    } catch {
      /* Invalid redirect uses the home page. */
    }
    // A fresh document closes old conversations and discards all previous account stores.
    window.location.assign(destination);
  } catch (e) {
    if (disposed) return;
    formError.value = (e as ApiError).message || '登录失败，请稍后重试';
  } finally {
    submitting.value = false;
  }
}
</script>

<style scoped>
.mm-remembered {
  margin-bottom: 24px;
}
.mm-remembered__heading {
  display: flex;
  justify-content: space-between;
  margin-bottom: 10px;
  font-size: 13px;
}
.mm-remembered__heading > span,
.mm-remembered > p {
  font-size: 12px;
  color: var(--mm-muted);
  line-height: 1.7;
}
.mm-remembered__row {
  display: flex;
  align-items: center;
  border: 1px solid var(--mm-border);
  border-radius: 8px;
  margin: 8px 0;
  background: var(--mm-white);
}
.mm-remembered__row.is-selected {
  border-color: var(--mm-primary);
  background: var(--mm-accent-soft);
}
.mm-remembered__choose {
  display: flex;
  gap: 10px;
  align-items: center;
  text-align: left;
  padding: 12px;
  min-width: 0;
  flex: 1;
  background: none;
  border: 0;
}
.mm-remembered__choose > span {
  min-width: 0;
}
.mm-remembered__choose strong,
.mm-remembered__choose span span {
  display: block;
  overflow-wrap: anywhere;
}
.mm-remembered__choose span span {
  color: var(--mm-muted);
  font-size: 12px;
  margin-top: 4px;
}
.mm-remembered small {
  margin-left: 8px;
  color: var(--mm-primary);
  font-weight: 400;
}
.mm-remembered__forget,
.mm-remembered__other {
  border: 0;
  background: none;
  color: var(--mm-muted);
  padding: 10px;
  font-size: 13px;
}
.mm-remembered__other {
  padding-left: 0;
  color: var(--mm-primary);
}
.mm-auth {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 460px);
  gap: clamp(32px, 5vw, 64px);
  max-width: 1080px;
  margin: 0 auto;
  padding: 44px 28px;
  align-items: start;
}

.mm-auth__card {
  width: 100%;
  max-width: 460px;
  padding: 14px 10px;
  box-shadow: none;
  border-radius: 10px;
}

.mm-auth__form {
  display: flex;
  flex-direction: column;
  gap: var(--mm-space-4);
}

.mm-auth__error {
  font-size: var(--mm-font-s);
  color: var(--mm-danger);
}

.mm-auth__links {
  display: flex;
  justify-content: space-between;
  font-size: var(--mm-font-s);
}
@media (max-width: 760px) {
  .mm-auth {
    grid-template-columns: minmax(0, 1fr);
    gap: 20px;
    padding: 20px 16px 32px;
  }
  .mm-auth__card {
    max-width: none;
    padding: 8px 0;
  }
  .mm-auth__links {
    flex-wrap: wrap;
    gap: 12px;
  }
}
.mm-auth__lead {
  color: var(--mm-muted);
  font-size: 14px;
  line-height: 1.8;
  margin-bottom: 24px;
}
.mm-auth__fields {
  display: flex;
  flex-direction: column;
  gap: 20px;
  min-width: 0;
  border: 0;
  padding: 0;
  margin: 0;
}
.mm-auth__fields:disabled {
  opacity: 0.75;
}
.mm-auth__links {
  border-top: 1px solid var(--mm-border);
  padding-top: 18px;
  line-height: 1.6;
}
.mm-auth__error,
.mm-auth__hint {
  line-height: 1.7;
  overflow-wrap: anywhere;
}
.mm-auth__error button {
  display: block;
  margin-top: 8px;
  background: none;
  color: inherit;
  border: 0;
  padding: 4px 0;
  text-decoration: underline;
}
@media (max-width: 420px) {
  .mm-auth__code-row {
    display: grid;
    grid-template-columns: minmax(0, 1fr);
    gap: 10px;
  }
  .mm-auth__code-row > .mm-button {
    justify-self: start;
  }
  .mm-auth__card :deep(.mm-card__body),
  .mm-auth__card :deep(.mm-card__header) {
    padding-left: 20px;
    padding-right: 20px;
  }
}
</style>
