<template>
  <div class="mm-auth">
    <MmCard title="找回密码" class="mm-auth__card">
      <form v-if="!done" class="mm-auth__form" novalidate @submit.prevent="onSubmit">
        <MmInput
          v-model="form.email"
          label="注册邮箱"
          type="email"
          placeholder="you@example.com"
          autocomplete="email"
          :error="errors.email"
        />
        <div class="mm-auth__code-row">
          <MmInput
            v-model="form.code"
            label="邮箱验证码"
            placeholder="6 位验证码"
            maxlength="6"
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
        <p v-if="codeHint" class="mm-auth__hint">{{ codeHint }}</p>
        <MmInput
          v-model="form.newPassword"
          label="新密码"
          type="password"
          placeholder="至少 8 位"
          autocomplete="new-password"
          :error="errors.newPassword"
        />
        <MmInput
          v-model="form.confirmPassword"
          label="确认新密码"
          type="password"
          placeholder="再次输入新密码"
          autocomplete="new-password"
          :error="errors.confirmPassword"
        />
        <p v-if="formError" class="mm-auth__error" role="alert">{{ formError }}</p>
        <MmButton type="submit" :loading="submitting">重置密码</MmButton>
        <div class="mm-auth__links">
          <RouterLink to="/login">返回登录</RouterLink>
        </div>
      </form>
      <div v-else class="mm-auth__done">
        <p>密码已重置，请使用新密码登录。原所有登录会话已失效。</p>
        <MmButton @click="router.push('/login')">去登录</MmButton>
      </div>
    </MmCard>
  </div>
</template>

<script setup lang="ts">
import { onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import MmButton from '../../shared/components/MmButton.vue'
import MmCard from '../../shared/components/MmCard.vue'
import MmInput from '../../shared/components/MmInput.vue'
import { post, type ApiError } from '../../shared/api'

const router = useRouter()

const form = reactive({ email: '', code: '', newPassword: '', confirmPassword: '' })
const errors = reactive({ email: '', code: '', newPassword: '', confirmPassword: '' })
const formError = ref('')
const codeHint = ref('')
const submitting = ref(false)
const sendingCode = ref(false)
const done = ref(false)
const cooldown = ref(0)
let cooldownTimer: ReturnType<typeof setInterval> | null = null

onUnmounted(() => {
  if (cooldownTimer) clearInterval(cooldownTimer)
})

function validEmail(): boolean {
  errors.email = form.email.trim() ? '' : '请输入注册邮箱'
  if (!errors.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) {
    errors.email = '邮箱格式不正确'
  }
  return !errors.email
}

function startCooldown() {
  cooldown.value = 60
  cooldownTimer = setInterval(() => {
    cooldown.value -= 1
    if (cooldown.value <= 0 && cooldownTimer) {
      clearInterval(cooldownTimer)
      cooldownTimer = null
    }
  }, 1000)
}

async function onSendCode() {
  codeHint.value = ''
  if (!validEmail()) return
  sendingCode.value = true
  try {
    await post('/auth/password/code', { email: form.email.trim() })
    codeHint.value = '若该邮箱已注册，验证码已发送，10 分钟内有效'
    startCooldown()
  } catch (e) {
    codeHint.value = (e as ApiError).message || '验证码发送失败，请稍后重试'
  } finally {
    sendingCode.value = false
  }
}

function validate(): boolean {
  const emailOk = validEmail()
  errors.code = form.code.trim() ? '' : '请输入邮箱验证码'
  errors.newPassword = form.newPassword.length >= 8 ? '' : '新密码至少 8 位'
  errors.confirmPassword =
    form.confirmPassword === form.newPassword ? '' : '两次输入的密码不一致'
  return emailOk && !errors.code && !errors.newPassword && !errors.confirmPassword
}

async function onSubmit() {
  formError.value = ''
  if (!validate()) return
  submitting.value = true
  try {
    await post('/auth/password/reset', {
      email: form.email.trim(),
      code: form.code.trim(),
      newPassword: form.newPassword,
    })
    done.value = true
  } catch (e) {
    formError.value = (e as ApiError).message || '重置失败，请检查验证码后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.mm-auth {
  display: flex;
  justify-content: center;
  padding: var(--mm-space-6) var(--mm-space-4);
}

.mm-auth__card {
  width: 100%;
  max-width: 400px;
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
</style>
