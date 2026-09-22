<script setup lang="ts">
import { ref, watch } from 'vue';
import { get, type ApiError } from '../../shared/api';
interface Scope { source: string; label: string; completedTrades: number; ratings: number; averageRating: number | null; positivePercent: number | null }
interface Reputation { userId: number; level: string; scopes: Scope[]; confirmedContentActions: number; rules: string; calculatedAt: string }
const props = defineProps<{ userId: number }>(), profile = ref<Reputation | null>(null), error = ref('');
let generation = 0;
watch(() => props.userId, async id => { const current = ++generation; profile.value = null; error.value = ''; try { const value = await get<Reputation>(`/community/users/${id}/reputation`); if (current === generation) profile.value = value; } catch(e) { if (current === generation) error.value = (e as ApiError).message; } }, { immediate: true });
</script>
<template>
  <section class="reputation-card" aria-label="站内交易信誉">
    <header><div><p>以交易记录为依据</p><h2>站内交易信誉</h2></div><strong v-if="profile">{{ profile.level }}</strong></header>
    <p v-if="error" class="mm-error" role="alert">信誉资料暂不可用：{{ error }}</p><p v-else-if="!profile" role="status">正在读取交易与评价记录…</p>
    <template v-if="profile"><div class="reputation-card__scopes"><article v-for="scope in profile.scopes" :key="scope.source"><h3>{{ scope.label }}</h3><div><strong>{{ scope.averageRating == null ? '—' : scope.averageRating.toFixed(2) }}<small v-if="scope.averageRating != null"> / 5</small></strong><span>{{ scope.ratings }} 条有效评价</span></div><p>完成交易 {{ scope.completedTrades }} 笔 · 好评率 {{ scope.positivePercent == null ? '暂无' : scope.positivePercent + '%' }}</p></article></div><details><summary>信誉依据与申诉方式</summary><p>{{ profile.rules }}</p><p>人工确认的内容处理：{{ profile.confirmedContentActions }} 项。没有评价时不预设满分，AI 分析和未核实举报不直接计入信誉。</p><RouterLink to="/support">对记录有异议，联系人工核查 →</RouterLink></details></template>
  </section>
</template>
<style scoped>
.reputation-card{margin:22px 0;padding:22px;border:1px solid var(--mm-border,#dedfd8);border-radius:14px;background:#fff}.reputation-card header{display:flex;justify-content:space-between;gap:16px;align-items:center;flex-wrap:wrap}.reputation-card header p{font-size:12px;color:var(--mm-muted);margin:0 0 5px}.reputation-card h2{font-size:19px;margin:0}.reputation-card header>strong{font-size:13px;color:#476956;background:#eef4ee;padding:8px 12px;border-radius:8px}.reputation-card__scopes{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px;margin:20px 0}.reputation-card__scopes article{background:#f7f8f4;border-radius:10px;padding:16px;min-width:0}.reputation-card h3{font-size:13px;margin:0 0 13px}.reputation-card__scopes article>div{display:flex;align-items:baseline;gap:10px;flex-wrap:wrap}.reputation-card__scopes strong{font-size:25px;font-variant-numeric:tabular-nums}.reputation-card small{font-size:12px;font-weight:400}.reputation-card__scopes span,.reputation-card__scopes p{font-size:12px;color:var(--mm-muted)}.reputation-card details{font-size:13px;line-height:1.8}.reputation-card summary{cursor:pointer}@media(max-width:640px){.reputation-card{padding:16px}.reputation-card__scopes{grid-template-columns:1fr}.reputation-card__scopes article{padding:13px}.reputation-card__scopes p{margin-bottom:0}}
</style>
