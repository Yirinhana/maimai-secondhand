<template>
  <div class="mm-auth">
    <AuthIntro />
    <MmCard title="登录麦麦二手" class="mm-auth__card">
      <p class="mm-auth__lead">欢迎回来，继续看看你的闲置与交易。</p>
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
          <MmButton type="submit" :loading="submitting">登录</MmButton>
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
import { nextTick, onUnmounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import MmButton from '../../shared/components/MmButton.vue';
import MmCard from '../../shared/components/MmCard.vue';
import MmInput from '../../shared/components/MmInput.vue';
import type { ApiError } from '../../shared/api';
import { useAuthStore } from '../../shared/stores/auth';

const router = useRouter();
const route = useRoute();
const auth = useAuthStore();

const form = reactive({ email: '', password: '' });
const errors = reactive({ email: '', password: '' });
const formError = ref('');
const submitting = ref(false);
const formElement = ref<HTMLFormElement | null>(null);
let disposed = false;
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
      typeof route.query.redirect === 'string' ? route.query.redirect : '/';
    router.push(
      redirect.startsWith('/') &&
        !redirect.startsWith('//') &&
        !redirect.includes('\\')
        ? redirect
        : '/',
    );
  } catch (e) {
    if (disposed) return;
    formError.value = (e as ApiError).message || '登录失败，请稍后重试';
  } finally {
    submitting.value = false;
  }
}
</script>

<style scoped>
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
