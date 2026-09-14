<template>
  <div class="mm-account">
    <header class="mm-account__identity">
      <div class="mm-account__portrait" aria-label="个人头像">
        <img
          v-if="auth.me?.avatarUrl && !avatarBroken"
          :src="auth.me.avatarUrl"
          alt="我的头像"
          @error="avatarBroken = true"
        />
        <span v-else>{{ auth.me?.nickname?.slice(0, 1) || '麦' }}</span>
      </div>
      <div>
        <p class="mm-account__eyebrow">我的麦麦</p>
        <h1 class="mm-account__heading">{{ currentTabTitle }}</h1>
        <p class="mm-account__meta">
          {{ auth.me?.nickname }} · 管理你的资料、收货地址与消息通知
        </p>
      </div>
      <RouterLink to="/orders" class="mm-account__orders"
        >查看我的订单 →</RouterLink
      >
    </header>

    <div class="mm-account__tabs" role="tablist" aria-label="个人中心分区">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        class="mm-account__tab"
        :class="{ 'is-active': activeTab === tab.key }"
        role="tab"
        :aria-selected="activeTab === tab.key"
        @click="activeTab = tab.key"
      >
        {{ tab.label }}
        <span
          v-if="tab.key === 'notifications' && unreadCount > 0"
          class="mm-account__badge"
        >
          {{ unreadCount > 99 ? '99+' : unreadCount }}
        </span>
      </button>
    </div>

    <!-- 资料 -->
    <MmCard v-show="activeTab === 'profile'" title="基本资料">
      <div class="mm-account__profile">
        <section class="mm-account__avatar-settings">
          <div>
            <h3>让大家认识你</h3>
            <p class="mm-account__meta">
              头像会显示在顶部个人菜单中，请使用适合公开展示的图片。
            </p>
          </div>
          <label class="mm-account__avatar-upload"
            >{{ avatarSaving ? '头像保存中…' : '上传新头像'
            }}<input
              type="file"
              accept="image/jpeg,image/png"
              aria-label="上传头像"
              :disabled="avatarSaving"
              @change="saveAvatar"
          /></label>
          <p class="mm-account__meta">
            支持 JPG、PNG，最大 5MB。上传后会移除图片元数据。
          </p>
          <p v-if="avatarError" role="alert" class="mm-account__error">
            {{ avatarError }}
          </p>
          <p v-if="avatarDone" role="status" class="mm-account__ok">
            头像已更新
          </p>
        </section>
        <p class="mm-account__meta">邮箱：{{ auth.me?.email }}</p>
        <form class="mm-account__form" @submit.prevent="onSaveNickname">
          <MmInput
            v-model="nicknameForm.nickname"
            label="昵称"
            maxlength="50"
            placeholder="请输入昵称"
            :error="nicknameForm.error"
          />
          <MmButton type="submit" :loading="nicknameForm.saving"
            >保存昵称</MmButton
          >
          <p v-if="nicknameForm.done" class="mm-account__ok" role="status">
            昵称已更新
          </p>
        </form>
      </div>
    </MmCard>

    <!-- 收货地址 -->
    <MmCard v-show="activeTab === 'addresses'" title="收货地址">
      <template #extra>
        <MmButton variant="ghost" @click="startAddAddress">新增地址</MmButton>
      </template>
      <p v-if="addressError" class="mm-account__error" role="alert">
        {{ addressError }}
      </p>
      <EmptyState v-else-if="addresses.length === 0" title="还没有收货地址" />
      <ul v-else class="mm-account__address-list">
        <li
          v-for="addr in addresses"
          :key="addr.id"
          class="mm-account__address"
        >
          <div>
            <p>
              <strong>{{ addr.receiver }}</strong> {{ addr.phone }}
              <MmTag v-if="addr.isDefault" text="默认" tone="primary" />
            </p>
            <p class="mm-account__meta">{{ addr.region }} {{ addr.detail }}</p>
          </div>
          <div class="mm-account__address-actions">
            <MmButton variant="ghost" @click="startEditAddress(addr)"
              >编辑</MmButton
            >
            <MmButton variant="danger" @click="onDeleteAddress(addr)"
              >删除</MmButton
            >
          </div>
        </li>
      </ul>

      <form
        v-if="addressForm.visible"
        class="mm-account__form mm-account__address-form"
        @submit.prevent="onSaveAddress"
      >
        <h3 class="mm-account__subheading">
          {{ addressForm.id ? '编辑地址' : '新增地址' }}
        </h3>
        <MmInput v-model="addressForm.receiver" label="收货人" maxlength="50" />
        <MmInput v-model="addressForm.phone" label="手机号" maxlength="20" />
        <MmInput
          v-model="addressForm.region"
          label="所在地区"
          maxlength="100"
          placeholder="省 / 市 / 区"
        />
        <MmInput
          v-model="addressForm.detail"
          label="详细地址"
          maxlength="200"
        />
        <MapPicker @select="onAddressPicked" />
        <label class="mm-account__checkbox">
          <input v-model="addressForm.isDefault" type="checkbox" />
          设为默认地址
        </label>
        <p v-if="addressForm.error" class="mm-account__error" role="alert">
          {{ addressForm.error }}
        </p>
        <div class="mm-account__form-actions">
          <MmButton type="submit" :loading="addressForm.saving">保存</MmButton>
          <MmButton variant="ghost" @click="addressForm.visible = false"
            >取消</MmButton
          >
        </div>
      </form>
    </MmCard>

    <!-- 卖家入驻 -->
    <MmCard v-show="activeTab === 'seller'" title="卖家入驻申请">
      <div v-if="sellerApp" class="mm-account__seller-status">
        <p>
          人工审核：<MmTag
            :text="SELLER_STATUS_TEXT[sellerApp.status]"
            :tone="sellerStatusTone"
          />
        </p>
        <p>
          渠道资格：<MmTag
            :text="CHANNEL_STATUS_TEXT[sellerApp.channelStatus]"
            :tone="channelStatusTone"
          />
        </p>
        <p v-if="sellerApp.reason" class="mm-account__meta">
          审核说明：{{ sellerApp.reason }}
        </p>
        <p class="mm-account__meta">
          提交时间：{{ formatTime(sellerApp.createdAt) }} ·
          人工审核与收款渠道资格分开记录，
          两者均通过后才能发布可成交商品并收款。
        </p>
      </div>
      <p v-else class="mm-account__meta">你还没有提交过卖家入驻申请。</p>

      <form
        v-if="
          !sellerApp ||
          ['REJECTED', 'SUPPLEMENT', 'NONE'].includes(sellerApp.status)
        "
        class="mm-account__form"
        @submit.prevent="onApplySeller"
      >
        <h3 class="mm-account__subheading">
          {{ sellerApp ? '重新提交申请' : '提交入驻申请' }}
        </h3>
        <label class="mm-account__field">
          <span>自我介绍 / 经营说明</span>
          <textarea
            v-model="sellerForm.intro"
            rows="4"
            maxlength="500"
            placeholder="介绍一下你想出售的闲置类型、交易方式等"
          ></textarea>
        </label>
        <p v-if="sellerForm.error" class="mm-account__error" role="alert">
          {{ sellerForm.error }}
        </p>
        <MmButton type="submit" :loading="sellerForm.submitting"
          >提交申请</MmButton
        >
      </form>
    </MmCard>

    <!-- 站内通知 -->
    <MmCard v-show="activeTab === 'notifications'" title="站内通知">
      <template #extra>
        <MmButton
          v-if="unreadCount > 0"
          variant="ghost"
          :loading="markingAll"
          @click="onMarkAllRead"
        >
          本页全部已读
        </MmButton>
      </template>
      <p v-if="notificationError" class="mm-account__error" role="alert">
        {{ notificationError }}
      </p>
      <p v-else-if="notificationLoading" class="mm-account__meta">加载中…</p>
      <EmptyState v-else-if="notifications.length === 0" title="暂无通知" />
      <template v-else>
        <ul class="mm-account__notification-list">
          <li
            v-for="item in notifications"
            :key="item.id"
            class="mm-account__notification"
            :class="{ 'is-unread': !item.read }"
          >
            <div>
              <p class="mm-account__notification-title">
                <span
                  v-if="!item.read"
                  class="mm-account__unread-dot"
                  aria-label="未读"
                ></span>
                {{ item.title }}
              </p>
              <p class="mm-account__notification-content">{{ item.content }}</p>
              <p class="mm-account__meta">{{ formatTime(item.createdAt) }}</p>
            </div>
            <MmButton
              v-if="!item.read"
              variant="ghost"
              @click="onMarkRead(item.id)"
            >
              标记已读
            </MmButton>
          </li>
        </ul>
        <MmPagination
          :page="notificationPage"
          :total-pages="notificationTotalPages"
          @change="onNotificationPage"
        />
      </template>
    </MmCard>
    <div class="mm-account__privacy">
      <RouterLink to="/policies">查看隐私与交易规则</RouterLink
      ><RouterLink to="/me/closure">账号注销申请</RouterLink>
    </div>
  </div>
</template>

<script setup lang="ts">
import { askConfirmation } from '../../shared/confirm'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { del, get, post, put, upload } from '../../shared/api'
import type { ApiError } from '../../shared/api'
import { formatTime } from '../../shared/format'
import { useAuthStore } from '../../shared/stores/auth'
import type {
  Address,
  Notification,
  Page,
  SellerApplication,
} from '../../shared/types'
import { CHANNEL_STATUS_TEXT, SELLER_STATUS_TEXT } from '../../shared/types'
import EmptyState from '../../shared/components/EmptyState.vue'
import MmButton from '../../shared/components/MmButton.vue'
import MmCard from '../../shared/components/MmCard.vue'
import MmInput from '../../shared/components/MmInput.vue'
import MmPagination from '../../shared/components/MmPagination.vue'
import MmTag from '../../shared/components/MmTag.vue'
import MapPicker from '../../shared/components/MapPicker.vue'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

function onAddressPicked(place: { region: string; detail: string }) {
  addressForm.region = place.region
  addressForm.detail = place.detail
}

type TabKey = 'profile' | 'addresses' | 'seller' | 'notifications'

const tabs: { key: TabKey; label: string }[] = [
  { key: 'profile', label: '基本资料' },
  { key: 'addresses', label: '收货地址' },
  { key: 'seller', label: '卖家入驻' },
  { key: 'notifications', label: '站内通知' },
]
const activeTab = computed<TabKey>({
  get: () =>
    tabs.some((tab) => tab.key === route.query.tab)
      ? (route.query.tab as TabKey)
      : 'profile',
  set: (tab) => {
    void router.replace({ query: { ...route.query, tab } })
  },
})
const currentTabTitle = computed(
  () =>
    ({
      profile: '个人资料',
      addresses: '我的地址簿',
      seller: '卖家入驻',
      notifications: '站内通知',
    })[activeTab.value],
)
const avatarSaving = ref(false),
  avatarError = ref(''),
  avatarDone = ref(false),
  avatarBroken = ref(false)
async function saveAvatar(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  avatarError.value = ''
  avatarDone.value = false
  if (
    !['image/jpeg', 'image/png'].includes(file.type) ||
    file.size > 5 * 1024 * 1024
  ) {
    avatarError.value = '请选择不超过 5MB 的 JPG 或 PNG 图片'
    input.value = ''
    return
  }
  avatarSaving.value = true
  try {
    const body = new FormData()
    body.append('file', file)
    const saved = await upload<{ avatarUrl: string }>('/me/avatar', body)
    if (auth.me) auth.me.avatarUrl = saved.avatarUrl
    avatarBroken.value = false
    avatarDone.value = true
  } catch (error) {
    avatarError.value =
      (error as ApiError).message || '头像上传失败，请稍后重试'
  } finally {
    avatarSaving.value = false
    input.value = ''
  }
}

/* ---------- 资料 ---------- */

const nicknameForm = reactive({
  nickname: auth.me?.nickname ?? '',
  saving: false,
  error: '',
  done: false,
})

async function onSaveNickname() {
  const nickname = nicknameForm.nickname.trim()
  if (!nickname) {
    nicknameForm.error = '昵称不能为空'
    return
  }
  nicknameForm.saving = true
  nicknameForm.error = ''
  nicknameForm.done = false
  try {
    await put('/me', { nickname })
    await auth.fetchMe()
    nicknameForm.done = true
  } catch (e) {
    nicknameForm.error = (e as ApiError).message || '保存失败，请稍后重试'
  } finally {
    nicknameForm.saving = false
  }
}

/* ---------- 收货地址 ---------- */

const addresses = ref<Address[]>([])
const addressError = ref('')
const addressForm = reactive({
  visible: false,
  id: 0,
  receiver: '',
  phone: '',
  region: '',
  detail: '',
  isDefault: false,
  saving: false,
  error: '',
})

async function loadAddresses() {
  addressError.value = ''
  try {
    addresses.value = await get<Address[]>('/me/addresses')
  } catch (e) {
    addressError.value = (e as ApiError).message || '地址加载失败'
  }
}

function startAddAddress() {
  Object.assign(addressForm, {
    visible: true,
    id: 0,
    receiver: '',
    phone: '',
    region: '',
    detail: '',
    isDefault: false,
    error: '',
  })
}

function startEditAddress(addr: Address) {
  Object.assign(addressForm, {
    visible: true,
    id: addr.id,
    receiver: addr.receiver,
    phone: addr.phone,
    region: addr.region,
    detail: addr.detail,
    isDefault: addr.isDefault,
    error: '',
  })
}

async function onSaveAddress() {
  const payload = {
    receiver: addressForm.receiver.trim(),
    phone: addressForm.phone.trim(),
    region: addressForm.region.trim(),
    detail: addressForm.detail.trim(),
    isDefault: addressForm.isDefault,
  }
  if (
    !payload.receiver ||
    !payload.phone ||
    !payload.region ||
    !payload.detail
  ) {
    addressForm.error = '请完整填写收货人、手机号、地区与详细地址'
    return
  }
  addressForm.saving = true
  addressForm.error = ''
  try {
    if (addressForm.id) {
      await put(`/me/addresses/${addressForm.id}`, payload)
    } else {
      await post('/me/addresses', payload)
    }
    addressForm.visible = false
    await loadAddresses()
  } catch (e) {
    addressForm.error = (e as ApiError).message || '保存失败，请稍后重试'
  } finally {
    addressForm.saving = false
  }
}

async function onDeleteAddress(addr: Address) {
  if (
    !(await askConfirmation(
      `确定删除收货地址「${addr.receiver} ${addr.region}」吗？`,
    ))
  )
    return
  try {
    await del(`/me/addresses/${addr.id}`)
    await loadAddresses()
  } catch (e) {
    addressError.value = (e as ApiError).message || '删除失败，请稍后重试'
  }
}

/* ---------- 卖家入驻 ---------- */

const sellerApp = ref<SellerApplication | null>(null)
const sellerForm = reactive({ intro: '', submitting: false, error: '' })

const sellerStatusTone = computed(() => {
  switch (sellerApp.value?.status) {
    case 'APPROVED':
      return 'success'
    case 'PENDING':
      return 'warning'
    case 'SUPPLEMENT':
      return 'info'
    case 'REJECTED':
    case 'SUSPENDED':
      return 'danger'
    default:
      return 'neutral'
  }
})

const channelStatusTone = computed(() => {
  switch (sellerApp.value?.channelStatus) {
    case 'QUALIFIED':
      return 'success'
    case 'PENDING':
      return 'warning'
    case 'REJECTED':
      return 'danger'
    default:
      return 'neutral'
  }
})

async function loadSellerApp() {
  try {
    const result = await get<SellerApplication | null>('/me/seller-application')
    sellerApp.value = result?.id ? result : null
  } catch {
    sellerApp.value = null
  }
}

async function onApplySeller() {
  sellerForm.submitting = true
  sellerForm.error = ''
  try {
    sellerApp.value = await post<SellerApplication>('/me/seller-application', {
      intro: sellerForm.intro.trim(),
    })
    sellerForm.intro = ''
    await auth.fetchMe()
  } catch (e) {
    sellerForm.error = (e as ApiError).message || '提交失败，请稍后重试'
  } finally {
    sellerForm.submitting = false
  }
}

/* ---------- 站内通知 ---------- */

const NOTIFICATION_PAGE_SIZE = 10
const notifications = ref<Notification[]>([])
const notificationPage = ref(0)
const notificationTotalPages = ref(1)
const notificationLoading = ref(false)
const notificationError = ref('')
const unreadCount = ref(0)
const markingAll = ref(false)

async function loadUnreadCount() {
  try {
    const data = await get<{ count: number }>('/me/notifications/unread-count')
    unreadCount.value = data.count
  } catch {
    /* 未读数失败不阻塞列表 */
  }
}

async function loadNotifications() {
  notificationLoading.value = true
  notificationError.value = ''
  try {
    const data = await get<Page<Notification>>('/me/notifications', {
      page: notificationPage.value,
      size: NOTIFICATION_PAGE_SIZE,
    })
    notifications.value = data.content
    notificationTotalPages.value = Math.max(1, data.totalPages)
  } catch (e) {
    notificationError.value = (e as ApiError).message || '通知加载失败'
  } finally {
    notificationLoading.value = false
  }
}

function onNotificationPage(next: number) {
  notificationPage.value = next
  loadNotifications()
}

async function onMarkRead(id: number) {
  try {
    await post('/me/notifications/read', { ids: [id] })
    const item = notifications.value.find((n) => n.id === id)
    if (item) item.read = true
    unreadCount.value = Math.max(0, unreadCount.value - 1)
  } catch (e) {
    notificationError.value = (e as ApiError).message || '操作失败'
  }
}

async function onMarkAllRead() {
  const ids = notifications.value.filter((n) => !n.read).map((n) => n.id)
  if (ids.length === 0) return
  markingAll.value = true
  try {
    await post('/me/notifications/read', { ids })
    await Promise.all([loadNotifications(), loadUnreadCount()])
  } catch (e) {
    notificationError.value = (e as ApiError).message || '操作失败'
  } finally {
    markingAll.value = false
  }
}

onMounted(() => {
  loadAddresses()
  loadSellerApp()
  loadNotifications()
  loadUnreadCount()
})
</script>

<style scoped>
.mm-account__identity {
  display: flex;
  gap: 22px;
  align-items: center;
  padding: 18px 0 28px;
  border-bottom: 1px solid var(--mm-border);
}
.mm-account__portrait {
  width: 88px;
  height: 88px;
  flex-shrink: 0;
  border-radius: 50%;
  background: var(--mm-ink);
  color: var(--mm-white);
  display: grid;
  place-items: center;
  font-size: 32px;
  overflow: hidden;
}
.mm-account__portrait img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.mm-account__eyebrow {
  font-size: 12px;
  letter-spacing: 0.12em;
  color: var(--mm-muted);
  margin-bottom: 6px;
}
.mm-account__orders {
  margin-left: auto;
  white-space: nowrap;
  font-size: 14px;
}
.mm-account__avatar-settings {
  padding-bottom: 24px;
  margin-bottom: 20px;
  border-bottom: 1px solid var(--mm-border);
  display: grid;
  gap: 12px;
}
.mm-account__avatar-upload {
  width: max-content;
  position: relative;
  padding: 10px 16px;
  border: 1px solid var(--mm-border);
  border-radius: 6px;
  cursor: pointer;
  font-weight: 600;
  overflow: hidden;
}
.mm-account__avatar-upload input {
  position: absolute;
  inset: 0;
  opacity: 0;
  cursor: pointer;
  width: 100%;
}
.mm-account__avatar-upload:focus-within {
  outline: 2px solid var(--mm-primary);
  outline-offset: 2px;
}
.mm-account__privacy {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  padding-top: 16px;
  color: var(--mm-muted);
}
.mm-account :deep(.mm-card) {
  box-shadow: none;
  border-radius: 8px;
}
@media (max-width: 600px) {
  .mm-account__identity {
    flex-wrap: wrap;
    gap: 14px;
  }
  .mm-account__portrait {
    width: 64px;
    height: 64px;
  }
  .mm-account__identity > div:nth-child(2) {
    flex: 1;
    min-width: 0;
  }
  .mm-account__orders {
    margin: 0;
    flex-basis: 100%;
    padding-left: 78px;
  }
  .mm-account__heading {
    font-size: 24px !important;
  }
}
.mm-account {
  display: flex;
  flex-direction: column;
  gap: var(--mm-space-4);
  width: 100%;
  max-width: 960px;
  margin: 0 auto;
  padding: var(--mm-space-5) var(--mm-space-4);
}

.mm-account__heading {
  font-size: var(--mm-font-xl);
}

.mm-account__tabs {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mm-space-2);
}

.mm-account__tab {
  display: inline-flex;
  align-items: center;
  gap: var(--mm-space-1);
  padding: var(--mm-space-2) var(--mm-space-4);
  border: 1px solid var(--mm-border);
  border-radius: var(--mm-radius-m);
  background-color: var(--mm-white);
  color: var(--mm-ink);
  font-size: var(--mm-font-base);
}

.mm-account__tab.is-active {
  background-color: var(--mm-primary);
  border-color: var(--mm-primary);
  color: var(--mm-white);
  font-weight: 600;
}

.mm-account__badge {
  min-width: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background-color: var(--mm-danger);
  color: var(--mm-white);
  font-size: 12px;
  text-align: center;
}

.mm-account__form {
  display: flex;
  flex-direction: column;
  gap: var(--mm-space-3);
  max-width: 480px;
  margin-top: var(--mm-space-3);
}

.mm-account__form-actions {
  display: flex;
  gap: var(--mm-space-2);
}

.mm-account__profile {
  display: flex;
  flex-direction: column;
  gap: var(--mm-space-2);
}

.mm-account__meta {
  font-size: var(--mm-font-s);
  color: var(--mm-muted);
}

.mm-account__error {
  color: var(--mm-danger);
  font-size: var(--mm-font-s);
}

.mm-account__ok {
  color: var(--mm-success);
  font-size: var(--mm-font-s);
}

.mm-account__subheading {
  font-size: var(--mm-font-base);
  font-weight: 600;
}

.mm-account__address-list {
  display: flex;
  flex-direction: column;
  gap: var(--mm-space-3);
}

.mm-account__address {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--mm-space-3);
  padding: var(--mm-space-3);
  border: 1px solid var(--mm-border);
  border-radius: var(--mm-radius-m);
}

.mm-account__address-actions {
  display: flex;
  gap: var(--mm-space-2);
  flex-shrink: 0;
}

.mm-account__address-form {
  border-top: 1px solid var(--mm-border);
  padding-top: var(--mm-space-4);
}

.mm-account__checkbox {
  display: flex;
  align-items: center;
  gap: var(--mm-space-2);
  font-size: var(--mm-font-s);
}

.mm-account__seller-status {
  display: flex;
  flex-direction: column;
  gap: var(--mm-space-2);
}

.mm-account__field {
  display: flex;
  flex-direction: column;
  gap: var(--mm-space-1);
  font-size: var(--mm-font-s);
  font-weight: 600;
}

.mm-account__field textarea {
  padding: var(--mm-space-2) var(--mm-space-3);
  border: 1px solid var(--mm-border);
  border-radius: var(--mm-radius-m);
  resize: vertical;
  font-weight: 400;
}

.mm-account__notification-list {
  display: flex;
  flex-direction: column;
  gap: var(--mm-space-3);
}

.mm-account__notification {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--mm-space-3);
  padding: var(--mm-space-3);
  border: 1px solid var(--mm-border);
  border-radius: var(--mm-radius-m);
}

.mm-account__notification.is-unread {
  border-color: var(--mm-primary);
  background-color: var(--mm-canvas);
}

.mm-account__notification-title {
  display: flex;
  align-items: center;
  gap: var(--mm-space-2);
  font-weight: 600;
}

.mm-account__unread-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: var(--mm-danger);
  flex-shrink: 0;
}

.mm-account__notification-content {
  font-size: var(--mm-font-s);
  margin-top: var(--mm-space-1);
}

@media (max-width: 768px) {
  .mm-account__address,
  .mm-account__notification {
    flex-direction: column;
  }
}
</style>
