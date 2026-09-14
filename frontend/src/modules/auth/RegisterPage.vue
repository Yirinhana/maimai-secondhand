<template>
  <div class="mm-auth">
    <MmCard title="注册麦麦二手" class="mm-auth__card">
      <form class="mm-auth__form" novalidate @submit.prevent="onSubmit">
        <MmInput
          v-model="form.email"
          label="邮箱"
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
          v-model="form.nickname"
          label="昵称"
          placeholder="2~20 个字符"
          maxlength="20"
          :error="errors.nickname"
        />
        <MmInput
          v-model="form.password"
          label="密码"
          type="password"
          placeholder="至少 8 位，建议包含字母和数字"
          autocomplete="new-password"
          :error="errors.password"
        />
        <MmInput
          v-model="form.confirmPassword"
          label="确认密码"
          type="password"
          placeholder="再次输入密码"
          autocomplete="new-password"
          :error="errors.confirmPassword"
        />
        <label class="mm-auth__terms"><input v-model="acceptedTerms" type="checkbox" />
          <span>我已阅读并同意 <RouterLink to="/policies" target="_blank" rel="noopener">用户协议与交易售后规则</RouterLink>（本地演示草案）</span>
        </label>
        <p v-if="policyError" class="mm-auth__error" role="alert">{{policyError}} <button type="button" @click="loadPolicy">重新加载条款版本</button></p>
        <p v-if="formError" class="mm-auth__error" role="alert">{{ formError }}</p>
        <MmButton type="submit" :disabled="!policyVersion" :loading="submitting">注册并登录</MmButton>
        <div class="mm-auth__links">
          <RouterLink to="/login">已有账号？去登录</RouterLink>
        </div>
      </form>
    </MmCard>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import MmButton from '../../shared/components/MmButton.vue'
import MmCard from '../../shared/components/MmCard.vue'
import MmInput from '../../shared/components/MmInput.vue'
import { get, post, type ApiError } from '../../shared/api'
import { useAuthStore } from '../../shared/stores/auth'

const router = useRouter()
const auth = useAuthStore()

const form = reactive({ email: '', code: '', nickname: '', password: '', confirmPassword: '' })
const errors = reactive({ email: '', code: '', nickname: '', password: '', confirmPassword: '' })
const formError = ref('')
const codeHint = ref('')
const submitting = ref(false)
const sendingCode = ref(false)
const cooldown = ref(0)
const acceptedTerms = ref(false), policyVersion = ref(''), policyError = ref('')
async function loadPolicy() {
  policyError.value = ''
  try { policyVersion.value = (await get<{version:string}>('/policies/current')).version }
  catch { policyError.value = '暂时无法读取条款版本，请稍后重试'; policyVersion.value = '' }
}
onMounted(loadPolicy)
let cooldownTimer: ReturnType<typeof setInterval> | null = null

onUnmounted(() => {
  if (cooldownTimer) clearInterval(cooldownTimer)
})

function validEmail(): boolean {
  errors.email = form.email.trim() ? '' : '请输入邮箱'
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
    await post('/auth/register/code', { email: form.email.trim() })
    codeHint.value = '验证码已发送，10 分钟内有效，请查收邮箱'
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
  errors.nickname =
    form.nickname.trim().length >= 2 ? '' : '昵称至少 2 个字符'
  errors.password = form.password.length >= 8 ? '' : '密码至少 8 位'
  errors.confirmPassword =
    form.confirmPassword === form.password ? '' : '两次输入的密码不一致'
  return (
    emailOk &&
    !errors.code &&
    !errors.nickname &&
    !errors.password &&
    !errors.confirmPassword
  )
}

async function onSubmit() {
  formError.value = ''
  if (!validate()) return
  if (!acceptedTerms.value || !policyVersion.value) { formError.value = '请阅读并勾选同意条款后注册'; return }
  submitting.value = true
  try {
    await auth.register({
      email: form.email.trim(),
      code: form.code.trim(),
      password: form.password,
      nickname: form.nickname.trim(),
      acceptedTerms: acceptedTerms.value,
      policyVersion: policyVersion.value,
    })
    router.push('/')
  } catch (e) {
    formError.value = (e as ApiError).message || '注册失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.mm-auth__terms {display:flex;align-items:flex-start;gap:8px;font-size:14px;line-height:1.7}
.mm-auth__terms input {flex:none;margin-top:5px}
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
</style>
