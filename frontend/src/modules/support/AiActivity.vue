<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';

const props = withDefaults(defineProps<{
  active: boolean;
  outcome?: 'idle' | 'success' | 'error';
  title?: string;
}>(), { outcome: 'idle', title: '麦仔正在分析' });
const started = ref(0), elapsed = ref(0), finished = ref('');
let clock: ReturnType<typeof setInterval> | undefined;
const stopClock = () => { if (clock) clearInterval(clock); clock = undefined; };
watch(() => props.active, (active) => {
  stopClock();
  if (active) {
    started.value = Date.now(); elapsed.value = 0; finished.value = '';
    clock = setInterval(() => { elapsed.value = Math.floor((Date.now() - started.value) / 1000); }, 1000);
  } else if (started.value) {
    elapsed.value = Math.floor((Date.now() - started.value) / 1000);
    finished.value = new Date().toLocaleTimeString('zh-CN', { hour12: false });
  }
}, { immediate: true });
onBeforeUnmount(stopClock);
const beginTime = computed(() => new Date(started.value).toLocaleTimeString('zh-CN', { hour12: false }));
const label = computed(() => props.active ? props.title : props.outcome === 'success' ? '分析结果已收到' : props.outcome === 'error' ? '本次分析未完成' : '请求已结束');
</script>

<template>
  <div v-if="started" class="ai-activity" :class="{ 'is-running': active, 'is-error': outcome === 'error' && !active }" :aria-busy="active">
    <div class="ai-activity__head">
      <span class="ai-activity__orb" aria-hidden="true"><i></i><i></i><i></i></span>
      <div><strong role="status" aria-live="polite">{{ label }}</strong><span>{{ active ? '等待服务端返回，本次请求仍在进行' : '以下为实际请求状态' }}</span></div>
      <time aria-label="本次请求耗时">{{ elapsed }} 秒</time>
    </div>
    <div v-if="active" class="ai-activity__track" aria-hidden="true"><span></span></div>
    <ol class="ai-activity__log" aria-label="请求状态记录">
      <li><time>{{ beginTime }}</time><span>已发起分析请求</span><b aria-hidden="true">✓</b></li>
      <li v-if="active"><span class="ai-activity__dots" aria-hidden="true">•••</span><span>正在等待回复</span></li>
      <li v-else><time>{{ finished }}</time><span>{{ outcome === 'success' ? '已收到分析结果' : outcome === 'error' ? '未取得有效结果，可重试或转人工' : '当前请求已结束' }}</span></li>
    </ol>
    <p v-if="active && elapsed >= 20" class="ai-activity__slow" role="status">这次等待稍久，您可以继续查看页面，不必重复点击。超时后会提示重试。</p>
    <small>等待期间可以继续浏览，交易操作仍由您确认。</small>
  </div>
</template>

<style scoped>
.ai-activity{padding:17px;border:1px solid #d9dfd4;border-radius:12px;background:#f4f7f1;min-width:0;color:#344535}.ai-activity__head{display:flex;align-items:center;gap:12px}.ai-activity__head>div{flex:1;min-width:0}.ai-activity__head strong{display:block;font-size:14px}.ai-activity__head div span{display:block;font-size:12px;color:#667461;margin-top:5px;line-height:1.6}.ai-activity__head>time{font-size:13px;font-variant-numeric:tabular-nums;white-space:nowrap}.ai-activity__orb{display:flex;align-items:center;justify-content:center;gap:3px;width:38px;height:38px;border-radius:12px;background:#e4ecd9;flex-shrink:0}.ai-activity__orb i{height:8px;width:5px;border-radius:4px;background:#778c5d}.is-running .ai-activity__orb i{animation:ai-pulse 1.2s ease-in-out infinite}.ai-activity__orb i:nth-child(2){animation-delay:.18s}.ai-activity__orb i:nth-child(3){animation-delay:.36s}.ai-activity__track{height:3px;background:#dfe7d8;border-radius:5px;margin-top:15px;overflow:hidden}.ai-activity__track span{display:block;width:35%;height:100%;background:#819868;border-radius:5px;animation:ai-travel 2.2s ease-in-out infinite}.ai-activity__log{list-style:none;padding:0;margin:13px 0 10px;display:grid;gap:8px;font-size:12px}.ai-activity__log li{display:flex;gap:9px;align-items:center;line-height:1.7}.ai-activity__log time{color:#7a8575;flex-shrink:0;font-variant-numeric:tabular-nums}.ai-activity__log b{margin-left:auto;color:#66844b}.ai-activity__dots{width:53px;text-align:center;letter-spacing:3px;animation:ai-fade 1.6s ease-in-out infinite}.ai-activity small{font-size:11px;color:#7c8576;line-height:1.7}.ai-activity__slow{margin:10px 0;padding:10px;background:#fff9e9;border-radius:7px;font-size:12px;line-height:1.7;color:#806327}.is-error{background:#fff8f3;border-color:#e9d9c9}.is-error .ai-activity__orb{background:#f3e5d7}@keyframes ai-pulse{50%{height:20px}}@keyframes ai-travel{from{transform:translateX(-110%)}to{transform:translateX(390%)}}@keyframes ai-fade{50%{opacity:.35}}@media(prefers-reduced-motion:reduce){.ai-activity *{animation:none!important}}@media(max-width:480px){.ai-activity{padding:13px}.ai-activity__head{gap:9px}.ai-activity__orb{width:32px;height:32px}.ai-activity__head strong{font-size:13px}}
</style>
