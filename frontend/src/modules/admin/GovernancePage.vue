<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { get, post, type ApiError } from '../../shared/api';
import { useAuthStore } from '../../shared/stores/auth';
import { formatTime } from '../../shared/format';
import { askConfirmation } from '../../shared/confirm';
import MmButton from '../../shared/components/MmButton.vue';
import MmPagination from '../../shared/components/MmPagination.vue';

interface Overview { configured: boolean; paused: boolean; pauseReason: string | null; model: string; dailyLimit: number; callsToday: number; lanes: string[]; jobs: Record<string, number>; cases: Record<string, number>; generatedAt: string }
interface CaseRow { id: number; lane: string; category: string; priority: string; status: string; assignedTo: number | null; assignedName: string | null; title: string; evidenceCount: number; summary: string; updatedAt: string }
interface Assessment { category: string; priority: string; summary: string; evidence: string; missing: string; suggestion: string }
interface Job { id: number; sourceType: string; sourceId: number; title: string; sourceUrl: string; actorName: string | null; subjectName: string | null; sourceText: string; facts: string; status: string; attempts: number; failureCode: string | null; model: string | null; assessment: Assessment | null; sourceChanged: boolean; createdAt: string; startedAt: string | null; finishedAt: string | null }
interface Detail { item: CaseRow; evidence: Job[]; actions: { actor: string; action: string; note: string; createdAt: string }[] }
interface Page<T> { items: T[]; total: number; page: number; totalPages: number }
const auth = useAuthStore(), route = useRoute(), router = useRouter();
const overview = ref<Overview | null>(null), items = ref<CaseRow[]>([]), jobs = ref<Job[]>([]), detail = ref<Detail | null>(null);
const tab = ref('cases'), state = ref('OPEN'), jobState = ref('QUEUED'), lane = ref(''), page = ref(0), pages = ref(0), total = ref(0);
const loading = ref(false), acting = ref(false), error = ref(''), notice = ref(''), note = ref(''), action = ref('CLAIM');
let generation = 0, detailGeneration = 0, timer: ReturnType<typeof setInterval> | undefined;
const superAdmin = computed(() => auth.me?.roles.includes('SUPER_ADMIN'));
const category: Record<string, string> = { NONE: '未发现线索', COMPLAINT: '投诉诉求', TRADE_DISPUTE: '交易纠纷', OFF_PLATFORM: '站外交易线索', ABUSE: '不当言论线索', PRIVACY: '隐私暴露线索', PROHIBITED: '禁售线索', NEEDS_REVIEW: '需人工核查' };
const priority: Record<string, string> = { HIGH: '优先核查', NORMAL: '常规跟进', LOW: '低优先级' };
const states: Record<string, string> = { OPEN: '待跟进', IN_PROGRESS: '跟进中', RESOLVED: '已完成跟进', DISMISSED: '不需跟进', QUEUED: '等待预审', ANALYSING: '模型分析中', COMPLETED: '分析完成', FAILED: '待人工核查', SUPERSEDED: '内容已变化', FILTERED: '本地筛选 · 未调用模型' };
const source: Record<string, string> = { PRODUCT: '商品资料', PRODUCT_COMMENT: '商品讨论', ORDER_REVIEW: '评价 / 追评', DEMAND_POST: '求购内容', DEMAND_REPLY: '求购回复', REPORT: '用户主动举报', AFTERSALE: '售后申请', SUPPORT_TICKET: '客服工单' };
const laneName = (value: string) => value === 'PUBLIC' ? '内容与社区' : '交易与客服';
const serviceState = computed(() => !overview.value?.configured ? '尚未配置模型' : overview.value.paused ? '已暂停预审' : overview.value.callsToday >= overview.value.dailyLimit ? '今日调用已达上限' : '后台预审已就绪');

async function load() {
  const current = ++generation; loading.value = true; error.value = '';
  try {
    const [summary, result] = await Promise.all([
      get<Overview>('/admin/governance'),
      tab.value === 'cases'
        ? get<Page<CaseRow>>('/admin/governance/cases', { status: state.value, lane: lane.value, page: page.value })
        : get<Page<Job>>('/admin/governance/jobs', { status: jobState.value, page: page.value }),
    ]);
    if (current !== generation) return;
    overview.value = summary; pages.value = result.totalPages; total.value = result.total;
    if (tab.value === 'cases') items.value = result.items as CaseRow[]; else jobs.value = result.items as Job[];
  } catch (e) { if (current === generation) error.value = (e as ApiError).message; }
  finally { if (current === generation) loading.value = false; }
}
async function openCase(id: number) {
  if (acting.value) return;
  const current = ++detailGeneration; detail.value = null; note.value = ''; error.value = '';
  try {
    const value = await get<Detail>(`/admin/governance/cases/${id}`);
    if (current !== detailGeneration) return;
    detail.value = value; action.value = ['RESOLVED', 'DISMISSED'].includes(value.item.status) ? 'REOPEN' : 'CLAIM';
    void router.replace({ query: { ...route.query, caseId: id } });
  } catch (e) { if (current === detailGeneration) error.value = (e as ApiError).message; }
}
function closeCase() { if (acting.value) return; detailGeneration++; detail.value = null; const query = { ...route.query }; delete query.caseId; void router.replace({ query }); }
async function decide() {
  if (!detail.value || acting.value) return;
  const id = detail.value.item.id; acting.value = true; error.value = '';
  try {
    detail.value = await post<Detail>(`/admin/governance/cases/${id}/actions`, { action: action.value, note: note.value });
    note.value = ''; notice.value = '跟进记录已保存。商品、举报、售后等原业务状态保持由对应流程管理。';
    action.value = ['RESOLVED', 'DISMISSED'].includes(detail.value.item.status) ? 'REOPEN' : 'RESOLVE';
    await load();
  } catch (e) { error.value = (e as ApiError).message; }
  finally { acting.value = false; }
}
async function togglePause() {
  if (!overview.value || acting.value) return;
  acting.value = true;
  try { await post('/admin/governance/pause', { paused: !overview.value.paused }); await load(); }
  catch (e) { error.value = (e as ApiError).message; } finally { acting.value = false; }
}
async function scan() {
  if (!await askConfirmation('补查各类最近20条已有内容？仅收集必要的脱敏资料，相同内容会去重；模型接通后会使用额度，仍受每日上限控制。')) return;
  acting.value = true;
  try { const value = await post<{ queued: number }>('/admin/governance/scan-recent'); notice.value = `已登记 ${value.queued} 条记录等待去重与预审，稍后刷新可查看任务。`; await load(); }
  catch (e) { error.value = (e as ApiError).message; } finally { acting.value = false; }
}
async function retry(id: number) {
  acting.value = true;
  try { await post(`/admin/governance/jobs/${id}/retry`); notice.value = '已重新排队，达到上限或暂停时不会发出模型请求。'; await load(); }
  catch (e) { error.value = (e as ApiError).message; } finally { acting.value = false; }
}
watch([tab, state, jobState, lane], () => { page.value = 0; void load(); });
watch(page, () => { void load(); });
onMounted(() => {
  void load();
  const id = Number(route.query.caseId); if (Number.isSafeInteger(id) && id > 0) void openCase(id);
  timer = setInterval(() => { if (!document.hidden && !loading.value && !acting.value) void load(); }, 10000);
});
onBeforeUnmount(() => { if (timer) clearInterval(timer); generation++; detailGeneration++; });
</script>

<template>
  <section class="governance">
    <header class="governance__heading"><div><p class="mm-eyebrow">平台治理</p><h1>麦仔预审工作台</h1><p>先整理线索，再核对事实。把时间留给真正需要处理的问题。</p></div><MmButton variant="ghost" :loading="loading" @click="load">刷新</MmButton></header>
    <p v-if="error" class="mm-error" role="alert">{{ error }}</p><p v-if="notice" class="mm-notice" role="status">{{ notice }}</p>
    <template v-if="overview">
      <section class="governance__service" aria-label="预审运行状态">
        <div><span class="governance__light" :class="{ 'is-ready': overview.configured && !overview.paused && overview.callsToday < overview.dailyLimit }"></span><strong>{{ serviceState }}</strong><p>{{ overview.configured ? '先用本地规则筛选，疑似风险再请求模型；分析失败后由人工决定是否重试。' : '内容会保留在队列；配置好服务端专用模型后才会发送分析请求。' }}</p></div>
        <div class="governance__quota"><strong>{{ overview.callsToday }} / {{ overview.dailyLimit }}</strong><span>今日模型请求 · UTC 零点重置</span><small>失败请求也计入；不是金额或剩余额度估算</small></div>
        <div v-if="superAdmin" class="governance__controls"><MmButton variant="ghost" :disabled="acting" @click="togglePause">{{ overview.paused ? '恢复预审' : '暂停预审' }}</MmButton><button :disabled="acting" class="governance__text-button" @click="scan">补查近期内容</button></div>
      </section>
      <p v-if="overview.pauseReason" class="mm-notice" role="status">{{ overview.pauseReason }}。核对模型配置后，可由超级管理员恢复。</p>
      <div class="governance__metrics"><article><span>需要跟进</span><strong>{{ (overview.cases.OPEN || 0) + (overview.cases.IN_PROGRESS || 0) }}</strong><small>按同一对象和线索类型汇总</small></article><article><span>优先核查</span><strong class="governance__urgent">{{ overview.cases.HIGH || 0 }}</strong><small>AI 标记的线索，仍需核实</small></article><article><span>排队 / 分析中</span><strong>{{ overview.jobs.QUEUED || 0 }} <i>/ {{ overview.jobs.ANALYSING || 0 }}</i></strong><small>每次仅处理一个模型请求</small></article><article><span>分析失败</span><strong>{{ overview.jobs.FAILED || 0 }}</strong><small>记录仍在，可人工处理或重试</small></article></div>
      <nav class="governance__tabs" aria-label="预审列表"><button :aria-pressed="tab === 'cases'" @click="tab = 'cases'">待办线索</button><button :aria-pressed="tab === 'jobs'" @click="tab = 'jobs'">处理记录</button><span>每 10 秒刷新 · {{ formatTime(overview.generatedAt) }}</span></nav>
      <div class="governance__filters"><template v-if="tab === 'cases'"><label>跟进状态<select v-model="state"><option value="OPEN">待跟进</option><option value="IN_PROGRESS">跟进中</option><option value="RESOLVED">已完成跟进</option><option value="DISMISSED">不需跟进</option><option value="ALL">全部</option></select></label><label>业务范围<select v-model="lane"><option value="">我有权查看的范围</option><option v-for="value in overview.lanes" :key="value" :value="value">{{ laneName(value) }}</option></select></label></template><label v-else>任务状态<select v-model="jobState"><option v-for="value in ['QUEUED', 'ANALYSING', 'COMPLETED', 'FAILED', 'SUPERSEDED', 'FILTERED']" :key="value" :value="value">{{ states[value] }}</option></select></label><span>共 {{ total }} 条</span></div>
      <div class="governance__workspace" :class="{ 'has-detail': detail }">
        <div class="governance__list">
          <template v-if="tab === 'cases'"><button v-for="item in items" :key="item.id" class="governance__case" :class="{ 'is-selected': detail?.item.id === item.id }" @click="openCase(item.id)"><div><span class="governance__badge" :class="{ 'is-high': item.priority === 'HIGH' }">{{ priority[item.priority] }}</span><span>{{ category[item.category] }}</span><small>#{{ item.id }}</small></div><h2>{{ item.title }}</h2><p>{{ item.summary }}</p><footer><span>{{ item.evidenceCount }} 条关联依据 · {{ laneName(item.lane) }}</span><span>{{ item.assignedName || '待认领' }} →</span></footer></button></template>
          <template v-else><article v-for="job in jobs" :key="job.id" class="governance__job"><header><span class="governance__badge">{{ states[job.status] }}</span><small>{{ source[job.sourceType] }} #{{ job.sourceId }}</small></header><h2>{{ job.title }}</h2><p>{{ job.assessment?.summary || (job.status === 'QUEUED' ? '已收集待审文本，正在等待可用的模型处理时段。' : job.status === 'ANALYSING' ? '已开始调用模型，结果返回后会校验并整理。' : job.status === 'FILTERED' ? '本地规则未命中需要调用模型的线索，不消耗模型额度；这不代表内容已通过审核。' : job.status === 'SUPERSEDED' ? '原内容已经改变或不再适用，此条结果不会作为当前结论。' : '未取得可靠结论，原业务可以继续由人工处理。') }}</p><footer><small>{{ formatTime(job.createdAt) }} · 已请求 {{ job.attempts }} 次</small><MmButton v-if="job.status === 'FAILED'" variant="ghost" :disabled="acting" @click="retry(job.id)">重新排队</MmButton></footer></article></template>
          <div v-if="!loading && (tab === 'cases' ? !items.length : !jobs.length)" class="governance__empty"><strong>当前筛选下暂无记录</strong><p>新发布或更新的内容会自动进入队列。历史内容可由超级管理员发起补查。</p><small>没有 AI 线索不代表没有风险，原有审核和举报流程仍正常工作。</small></div>
          <MmPagination :page="page" :total-pages="pages" @change="page = $event" />
        </div>
        <section v-if="detail" class="governance__detail" aria-label="预审核查材料">
          <header><div><small>线索 #{{ detail.item.id }} · {{ states[detail.item.status] }}</small><h2>核查材料</h2></div><button class="governance__text-button" @click="closeCase">关闭材料</button></header>
          <p class="governance__notice">AI 提供的是待核实线索，不是已确认违规。自动发现的线索不会冒充用户举报，也不会自动扣信誉、退款或隐藏内容。</p>
          <a class="governance__download" :href="`/api/v1/admin/governance/cases/${detail.item.id}/export`">下载核查材料（TXT） ↓</a>
          <p v-if="detail.item.evidenceCount > 50" class="mm-muted">当前展示最近 50 条依据，共 {{ detail.item.evidenceCount }} 条。</p>
          <article v-for="job in detail.evidence" :key="job.id" class="governance__evidence"><div><span class="governance__badge">{{ source[job.sourceType] }}</span><small>{{ states[job.status] }}</small></div><h3>{{ job.title }}</h3><p class="governance__people">内容提交者：{{ job.actorName || '账号已不可用' }}<br />关联对象：{{ job.subjectName || '未指定' }}</p><p v-if="job.sourceChanged" class="mm-notice">原文已经变化或当前不可用，请重新核对后再作决定。</p><details open><summary>脱敏原文快照</summary><blockquote>{{ job.sourceText }}</blockquote><p class="mm-muted">{{ job.facts }}</p></details><template v-if="job.assessment"><h4>待核实摘要</h4><p>{{ job.assessment.summary }}</p><h4 v-if="job.assessment.evidence">对应原文</h4><p v-if="job.assessment.evidence" class="governance__quote">{{ job.assessment.evidence }}</p><h4>还需核对</h4><p>{{ job.assessment.missing || '请结合原业务记录复核。' }}</p><h4>建议下一步</h4><p>{{ job.assessment.suggestion }}</p></template><p v-else class="mm-notice">没有有效的模型结论，请按原文人工核查。</p><nav><RouterLink :to="job.sourceUrl">查看来源 →</RouterLink><RouterLink v-if="['PRODUCT', 'DEMAND_POST', 'DEMAND_REPLY'].includes(job.sourceType)" :to="job.sourceType === 'PRODUCT' ? '/admin/products' : '/admin/community'">进入审核流程 →</RouterLink></nav><small>{{ formatTime(job.createdAt) }} · {{ job.model || '尚未调用模型' }}</small></article>
          <form class="governance__decision" @submit.prevent="decide"><h3>记录人工跟进</h3><p>这里只管理核查待办。需要处理举报、商品、工单或售后时，请进入对应业务页面操作。</p><label>跟进动作<select v-model="action"><template v-if="['OPEN', 'IN_PROGRESS'].includes(detail.item.status)"><option value="CLAIM">由我跟进</option><option value="RESOLVE">已完成跟进</option><option value="DISMISS">误报 / 不需跟进</option></template><option v-else value="REOPEN">重新开启</option></select></label><label>核查说明<textarea v-model="note" required minlength="6" maxlength="1000" rows="4" placeholder="写明核对了哪些依据，以及处理结果。" /></label><MmButton type="submit" :loading="acting">保存跟进记录</MmButton></form>
          <details v-if="detail.actions.length" class="governance__history"><summary>人工跟进历史</summary><article v-for="(entry, index) in detail.actions" :key="index"><strong>{{ entry.actor }} · {{ ({ CLAIM: '认领', RESOLVE: '完成跟进', DISMISS: '无需跟进', REOPEN: '重新开启' } as Record<string, string>)[entry.action] || entry.action }}</strong><p>{{ entry.note }}</p><small>{{ formatTime(entry.createdAt) }}</small></article></details>
        </section>
      </div>
    </template>
  </section>
</template>

<style scoped>
.governance{display:grid;gap:20px;min-width:0;color:#2c3e4c}.governance__heading{display:flex;justify-content:space-between;gap:16px;align-items:center}.governance h1{font-size:28px;margin:5px 0 9px}.governance__heading p{color:#6c7b85;line-height:1.75;margin:0}.governance p{overflow-wrap:anywhere}.governance__service{display:flex;gap:22px;align-items:center;padding:22px;background:#edf3f5;border:1px solid #d9e3e8;border-radius:15px}.governance__service>div:first-child{flex:1}.governance__service p{margin:8px 0 0;font-size:13px;line-height:1.7;color:#637482}.governance__light{display:inline-block;width:8px;height:8px;background:#b98b52;border-radius:50%;margin-right:8px}.governance__light.is-ready{background:#4e8c72;box-shadow:0 0 0 4px #4e8c7218}.governance__quota{display:grid;gap:6px}.governance__quota strong{font-size:22px;font-variant-numeric:tabular-nums}.governance__quota span{font-size:12px}.governance small{color:#6c7b85;font-size:12px;line-height:1.6}.governance__controls{display:grid;gap:10px}.governance__text-button{background:none;border:0;color:#45667f;cursor:pointer;font:inherit;font-size:13px;min-height:36px}.governance__metrics{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px}.governance__metrics article{display:grid;gap:10px;background:white;border:1px solid #dfe5e9;border-radius:12px;padding:20px}.governance__metrics span{font-size:13px}.governance__metrics strong{font-size:30px;font-variant-numeric:tabular-nums}.governance__metrics i{font-size:18px;font-style:normal;color:#84929c}.governance__urgent{color:#b76c39}.governance__tabs{display:flex;align-items:center;gap:10px;border-bottom:1px solid #dfe5e9;padding-bottom:12px}.governance__tabs button{border:0;border-radius:8px;padding:11px 18px;background:transparent;color:#657984;cursor:pointer;font:inherit;font-size:14px}.governance__tabs button[aria-pressed=true]{background:#2d465a;color:white}.governance__tabs>span{margin-left:auto;font-size:11px;color:#82909a}.governance__filters{display:flex;gap:15px;align-items:end;flex-wrap:wrap}.governance__filters label,.governance__decision label{display:grid;gap:7px;font-size:12px}.governance select,.governance textarea{min-height:42px;border:1px solid #ccd7df;border-radius:8px;padding:10px 12px;background:white;color:inherit;font:inherit;font-size:14px;max-width:100%}.governance__filters>span{margin-left:auto;padding-bottom:12px;color:#7a8994;font-size:12px}.governance__workspace{display:grid;grid-template-columns:1fr;gap:20px;align-items:start;min-width:0}.governance__workspace.has-detail{grid-template-columns:minmax(240px,.8fr) minmax(360px,1.2fr)}.governance__list{display:grid;gap:12px;min-width:0}.governance__case,.governance__job{display:block;width:100%;padding:20px;border:1px solid #dbe3e9;border-radius:12px;background:white;text-align:left;color:inherit;min-width:0;font:inherit}.governance__case{cursor:pointer;transition:border-color .2s,box-shadow .2s}.governance__case:hover,.governance__case.is-selected{border-color:#7590a5;box-shadow:0 3px 15px #2845590a}.governance__case>div,.governance__job header,.governance__evidence>div{display:flex;align-items:center;gap:9px;font-size:12px;flex-wrap:wrap}.governance__case>div small{margin-left:auto}.governance__badge{display:inline-flex;border-radius:6px;background:#edf2f5;padding:5px 8px;font-size:11px;color:#5a7282}.governance__badge.is-high{background:#fff1e5;color:#ad5e2b}.governance h2{font-size:17px;margin:13px 0 10px;line-height:1.5;overflow-wrap:anywhere}.governance__case p,.governance__job p{font-size:13px;line-height:1.85;color:#647583;margin:0 0 16px}.governance footer{display:flex;gap:12px;align-items:center;justify-content:space-between;font-size:11px;color:#7b8b96;flex-wrap:wrap}.governance__empty{border:1px dashed #d3dfe7;border-radius:12px;padding:40px 24px;text-align:center;background:#f8fafb}.governance__empty p{font-size:13px;line-height:1.8}.governance__detail{min-width:0;background:white;border:1px solid #d5e0e7;border-radius:14px;padding:22px}.governance__detail>header{display:flex;align-items:center;justify-content:space-between;gap:12px}.governance__detail h2{margin:5px 0 0}.governance__notice{background:#f6f3eb;padding:12px 14px;border-radius:8px;color:#85714d;font-size:12px;line-height:1.8}.governance__download{display:block;padding:12px 0;color:#3f677f;font-size:13px}.governance__evidence{padding:21px 0;border-top:1px solid #e5ebef;min-width:0}.governance h3{font-size:15px;margin:12px 0}.governance h4{font-size:12px;color:#84919b;font-weight:500;margin:18px 0 5px}.governance__evidence p{font-size:13px;line-height:1.9;margin:7px 0;white-space:pre-wrap}.governance__people{color:#748590}.governance summary{cursor:pointer;font-size:13px;padding:8px 0}.governance blockquote{margin:8px 0;padding:13px;background:#f4f7f9;border-left:3px solid #9eafbb;white-space:pre-wrap;overflow-wrap:anywhere;font-size:13px;line-height:1.85}.governance__quote{color:#597688}.governance__evidence nav{display:flex;gap:15px;flex-wrap:wrap;margin:15px 0 8px;font-size:12px}.governance__evidence nav a{color:#446d87}.governance__decision{display:grid;gap:13px;border-top:1px solid #dfe7ed;padding-top:12px}.governance__decision p{font-size:12px;line-height:1.8;margin:0;color:#71828e}.governance textarea{width:100%;resize:vertical;box-sizing:border-box}.governance__history{margin-top:20px}.governance__history article{border-left:2px solid #d5dfe7;padding:12px;margin:8px 0;font-size:12px}.governance__history p{line-height:1.8;white-space:pre-wrap}@media(max-width:1100px){.governance__workspace.has-detail{grid-template-columns:1fr}.governance__detail{grid-row:1}.governance__service{flex-wrap:wrap}.governance__metrics{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:550px){.governance{gap:15px}.governance h1{font-size:23px}.governance__heading{align-items:start}.governance__heading p{font-size:12px}.governance__service{padding:16px;gap:16px}.governance__service>div:first-child{flex-basis:100%}.governance__controls{margin-left:auto}.governance__quota small{max-width:170px}.governance__metrics{gap:8px}.governance__metrics article{padding:14px;gap:7px}.governance__metrics strong{font-size:26px}.governance__tabs{flex-wrap:wrap}.governance__tabs>span{flex-basis:100%;margin-left:0}.governance__case,.governance__job,.governance__detail{padding:16px}.governance select,.governance textarea{font-size:16px;min-width:0}.governance__filters label{flex:1;min-width:130px}.governance__filters select{width:100%}.governance__metrics small{font-size:11px}}
</style>
