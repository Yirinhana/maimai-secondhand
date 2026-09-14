<template>
  <div class="mm-auth">
    <AuthIntro />
    <MmCard title="登录麦麦二手" class="mm-auth__card">
      <form class="mm-auth__form" novalidate @submit.prevent="onSubmit">
        <MmInput
          v-model="form.email"
          label="邮箱"
          type="email"
          placeholder="you@example.com"
          autocomplete="email"
          :error="errors.email"
        />
        <MmInput
          v-model="form.password"
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
      </form>
    </MmCard>
  </div>
</template>

<script setup lang="ts">
import AuthIntro from '../../shared/components/AuthIntro.vue';
import { reactive, ref } from 'vue';
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

function validate(): boolean {
  errors.email = form.email.trim() ? '' : '请输入邮箱';
  if (!errors.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) {
    errors.email = '邮箱格式不正确';
  }
  errors.password = form.password ? '' : '请输入密码';
  return !errors.email && !errors.password;
}

async function onSubmit() {
  formError.value = '';
  if (!validate()) return;
  submitting.value = true;
  try {
    await auth.login({ email: form.email.trim(), password: form.password });
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
  gap: 60px;
  max-width: 1080px;
  margin: 0 auto;
  padding: 60px 28px;
  align-items: center;
}

.mm-auth__card {
  width: 100%;
  max-width: 460px;
  padding: 20px 10px;
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
    gap: 26px;
    padding: 28px 18px 38px;
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
</style>
