<script setup lang="ts">
import { ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { get, post, type ApiError } from '../../shared/api';
import { useAuthStore } from '../../shared/stores/auth';
import MmButton from '../../shared/components/MmButton.vue';
const props = defineProps<{ stage: string; resourceId?: number; draft?: string; title?: string }>();
interface Result { id: number; status: string; answer: string | null; failureCode: string | null; model: string; createdAt: string }
const auth = useAuthStore(), route = useRoute(), open = ref(false), busy = ref(false), error = ref(''), question = ref(''), result = ref<Result | null>(null), available = ref<boolean | null>(null);
let requestKey = '';
let generation = 0;
watch(() => [props.resourceId, props.draft, props.stage, question.value], () => { generation++; requestKey = ''; result.value = null; error.value = ''; busy.value = false; });
async function toggle() {
  open.value = !open.value;
  if (open.value && auth.me && available.value === null) {
    try { available.value = (await get<{ enabled: boolean }>('/ai/workflows/availability')).enabled; }
    catch (e) { error.value = (e as ApiError).message; }
  }
}
async function ask() {
  if (busy.value) return;
  const current = generation;
  busy.value = true; error.value = '';
  if (!requestKey || result.value?.status === 'FAILED' || result.value?.status === 'SUCCEEDED') requestKey = crypto.randomUUID();
  try {
    const value = await post<Result>('/ai/workflows', { stage: props.stage, resourceId: props.resourceId ?? null,
      question: [props.draft, question.value || '请根据当前信息给我具体的下一步建议。'].filter(Boolean).join('\n').slice(0, 1400), requestKey });
    if (current !== generation) return;
    result.value = value;
    if (result.value.status === 'FAILED') error.value = result.value.failureCode === 'AI_NOT_CONFIGURED' ? '麦仔暂未接通。你仍可正常操作，或联系人工客服。' : '本次分析未完成，没有改变任何业务状态。可以重试或联系人工客服。';
  } catch (e) { if (current === generation) error.value = (e as ApiError).message; }
  finally { if (current === generation) busy.value = false; }
}
</script>
<template>
  <section class="workflow-assistant" :aria-label="title || '麦仔流程助手'">
    <button type="button" class="workflow-assistant__toggle" :aria-expanded="open" @click="toggle">
      <span class="workflow-assistant__mark" aria-hidden="true">麦</span><span><strong>{{ title || '这一步，让麦仔帮你看看' }}</strong><small>根据当前商品或交易资料给出建议，由你决定下一步</small></span><span aria-hidden="true">{{ open ? '−' : '+' }}</span>
    </button>
    <div v-if="open" class="workflow-assistant__body">
      <RouterLink v-if="!auth.me" :to="{ path: '/login', query: { redirect: route.fullPath } }">登录后使用流程助手</RouterLink>
      <template v-else>
        <p class="mm-muted">点击分析会提交本环节必要的脱敏资料和补充说明。不要填写密码、电话或详细地址。AI 不会替你提交、付款或处理争议。</p>
        <p v-if="available === false" class="mm-notice">麦仔暂未接通，正常交易和人工客服仍可使用。</p>
        <label>补充说明（可选）<textarea v-model="question" :disabled="busy" maxlength="700" rows="2" placeholder="例如：这件商品还需要向卖家确认哪些细节？" /></label>
        <div class="mm-actions"><MmButton type="button" :loading="busy" :disabled="available === false" @click="ask">{{ result?.status === 'RUNNING' ? '获取分析结果' : '请麦仔分析' }}</MmButton><RouterLink :to="{ path: '/support', query: { title: title || '交易流程咨询', body: '我在页面 ' + route.fullPath + ' 需要人工协助。' } }">转人工客服</RouterLink></div>
        <p v-if="busy || result?.status === 'RUNNING'" role="status" class="workflow-assistant__thinking">麦仔正在核对这一步的资料…</p>
        <p v-if="error" class="mm-error" role="alert">{{ error }}</p>
        <div v-if="result?.status === 'SUCCEEDED'" class="workflow-assistant__answer" role="status"><p>{{ result.answer }}</p><small>AI 辅助建议 · 根据请求时的数据生成 · {{ result.model }}</small></div>
      </template>
    </div>
  </section>
</template>
<style scoped>
.workflow-assistant{margin:18px 0;border:1px solid #dddccf;border-radius:14px;background:#faf9f2;color:var(--mm-ink);overflow:hidden}.workflow-assistant__toggle{width:100%;display:flex;gap:12px;align-items:center;text-align:left;padding:16px;border:0;background:none;color:inherit;cursor:pointer}.workflow-assistant__toggle>span:nth-child(2){flex:1;min-width:0}.workflow-assistant__toggle strong,.workflow-assistant__toggle small{display:block}.workflow-assistant__toggle small{margin-top:4px;font-size:12px;color:var(--mm-muted);line-height:1.6}.workflow-assistant__mark{display:grid;place-items:center;flex-shrink:0;width:36px;height:36px;border-radius:50%;background:#eae4cf;color:#675a2b;font-weight:700}.workflow-assistant__body{padding:0 16px 16px;display:grid;gap:12px}.workflow-assistant__body label{display:grid;gap:7px}.workflow-assistant__body textarea{width:100%;box-sizing:border-box;border:1px solid #d9d8cb;border-radius:9px;padding:10px;font:inherit;background:white}.workflow-assistant__answer{padding:16px;background:white;border-radius:10px}.workflow-assistant__answer p{white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.9;margin:0 0 10px}.workflow-assistant__answer small{color:var(--mm-muted);font-size:11px}.workflow-assistant__thinking{color:#766324;animation:workflow-breathe 1.7s ease-in-out infinite}@keyframes workflow-breathe{50%{opacity:.45}}@media(prefers-reduced-motion:reduce){.workflow-assistant__thinking{animation:none}}@media(max-width:480px){.workflow-assistant__toggle{padding:13px}.workflow-assistant__toggle small{font-size:12px}.workflow-assistant__body textarea{font-size:16px}}
</style>
