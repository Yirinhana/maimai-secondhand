<template>
  <div class="mm-auth">
    <AuthIntro />
    <MmCard title="找回密码" class="mm-auth__card">
      <p class="mm-auth__lead">通过注册邮箱验证身份，再设置新的登录密码。</p>
      <form
        v-if="!done"
        ref="formElement"
        :aria-busy="submitting"
        class="mm-auth__form"
        novalidate
        @submit.prevent="onSubmit"
      >
        <fieldset class="mm-auth__fields" :disabled="submitting || sendingCode">
          <MmInput
            v-model="form.email"
            @update:model-value="
              errors.email = '';
              formError = '';
            "
            label="注册邮箱"
            type="email"
            placeholder="you@example.com"
            autocomplete="email"
            :error="errors.email"
          />
          <div class="mm-auth__code-row">
            <MmInput
              v-model="form.code"
              @update:model-value="
                errors.code = '';
                formError = '';
              "
              label="邮箱验证码"
              placeholder="6 位验证码"
              maxlength="6"
              autocomplete="one-time-code"
              inputmode="numeric"
              :error="errors.code"
              class="mm-auth__code-input"
            />
            <MmButton
              variant="ghost"
              :disabled="cooldown > 0 || sendingCode"
              :loading="sendingCode"
              @click="onSendCode"
            >
              {{ cooldown > 0 ? `${cooldown} 秒后重发` : '发送验证码' }}
            </MmButton>
          </div>
          <p
            v-if="codeHint"
            :role="codeFailed ? 'alert' : 'status'"
            :class="codeFailed ? 'mm-auth__error' : 'mm-auth__hint'"
          >
            {{ codeHint }}
          </p>
          <MmInput
            v-model="form.newPassword"
            @update:model-value="
              errors.newPassword = '';
              formError = '';
            "
            label="新密码"
            type="password"
            placeholder="至少 8 位"
            autocomplete="new-password"
            :error="errors.newPassword"
          />
          <MmInput
            v-model="form.confirmPassword"
            @update:model-value="
              errors.confirmPassword = '';
              formError = '';
            "
            label="确认新密码"
            type="password"
            placeholder="再次输入新密码"
            autocomplete="new-password"
            :error="errors.confirmPassword"
          />
          <p v-if="formError" class="mm-auth__error" role="alert">
            {{ formError }}
          </p>
          <MmButton type="submit" :loading="submitting">重置密码</MmButton>
          <div class="mm-auth__links">
            <RouterLink to="/login">返回登录</RouterLink>
          </div>
        </fieldset>
      </form>
      <div
        v-else
        ref="doneElement"
        class="mm-auth__done"
        role="status"
        tabindex="-1"
      >
        <h3>密码设置成功</h3>
        <p>密码已重置，请使用新密码登录。原所有登录会话已失效。</p>
        <MmButton @click="router.push('/login')">去登录</MmButton>
      </div>
    </MmCard>
  </div>
</template>

<script setup lang="ts">
import AuthIntro from '../../shared/components/AuthIntro.vue';
import { nextTick, onUnmounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import MmButton from '../../shared/components/MmButton.vue';
import MmCard from '../../shared/components/MmCard.vue';
import MmInput from '../../shared/components/MmInput.vue';
import { post, type ApiError } from '../../shared/api';

const router = useRouter();

const form = reactive({
  email: '',
  code: '',
  newPassword: '',
  confirmPassword: '',
});
const errors = reactive({
  email: '',
  code: '',
  newPassword: '',
  confirmPassword: '',
});
const formError = ref('');
const codeHint = ref('');
const codeFailed = ref(false);
watch(
  () => form.email,
  () => {
    codeHint.value = '';
    form.code = '';
    errors.code = '';
  },
);
const submitting = ref(false);
const formElement = ref<HTMLFormElement | null>(null);
let disposed = false;
async function focusInvalidField() {
  await nextTick();
  formElement.value
    ?.querySelector<HTMLInputElement>('[aria-invalid="true"]')
    ?.focus();
}
const sendingCode = ref(false);
const done = ref(false);
const doneElement = ref<HTMLDivElement | null>(null);
const cooldown = ref(0);
let cooldownTimer: ReturnType<typeof setInterval> | null = null;

onUnmounted(() => {
  disposed = true;
  if (cooldownTimer) clearInterval(cooldownTimer);
});

function validEmail(): boolean {
  errors.email = form.email.trim() ? '' : '请输入注册邮箱';
  if (!errors.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) {
    errors.email = '邮箱格式不正确';
  }
  return !errors.email;
}

function startCooldown() {
  cooldown.value = 60;
  cooldownTimer = setInterval(() => {
    cooldown.value -= 1;
    if (cooldown.value <= 0 && cooldownTimer) {
      clearInterval(cooldownTimer);
      cooldownTimer = null;
    }
  }, 1000);
}

async function onSendCode() {
  if (sendingCode.value || submitting.value || cooldown.value > 0) return;
  codeFailed.value = false;
  codeHint.value = '';
  if (!validEmail()) {
    await focusInvalidField();
    return;
  }
  sendingCode.value = true;
  try {
    await post('/auth/password/code', { email: form.email.trim() });
    if (disposed) return;
    codeHint.value = '若该邮箱已注册，验证码已发送，10 分钟内有效';
    startCooldown();
  } catch (e) {
    if (disposed) return;
    codeFailed.value = true;
    codeHint.value = (e as ApiError).message || '验证码发送失败，请稍后重试';
  } finally {
    sendingCode.value = false;
  }
}

function validate(): boolean {
  const emailOk = validEmail();
  errors.code = form.code.trim() ? '' : '请输入邮箱验证码';
  errors.newPassword = form.newPassword.length >= 8 ? '' : '新密码至少 8 位';
  errors.confirmPassword =
    form.confirmPassword === form.newPassword ? '' : '两次输入的密码不一致';
  return (
    emailOk && !errors.code && !errors.newPassword && !errors.confirmPassword
  );
}

async function onSubmit() {
  if (submitting.value || sendingCode.value) return;
  formError.value = '';
  if (!validate()) {
    await focusInvalidField();
    return;
  }
  submitting.value = true;
  try {
    await post('/auth/password/reset', {
      email: form.email.trim(),
      code: form.code.trim(),
      newPassword: form.newPassword,
    });
    if (disposed) return;
    done.value = true;
    await nextTick();
    doneElement.value?.focus();
  } catch (e) {
    if (disposed) return;
    formError.value = (e as ApiError).message || '重置失败，请检查验证码后重试';
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

.mm-auth__code-row {
  display: flex;
  align-items: flex-end;
  gap: var(--mm-space-2);
}

.mm-auth__code-input {
  flex: 1;
  min-width: 0;
}

.mm-auth__code-row .mm-button {
  flex-shrink: 0;
  white-space: nowrap;
}

.mm-auth__hint {
  font-size: var(--mm-font-s);
  color: var(--mm-muted);
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

.mm-auth__done {
  display: flex;
  flex-direction: column;
  gap: var(--mm-space-4);
  align-items: flex-start;
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
