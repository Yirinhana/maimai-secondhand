<template>
  <div class="mm-aftersale-detail">
    <p v-if="error" class="mm-aftersale-detail__error" role="alert">{{ error }}</p>
    <p v-else-if="!detail" class="mm-aftersale-detail__hint">加载中…</p>
    <template v-else>
      <MmCard class="mm-aftersale-detail__card">
        <div class="mm-aftersale-detail__head">
          <div>
            <h1 class="mm-aftersale-detail__title">售后单 {{ detail.aftersaleNo }}</h1>
            <p class="mm-aftersale-detail__meta">
              {{ AFTERSALE_TYPE_TEXT[detail.type] }} · 关联订单
              <RouterLink v-if="isBuyer||isSeller||auth.isAdmin" :to="`/orders/${detail.orderNo}`">{{ detail.orderNo }}</RouterLink><span v-else>{{detail.orderNo}}</span>
            </p>
          </div>
          <MmTag :text="AFTERSALE_STATUS_TEXT[detail.status]" :tone="statusTone(detail.status)" />
        </div>

        <!-- 状态进度 -->
        <ol v-if="detail.status !== 'PENDING_MANUAL'" class="mm-aftersale-detail__steps" aria-label="售后进度">
          <li
            v-for="(step, index) in steps"
            :key="step"
            class="mm-aftersale-detail__step"
            :class="{
              'is-done': index < currentStep,
              'is-current': index === currentStep && !isTerminated,
            }"
          >
            <span class="mm-aftersale-detail__step-dot" aria-hidden="true"></span>
            <span class="mm-aftersale-detail__step-label">{{ step }}</span>
          </li>
        </ol>
        <p v-if="detail.status === 'SELLER_REJECTED'" class="mm-aftersale-detail__notice">
          卖家已拒绝本次申请，如有异议请联系平台客服介入。
        </p>
        <p v-else-if="detail.status === 'PENDING_MANUAL'" class="mm-aftersale-detail__notice">
          该售后单正在等待平台人工核实。已完成的寄回、签收等操作保留在下方处理记录中，请留意后续答复。
        </p>
        <p v-else-if="detail.status === 'CLOSED'" class="mm-aftersale-detail__notice">
          该售后单已关闭。
        </p>
        <div v-if="isBuyer&&['PENDING_RETURN','RETURN_SHIPPED','SELLER_REJECTED','CLOSED','RESOLVED'].includes(detail.status)" class="mm-aftersale-detail__notice">
          <p>退货地址有问题、退件未获处理或对结论有异议时，可申请人工核实；申诉不会自动再次退款。</p>
          <MmButton variant="ghost" :disabled="acting" @click="escalate">申请人工介入</MmButton>
        </div>
        <p v-if="actionError" class="mm-aftersale-detail__error" role="alert">{{actionError}}</p>
      </MmCard>

      <!-- 申请详情 -->
      <MmCard title="申请详情" class="mm-aftersale-detail__card">
        <dl class="mm-aftersale-detail__grid">
          <div>
            <dt>售后类型</dt>
            <dd>{{ AFTERSALE_TYPE_TEXT[detail.type] }}</dd>
          </div>
          <div>
            <dt>商品款</dt>
            <dd><PriceText :cents="detail.goodsAmountCents" /></dd>
          </div>
          <div>
            <dt>运费</dt>
            <dd><PriceText :cents="detail.freightAmountCents" /></dd>
          </div>
          <div>
            <dt>合计申请金额</dt>
            <dd>
              <PriceText :cents="detail.goodsAmountCents + detail.freightAmountCents" />
            </dd>
          </div>
          <div class="mm-aftersale-detail__grid-wide">
            <dt>申请原因</dt>
            <dd>{{ detail.reason }}</dd>
          </div>
          <div v-if="detail.evidence" class="mm-aftersale-detail__grid-wide">
            <dt>证据说明</dt>
            <dd class="mm-aftersale-detail__pre">{{ detail.evidence }}</dd>
          </div>
          <div v-if="detail.sellerReply" class="mm-aftersale-detail__grid-wide">
            <dt>卖家答复</dt>
            <dd>{{ detail.sellerReply }}</dd>
          </div>
          <div v-if="detail.returnCarrier">
            <dt>退回快递</dt>
            <dd>{{ detail.returnCarrier }} · {{ detail.returnTrackingNo }}</dd>
          </div>
          <div v-if="detail.returnAddress" class="mm-aftersale-detail__grid-wide">
            <dt>卖家提供的退货地址</dt>
            <dd>{{detail.returnRecipient}} · {{detail.returnPhone}}<br />{{detail.returnAddress}}</dd>
          </div>
          <div v-if="detail.returnShippedAt"><dt>买家寄回时间</dt><dd>{{formatTime(detail.returnShippedAt)}}</dd></div>
          <div v-if="detail.returnReceivedAt"><dt>卖家确认签收</dt><dd>{{formatTime(detail.returnReceivedAt)}}</dd></div>
          <div v-if="detail.returnInspectionDeadline"><dt>验退答复截止</dt><dd>{{formatTime(detail.returnInspectionDeadline)}}</dd></div>
          <div>
            <dt>申请时间</dt>
            <dd>{{ formatTime(detail.createdAt) }}</dd>
          </div>
          <div>
            <dt>最近更新</dt>
            <dd>{{ formatTime(detail.updatedAt) }}</dd>
          </div>
        </dl>
      </MmCard>

      <!-- 时限规则说明 -->
      <MmCard title="时限规则" class="mm-aftersale-detail__card">
        <ul class="mm-aftersale-detail__rules">
          <li>
            卖家需在 48 小时内答复售后申请
            <template v-if="detail.sellerDeadline">
              （本单截止：{{ formatTime(detail.sellerDeadline) }}）
            </template>
          </li>
          <li>
            卖家同意退货并提供有效地址后，买家需在 7 天内寄回商品并填写运单
            <template v-if="detail.returnDeadline">
              （本单截止：{{ formatTime(detail.returnDeadline) }}）
            </template>
          </li>
          <li>退款按商品款与运费分开计算，累计退款不超过实付金额。</li>
          <li>卖家主动确认退件实际签收后，进入 48 小时验退答复窗口。真实物流签收自动核对待渠道接入，买家始终保留人工介入入口；流程时限不代替法定权利。</li>
        </ul>
      </MmCard>

      <EvidenceImages :key="detail.id" :aftersale-id="detail.id" :can-upload="(isBuyer||isSeller)&&!['RESOLVED','CLOSED'].includes(detail.status)" />
      <!-- 卖家答复（卖家视角，待处理） -->
      <MmCard v-if="isSeller && detail.status === 'PENDING_SELLER'" title="答复售后申请" class="mm-aftersale-detail__card">
        <form class="mm-aftersale-detail__form" @submit.prevent="onRespond">
          <div class="mm-aftersale-detail__radios" role="radiogroup" aria-label="答复结果">
            <label>
              <input v-model="respondForm.agree" type="radio" :value="true" />
              同意申请（{{ detail.type === 'REFUND_ONLY' ? '直接退款' : '进入退货流程' }}）
            </label>
            <label>
              <input v-model="respondForm.agree" type="radio" :value="false" />
              拒绝申请
            </label>
          </div>
          <label class="mm-aftersale-detail__field">
            <span>{{ respondForm.agree ? '答复说明（可选）' : '拒绝理由（必填）' }}</span>
            <textarea
              v-model="respondForm.reply"
              rows="3"
              maxlength="500"
              placeholder="给买家的答复说明"
            ></textarea>
          </label>
          <div v-if="respondForm.agree&&detail.type==='RETURN_REFUND'" class="mm-form">
            <p class="mm-muted">请提供可实际接收退件的地址，同意后将向买家展示并开始 7 天寄回时限。</p>
            <label>退货收件人<input v-model="respondForm.returnRecipient" required maxlength="50" autocomplete="name" /></label>
            <label>退货联系电话<input v-model="respondForm.returnPhone" required maxlength="30" type="tel" /></label>
            <label>完整退货地址<textarea v-model="respondForm.returnAddress" required maxlength="500" placeholder="省市区、街道及门牌号" /></label>
          </div>
          <p v-if="actionError" class="mm-aftersale-detail__error" role="alert">{{ actionError }}</p>
          <MmButton type="submit" :loading="acting">提交答复</MmButton>
        </form>
      </MmCard>

      <!-- 买家寄回（买家视角，待寄回） -->
      <MmCard v-if="isBuyer && detail.status === 'PENDING_RETURN'" title="填写退货物流" class="mm-aftersale-detail__card">
        <p v-if="!detail.returnAddress" class="mm-notice">卖家尚未提供完整退货地址，请先通过站内沟通或人工介入核实，不要自行猜测寄回地址。</p>
        <form v-else class="mm-aftersale-detail__form" @submit.prevent="onReturnShip">
          <MmInput
            v-model="shipForm.carrier"
            label="快递公司"
            placeholder="如：顺丰速运"
            maxlength="50"
          />
          <MmInput
            v-model="shipForm.trackingNo"
            label="运单号"
            placeholder="请输入退货运单号"
            maxlength="64"
          />
          <p v-if="actionError" class="mm-aftersale-detail__error" role="alert">{{ actionError }}</p>
          <MmButton type="submit" :loading="acting">确认寄出</MmButton>
        </form>
      </MmCard>

      <!-- 卖家验退确认（卖家视角，退货已发出） -->
      <MmCard v-if="isSeller && detail.status === 'RETURN_SHIPPED'" title="验退确认" class="mm-aftersale-detail__card">
        <p class="mm-aftersale-detail__meta">
          买家已通过 {{ detail.returnCarrier }}（{{ detail.returnTrackingNo }}）寄回商品。
          可先确认实物已签收，再于 48 小时内验退答复；验收无误也可直接确认收货并退款。
        </p>
        <p v-if="actionError" class="mm-aftersale-detail__error" role="alert">{{ actionError }}</p>
        <MmButton v-if="!detail.returnReceivedAt" variant="ghost" :disabled="acting" @click="onReceiveReturn">确认退件已实际签收</MmButton>
        <MmButton :loading="acting" @click="onConfirmReturn">确认收货并退款</MmButton>
      </MmCard>

      <!-- 操作日志 -->
      <MmCard title="处理记录" class="mm-aftersale-detail__card">
        <ol v-if="detail.logs.length > 0" class="mm-aftersale-detail__timeline">
          <li v-for="log in detail.logs" :key="log.id" class="mm-aftersale-detail__log">
            <span class="mm-aftersale-detail__log-dot" aria-hidden="true"></span>
            <div>
              <p class="mm-aftersale-detail__log-action">
                <MmTag :text="roleText(log.actorRole)" tone="primary" />
                {{ actionText(log.action) }}
              </p>
              <p v-if="log.note" class="mm-aftersale-detail__log-note">{{ log.note }}</p>
              <p class="mm-aftersale-detail__log-time">{{ formatTime(log.createdAt) }}</p>
            </div>
          </li>
        </ol>
        <p v-else class="mm-aftersale-detail__meta">暂无处理记录</p>
      </MmCard>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import {askConfirmation} from '../../shared/confirm'
import { useRoute } from 'vue-router'
import { get, post } from '../../shared/api'
import type { ApiError } from '../../shared/api'
import { formatTime } from '../../shared/format'
import { useAuthStore } from '../../shared/stores/auth'
import type { AftersaleDetail, AftersaleStatus } from '../../shared/types'
import { AFTERSALE_STATUS_TEXT, AFTERSALE_TYPE_TEXT } from '../../shared/types'
import MmButton from '../../shared/components/MmButton.vue'
import MmCard from '../../shared/components/MmCard.vue'
import MmInput from '../../shared/components/MmInput.vue'
import MmTag from '../../shared/components/MmTag.vue'
import PriceText from '../../shared/components/PriceText.vue'
import EvidenceImages from './EvidenceImages.vue'

const route = useRoute()
const auth = useAuthStore()

const detail = ref<AftersaleDetail | null>(null)
const error = ref('')
const acting = ref(false)
const actionError = ref('')

const respondForm = reactive({ agree: true, reply: '', returnRecipient:'',returnPhone:'',returnAddress:'' })
const shipForm = reactive({ carrier: '', trackingNo: '' })

const isBuyer = computed(
  () => auth.me !== null && detail.value !== null && auth.me.id === detail.value.buyerId,
)
const isSeller = computed(
  () => auth.me !== null && detail.value !== null && auth.me.id === detail.value.sellerId,
)

const steps = computed(() =>
  detail.value?.type === 'RETURN_REFUND'
    ? ['提交申请', '卖家答复', '买家寄回', '卖家验退', '退款完成']
    : ['提交申请', '卖家答复', '退款完成'],
)

const isTerminated = computed(() =>
  detail.value
    ? ['SELLER_REJECTED', 'PENDING_MANUAL', 'CLOSED'].includes(detail.value.status)
    : false,
)

const currentStep = computed(() => {
  if (!detail.value) return 0
  const isReturn = detail.value.type === 'RETURN_REFUND'
  switch (detail.value.status) {
    case 'PENDING_SELLER':
    case 'SELLER_REJECTED':
    case 'PENDING_MANUAL':
      return 1
    case 'PENDING_RETURN':
      return 2
    case 'RETURN_SHIPPED':
      return 3
    case 'RESOLVED':
      return steps.value.length - 1
    case 'CLOSED':
      return isReturn ? 2 : 1
    default:
      return 0
  }
})

type TagTone = 'primary' | 'success' | 'warning' | 'danger' | 'info' | 'neutral'

function statusTone(status: AftersaleStatus): TagTone {
  switch (status) {
    case 'PENDING_SELLER':
    case 'PENDING_RETURN':
    case 'RETURN_SHIPPED':
      return 'warning'
    case 'PENDING_MANUAL':
      return 'danger'
    case 'RESOLVED':
      return 'success'
    case 'SELLER_REJECTED':
      return 'info'
    default:
      return 'neutral'
  }
}

function roleText(role: string): string {
  if (role === 'BUYER') return '买家'
  if (role === 'SELLER') return '卖家'
  if (['ADMIN', 'SUPER_ADMIN', 'OPERATOR', 'SUPPORT'].includes(role)) return '平台'
  if (role === 'SYSTEM') return '系统'
  return '处理方'
}

const actionLabels: Record<string, string> = {
  CREATE: '提交售后申请',
  SELLER_REJECT: '卖家拒绝申请',
  SELLER_AGREE_REFUND: '卖家同意退款',
  SELLER_AGREE_RETURN: '卖家同意退货并提供地址',
  RETURN_SHIPPED: '买家已寄回商品',
  RETURN_RECEIVED: '卖家确认退件签收',
  CONFIRM_RETURN: '卖家验收退件并发起退款',
  ESCALATE_MANUAL: '转入平台人工处理',
  ADMIN_RESOLVE_REFUND: '平台处理并发起退款',
  ADMIN_RESOLVE_REJECT: '平台驳回申请',
}

function actionText(action: string): string {
  return actionLabels[action] ?? '售后处理记录'
}

async function load() {
  error.value = ''
  try {
    detail.value = await get<AftersaleDetail>(`/aftersales/${route.params.id}`)
  } catch (e) {
    error.value = (e as ApiError).message || '加载失败，请稍后重试'
  }
}

async function onRespond() {
  if (!detail.value) return
  if (!respondForm.agree && !respondForm.reply.trim()) {
    actionError.value = '拒绝申请时请填写拒绝理由'
    return
  }
  if (respondForm.agree&&detail.value.type==='RETURN_REFUND'&&(!respondForm.returnRecipient.trim()||!respondForm.returnPhone.trim()||!respondForm.returnAddress.trim())) {
    actionError.value='同意退货时，请完整填写收件人、联系电话和退货地址'
    return
  }
  acting.value = true
  actionError.value = ''
  try {
    detail.value = await post<AftersaleDetail>(
      `/seller/aftersales/${detail.value.id}/respond`,
      { agree: respondForm.agree, reply: respondForm.reply.trim(),...respondForm.agree&&detail.value.type==='RETURN_REFUND'?{returnRecipient:respondForm.returnRecipient.trim(),returnPhone:respondForm.returnPhone.trim(),returnAddress:respondForm.returnAddress.trim()}:{} },
    )
  } catch (e) {
    actionError.value = (e as ApiError).message || '提交失败，请稍后重试'
  } finally {
    acting.value = false
  }
}

async function onReturnShip() {
  if (!detail.value) return
  if (!shipForm.carrier.trim() || !shipForm.trackingNo.trim()) {
    actionError.value = '请填写快递公司与运单号'
    return
  }
  acting.value = true
  actionError.value = ''
  try {
    detail.value = await post<AftersaleDetail>(
      `/me/aftersales/${detail.value.id}/return-ship`,
      { carrier: shipForm.carrier.trim(), trackingNo: shipForm.trackingNo.trim() },
    )
  } catch (e) {
    actionError.value = (e as ApiError).message || '提交失败，请稍后重试'
  } finally {
    acting.value = false
  }
}

async function onConfirmReturn() {
  if (!detail.value) return
  if(!await askConfirmation('确认退件已实际收到并验收无误，同意按本次申请金额退款？'))return
  acting.value = true
  actionError.value = ''
  try {
    detail.value = await post<AftersaleDetail>(
      `/seller/aftersales/${detail.value.id}/confirm-return`,
    )
  } catch (e) {
    actionError.value = (e as ApiError).message || '操作失败，请稍后重试'
  } finally {
    acting.value = false
  }
}

async function onReceiveReturn(){
  if(!detail.value||acting.value)return
  if(!await askConfirmation('确认退件已实际签收？确认后进入48小时验退答复窗口。'))return
  acting.value=true;actionError.value=''
  try{await post(`/seller/aftersales/${detail.value.id}/receive-return`);await load()}
  catch(e){actionError.value=(e as ApiError).message||'签收确认失败'}
  finally{acting.value=false}
}

watch(()=>route.params.id,()=>{detail.value=null;actionError.value='';void load()},{immediate:true})
async function escalate() {
  if(!detail.value||acting.value)return
  acting.value=true;actionError.value=''
  try {await post(`/me/aftersales/${detail.value.id}/escalate`);await load()}
  catch(e){actionError.value=(e as ApiError).message}
  finally{acting.value=false}
}
</script>

<style scoped>
.mm-aftersale-detail {
  display: flex;
  flex-direction: column;
  gap: var(--mm-space-4);
  width: 100%;
  max-width: 960px;
  margin: 0 auto;
  padding: var(--mm-space-5) var(--mm-space-4);
}

.mm-aftersale-detail__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--mm-space-3);
}

.mm-aftersale-detail__title {
  font-size: var(--mm-font-xl);
}

.mm-aftersale-detail__meta {
  font-size: var(--mm-font-s);
  color: var(--mm-muted);
}

.mm-aftersale-detail__error {
  color: var(--mm-danger);
}

.mm-aftersale-detail__hint {
  color: var(--mm-muted);
  text-align: center;
  padding: var(--mm-space-6) 0;
}

.mm-aftersale-detail__notice {
  margin-top: var(--mm-space-3);
  padding: var(--mm-space-3);
  border-radius: var(--mm-radius-m);
  background-color: var(--mm-canvas);
  font-size: var(--mm-font-s);
}

.mm-aftersale-detail__steps {
  display: flex;
  margin-top: var(--mm-space-4);
}

.mm-aftersale-detail__step {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--mm-space-1);
  position: relative;
  color: var(--mm-muted);
  font-size: var(--mm-font-s);
}

.mm-aftersale-detail__step-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  border: 2px solid var(--mm-border);
  background-color: var(--mm-white);
  z-index: 1;
}

.mm-aftersale-detail__step::before {
  content: '';
  position: absolute;
  top: 6px;
  left: -50%;
  width: 100%;
  height: 2px;
  background-color: var(--mm-border);
}

.mm-aftersale-detail__step:first-child::before {
  display: none;
}

.mm-aftersale-detail__step.is-done,
.mm-aftersale-detail__step.is-current {
  color: var(--mm-primary);
  font-weight: 600;
}

.mm-aftersale-detail__step.is-done .mm-aftersale-detail__step-dot,
.mm-aftersale-detail__step.is-current .mm-aftersale-detail__step-dot {
  border-color: var(--mm-primary);
  background-color: var(--mm-primary);
}

.mm-aftersale-detail__step.is-done::before,
.mm-aftersale-detail__step.is-current::before {
  background-color: var(--mm-primary);
}

.mm-aftersale-detail__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: var(--mm-space-3);
}

.mm-aftersale-detail__grid dt {
  font-size: var(--mm-font-s);
  color: var(--mm-muted);
}

.mm-aftersale-detail__grid dd {
  margin: 0;
}

.mm-aftersale-detail__grid-wide {
  grid-column: 1 / -1;
}

.mm-aftersale-detail__pre {
  white-space: pre-wrap;
  word-break: break-word;
}

.mm-aftersale-detail__rules {
  display: flex;
  flex-direction: column;
  gap: var(--mm-space-2);
  font-size: var(--mm-font-s);
  color: var(--mm-muted);
}

.mm-aftersale-detail__form {
  display: flex;
  flex-direction: column;
  gap: var(--mm-space-3);
  max-width: 480px;
}

.mm-aftersale-detail__radios {
  display: flex;
  flex-direction: column;
  gap: var(--mm-space-2);
}

.mm-aftersale-detail__radios label {
  display: flex;
  align-items: center;
  gap: var(--mm-space-2);
}

.mm-aftersale-detail__field {
  display: flex;
  flex-direction: column;
  gap: var(--mm-space-1);
  font-size: var(--mm-font-s);
  font-weight: 600;
}

.mm-aftersale-detail__field textarea {
  padding: var(--mm-space-2) var(--mm-space-3);
  border: 1px solid var(--mm-border);
  border-radius: var(--mm-radius-m);
  resize: vertical;
  font-weight: 400;
}

.mm-aftersale-detail__timeline {
  display: flex;
  flex-direction: column;
}

.mm-aftersale-detail__log {
  display: flex;
  gap: var(--mm-space-3);
  position: relative;
  padding-bottom: var(--mm-space-4);
}

.mm-aftersale-detail__log::before {
  content: '';
  position: absolute;
  left: 5px;
  top: 14px;
  bottom: 0;
  width: 2px;
  background-color: var(--mm-border);
}

.mm-aftersale-detail__log:last-child::before {
  display: none;
}

.mm-aftersale-detail__log-dot {
  width: 12px;
  height: 12px;
  margin-top: 4px;
  border-radius: 50%;
  background-color: var(--mm-primary);
  flex-shrink: 0;
  z-index: 1;
}

.mm-aftersale-detail__log-action {
  display: flex;
  align-items: center;
  gap: var(--mm-space-2);
  font-weight: 600;
}

.mm-aftersale-detail__log-note {
  font-size: var(--mm-font-s);
  color: var(--mm-ink);
  margin-top: var(--mm-space-1);
  white-space: pre-wrap;
}

.mm-aftersale-detail__log-time {
  font-size: var(--mm-font-s);
  color: var(--mm-muted);
}
</style>
