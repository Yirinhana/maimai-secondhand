<template>
  <div class="mm-account">
    <header class="mm-account__identity">
      <div class="mm-account__portrait" aria-label="个人头像">
        <UserAvatar
          :src="auth.me?.avatarUrl"
          :nickname="auth.me?.nickname || '麦'"
          :size="76"
        />
      </div>
      <div>
        <p class="mm-account__eyebrow">{{ auth.me?.nickname }} · 我的麦麦</p>
        <h1 class="mm-account__heading">{{ currentTabTitle }}</h1>
        <p class="mm-account__meta">
          {{ tabDescriptions[activeTab] }}
        </p>
      </div>
      <RouterLink :to="auth.isAdmin ? '/admin' : '/orders'" class="mm-account__orders"
        >{{ auth.isAdmin ? '进入管理后台 →' : '查看我的订单 →' }}</RouterLink
      >
    </header>

    <div class="mm-account__tabs" role="tablist" aria-label="个人中心分区">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        :id="`account-tab-${tab.key}`"
        type="button"
        :aria-controls="`account-panel-${tab.key}`"
        :tabindex="activeTab === tab.key ? 0 : -1"
        @keydown="onTabKeydown($event, tab.key)"
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

    <OperationsDashboard v-if="auth.isAdmin && activeTab === 'profile'" title="我的管理概览" />
    <ReputationCard v-if="auth.me && !auth.isAdmin && activeTab === 'profile'" :user-id="auth.me.id" />
    <!-- 资料 -->
    <MmCard
      id="account-panel-profile"
      role="tabpanel"
      aria-labelledby="account-tab-profile"
      tabindex="0"
      v-show="activeTab === 'profile'"
      title="基本资料"
    >
      <div class="mm-account__profile">
        <section class="mm-account__avatar-settings">
          <div>
            <h3>让大家认识你</h3>
            <p class="mm-account__meta">
              头像会显示在顶部个人菜单中，请使用适合公开展示的图片。
            </p>
          </div>
          <div
            class="mm-account__avatar-presets"
            role="group"
            aria-label="选择默认头像"
          >
            <button
              v-for="avatar in defaultAvatars"
              :key="avatar.id"
              type="button"
              :disabled="avatarSaving"
              :aria-label="`使用${avatar.name}头像`"
              @click="saveDefaultAvatar(avatar)"
            >
              <img :src="avatar.src" alt="" width="52" height="52" />
              <span>{{ avatar.name }}</span>
            </button>
          </div>
          <p class="mm-account__meta">
            点选一款插画头像，或上传自己的照片。插画由 AI 生成。
          </p>
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
        <section class="mm-account__profile-details">
          <div class="mm-account__email">
            <span>登录邮箱</span><strong>{{ auth.me?.email }}</strong>
            <p>用于登录、接收验证码和找回密码。</p>
          </div>
          <form
            ref="nicknameFormElement"
            class="mm-account__form"
            :aria-busy="nicknameForm.saving"
            @submit.prevent="onSaveNickname"
          >
            <fieldset
              :disabled="nicknameForm.saving"
              class="mm-account__address-fields"
            >
              <MmInput
                v-model="nicknameForm.nickname"
                label="昵称"
                maxlength="50"
                placeholder="请输入昵称"
                hint="显示在商品、私信与个人页面中，最多 50 个字符。"
                @update:model-value="
                  nicknameForm.done = false;
                  nicknameForm.error = '';
                "
                :error="nicknameForm.error"
              />
              <MmButton
                type="submit"
                :loading="nicknameForm.saving"
                :disabled="nicknameForm.saving"
                >保存昵称</MmButton
              >
              <p v-if="nicknameForm.done" class="mm-account__ok" role="status">
                昵称已更新
              </p>
            </fieldset>
          </form>
        </section>
      </div>
    </MmCard>

    <!-- 收货地址 -->
    <MmCard
      id="account-panel-addresses"
      role="tabpanel"
      aria-labelledby="account-tab-addresses"
      tabindex="0"
      v-show="activeTab === 'addresses'"
      title="收货地址"
    >
      <template #extra>
        <MmButton
          variant="ghost"
          :disabled="addressForm.saving || deletingAddress !== null"
          @click="startAddAddress"
          >新增地址</MmButton
        >
      </template>
      <p v-if="addressError" class="mm-account__error" role="alert">
        {{ addressError }}
        <button type="button" @click="loadAddresses">重新加载地址</button>
      </p>
      <MmSkeleton
        v-else-if="addressLoading"
        kind="rows"
        :count="2"
        label="正在读取地址"
      />
      <EmptyState
        v-else-if="addresses.length === 0"
        title="还没有收货地址"
        description="添加地址后，快递下单时可以直接选择。"
      />
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
            <MmButton
              variant="ghost"
              :disabled="addressForm.saving || deletingAddress !== null"
              @click="startEditAddress(addr)"
              >编辑</MmButton
            >
            <MmButton
              variant="danger"
              :loading="deletingAddress === addr.id"
              :disabled="addressForm.saving || deletingAddress !== null"
              @click="onDeleteAddress(addr)"
              >删除</MmButton
            >
          </div>
        </li>
      </ul>

      <p v-if="addressDone" class="mm-account__ok" role="status">
        {{ addressDone }}
      </p>
      <form
        ref="addressFormElement"
        :aria-busy="addressForm.saving"
        v-if="addressForm.visible"
        class="mm-account__form mm-account__address-form"
        @submit.prevent="onSaveAddress"
      >
        <h3 class="mm-account__subheading">
          {{ addressForm.id ? '编辑地址' : '新增地址' }}
        </h3>
        <fieldset
          :disabled="addressForm.saving"
          class="mm-account__address-fields"
        >
          <MmInput
            v-model="addressForm.receiver"
            label="收货人"
            autocomplete="shipping name"
            maxlength="50"
          />
          <MmInput
            v-model="addressForm.phone"
            label="手机号"
            inputmode="tel"
            autocomplete="shipping tel"
            maxlength="20"
          />
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
            <MmButton type="submit" :loading="addressForm.saving"
              >保存</MmButton
            >
            <MmButton
              variant="ghost"
              :disabled="addressForm.saving"
              @click="addressForm.visible = false"
              >取消</MmButton
            >
          </div>
        </fieldset>
      </form>
    </MmCard>

    <!-- 卖家入驻 -->
    <MmCard
      id="account-panel-seller"
      role="tabpanel"
      aria-labelledby="account-tab-seller"
      tabindex="0"
      v-show="activeTab === 'seller'"
      title="卖家入驻申请"
    >
      <MmSkeleton
        v-if="sellerLoading"
        kind="rows"
        :count="2"
        label="正在读取入驻状态"
      />
      <p v-else-if="sellerLoadError" class="mm-account__error" role="alert">
        {{ sellerLoadError }}
        <button type="button" @click="loadSellerApp">重新加载入驻状态</button>
      </p>
      <div v-else-if="sellerApp" class="mm-account__seller-status">
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
          !sellerLoading &&
          !sellerLoadError &&
          (!sellerApp ||
            ['REJECTED', 'SUPPLEMENT', 'NONE'].includes(sellerApp.status))
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
            :disabled="sellerForm.submitting"
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
      <p v-if="sellerDone" class="mm-account__ok" role="status">
        入驻申请已提交，请留意审核结果。
      </p>
    </MmCard>

    <!-- 站内通知 -->
    <MmCard
      id="account-panel-notifications"
      role="tabpanel"
      aria-labelledby="account-tab-notifications"
      tabindex="0"
      v-show="activeTab === 'notifications'"
      title="站内通知"
    >
      <template #extra>
        <MmButton
          v-if="unreadCount > 0"
          variant="ghost"
          :loading="markingAll"
          :disabled="markingAll || markingIds.length > 0"
          @click="onMarkAllRead"
        >
          本页全部已读
        </MmButton>
      </template>
      <p v-if="notificationError" class="mm-account__error" role="alert">
        {{ notificationError }}
        <button type="button" @click="loadNotifications">重新加载通知</button>
      </p>
      <MmSkeleton
        v-else-if="notificationLoading"
        kind="rows"
        :count="3"
        label="正在读取通知"
      />
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
              :loading="markingIds.includes(item.id)"
              :disabled="markingAll || markingIds.includes(item.id)"
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
import OperationsDashboard from "../admin/OperationsDashboard.vue";
import ReputationCard from "../community/ReputationCard.vue";
import { askConfirmation } from '../../shared/confirm';
import UserAvatar from '../../shared/components/UserAvatar.vue';
import { defaultAvatars } from '../../shared/defaultAvatars';
import { computed, nextTick, onUnmounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { del, get, post, put, upload } from '../../shared/api';
import type { ApiError } from '../../shared/api';
import { formatTime } from '../../shared/format';
import { useAuthStore } from '../../shared/stores/auth';
import type {
  Address,
  Me,
  Notification,
  Page,
  SellerApplication,
} from '../../shared/types';
import { CHANNEL_STATUS_TEXT, SELLER_STATUS_TEXT } from '../../shared/types';
import EmptyState from '../../shared/components/EmptyState.vue';
import MmButton from '../../shared/components/MmButton.vue';
import MmCard from '../../shared/components/MmCard.vue';
import MmInput from '../../shared/components/MmInput.vue';
import MmPagination from '../../shared/components/MmPagination.vue';
import MmTag from '../../shared/components/MmTag.vue';
import MapPicker from '../../shared/components/MapPicker.vue';
import MmSkeleton from '../../shared/components/MmSkeleton.vue';

const auth = useAuthStore();
const route = useRoute();
const router = useRouter();
let accountVersion = 0;
let disposed = false;
const current = (version: number) => !disposed && version === accountVersion;
const nicknameFormElement = ref<HTMLFormElement | null>(null);
const addressFormElement = ref<HTMLFormElement | null>(null);
const tabDescriptions: Record<TabKey, string> = {
  profile: '设置让大家认识你的头像与昵称。',
  addresses: '管理快递收货信息，下单时轻松选择。',
  seller: '查看入驻资格，准备发布你的闲置。',
  notifications: '交易进展与平台消息，在这里查看。',
};
async function onTabKeydown(event: KeyboardEvent, key: TabKey) {
  const index = tabs.findIndex((tab) => tab.key === key);
  let next = index;
  if (event.key === 'ArrowRight') next = (index + 1) % tabs.length;
  else if (event.key === 'ArrowLeft')
    next = (index - 1 + tabs.length) % tabs.length;
  else if (event.key === 'Home') next = 0;
  else if (event.key === 'End') next = tabs.length - 1;
  else return;
  event.preventDefault();
  const tab = tabs[next]!;
  await router.replace({ query: { ...route.query, tab: tab.key } });
  await nextTick();
  document.getElementById('account-tab-' + tab.key)?.focus();
}

function onAddressPicked(place: { region: string; detail: string }) {
  addressForm.region = place.region;
  addressForm.detail = place.detail;
}

type TabKey = 'profile' | 'addresses' | 'seller' | 'notifications';

const tabs: { key: TabKey; label: string }[] = [
  { key: 'profile', label: '基本资料' },
  { key: 'addresses', label: '收货地址' },
  { key: 'seller', label: '卖家入驻' },
  { key: 'notifications', label: '站内通知' },
];
const activeTab = computed<TabKey>({
  get: () =>
    tabs.some((tab) => tab.key === route.query.tab)
      ? (route.query.tab as TabKey)
      : 'profile',
  set: (tab) => {
    void router.replace({ query: { ...route.query, tab } });
  },
});
const currentTabTitle = computed(
  () =>
    ({
      profile: '个人资料',
      addresses: '我的地址簿',
      seller: '卖家入驻',
      notifications: '站内通知',
    })[activeTab.value],
);
const avatarSaving = ref(false),
  avatarError = ref(''),
  avatarDone = ref(false);
async function saveDefaultAvatar(avatar: (typeof defaultAvatars)[number]) {
  if (avatarSaving.value) return;
  const version = accountVersion;
  avatarSaving.value = true;
  avatarError.value = '';
  avatarDone.value = false;
  try {
    const response = await fetch(avatar.src, {
      credentials: 'omit',
      signal: AbortSignal.timeout(10000),
    });
    if (!response.ok) throw new Error('头像素材暂时无法加载，请稍后重试');
    const blob = await response.blob();
    if (!current(version)) return;
    const body = new FormData();
    body.append(
      'file',
      new File([blob], `${avatar.id}.jpg`, { type: 'image/jpeg' }),
    );
    const saved = await upload<{ avatarUrl: string }>('/me/avatar', body);
    if (!current(version)) return;
    if (auth.me) auth.me.avatarUrl = saved.avatarUrl;
    avatarDone.value = true;
  } catch (error) {
    if (current(version))
      avatarError.value =
        (error as Error).message || '头像保存失败，请稍后重试';
  } finally {
    if (current(version)) avatarSaving.value = false;
  }
}
async function saveAvatar(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file || avatarSaving.value) return;
  const version = accountVersion;
  avatarError.value = '';
  avatarDone.value = false;
  if (
    !['image/jpeg', 'image/png'].includes(file.type) ||
    file.size > 5 * 1024 * 1024
  ) {
    avatarError.value = '请选择不超过 5MB 的 JPG 或 PNG 图片';
    input.value = '';
    return;
  }
  avatarSaving.value = true;
  try {
    const body = new FormData();
    body.append('file', file);
    const saved = await upload<{ avatarUrl: string }>('/me/avatar', body);
    if (!current(version)) return;
    if (auth.me) auth.me.avatarUrl = saved.avatarUrl;
    avatarDone.value = true;
  } catch (error) {
    if (!current(version)) return;
    avatarError.value =
      (error as ApiError).message || '头像上传失败，请稍后重试';
  } finally {
    if (current(version)) avatarSaving.value = false;
    input.value = '';
  }
}

/* ---------- 资料 ---------- */

const nicknameForm = reactive({
  nickname: auth.me?.nickname ?? '',
  saving: false,
  error: '',
  done: false,
});

async function onSaveNickname() {
  if (nicknameForm.saving) return;
  const version = accountVersion;
  const nickname = nicknameForm.nickname.trim();
  if (!nickname) {
    nicknameForm.error = '昵称不能为空';
    nicknameForm.done = false;
    await nextTick();
    nicknameFormElement.value
      ?.querySelector<HTMLInputElement>('input')
      ?.focus();
    return;
  }
  nicknameForm.saving = true;
  nicknameForm.error = '';
  nicknameForm.done = false;
  try {
    const saved = await put<Me>('/me', { nickname });
    if (!current(version) || auth.me?.id !== saved.id) return;
    auth.me.nickname = saved.nickname;
    if (nicknameForm.nickname.trim() === nickname)
      nicknameForm.nickname = saved.nickname;
    nicknameForm.done = true;
  } catch (e) {
    if (!current(version)) return;
    nicknameForm.error = (e as ApiError).message || '保存失败，请稍后重试';
  } finally {
    if (current(version)) nicknameForm.saving = false;
  }
}

/* ---------- 收货地址 ---------- */

const addresses = ref<Address[]>([]);
const addressError = ref('');
const addressLoading = ref(false),
  addressDone = ref(''),
  deletingAddress = ref<number | null>(null);
let addressSequence = 0;
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
});

async function loadAddresses() {
  const version = accountVersion,
    sequence = ++addressSequence;
  addressError.value = '';
  addressLoading.value = true;
  try {
    const result = await get<Address[]>('/me/addresses');
    if (current(version) && sequence === addressSequence)
      addresses.value = result;
  } catch (e) {
    if (current(version) && sequence === addressSequence)
      addressError.value = (e as ApiError).message || '地址加载失败';
  } finally {
    if (current(version) && sequence === addressSequence)
      addressLoading.value = false;
  }
}

function startAddAddress() {
  if (addressForm.saving || deletingAddress.value !== null) return;
  addressDone.value = '';
  Object.assign(addressForm, {
    visible: true,
    id: 0,
    receiver: '',
    phone: '',
    region: '',
    detail: '',
    isDefault: false,
    error: '',
  });
  void nextTick(() =>
    addressFormElement.value?.querySelector<HTMLInputElement>('input')?.focus(),
  );
}

function startEditAddress(addr: Address) {
  if (addressForm.saving || deletingAddress.value !== null) return;
  addressDone.value = '';
  Object.assign(addressForm, {
    visible: true,
    id: addr.id,
    receiver: addr.receiver,
    phone: addr.phone,
    region: addr.region,
    detail: addr.detail,
    isDefault: addr.isDefault,
    error: '',
  });
  void nextTick(() =>
    addressFormElement.value?.querySelector<HTMLInputElement>('input')?.focus(),
  );
}

async function onSaveAddress() {
  if (addressForm.saving || deletingAddress.value !== null) return;
  const version = accountVersion;
  const payload = {
    receiver: addressForm.receiver.trim(),
    phone: addressForm.phone.trim(),
    region: addressForm.region.trim(),
    detail: addressForm.detail.trim(),
    isDefault: addressForm.isDefault,
  };
  if (
    !payload.receiver ||
    !payload.phone ||
    !payload.region ||
    !payload.detail
  ) {
    addressForm.error = '请完整填写收货人、手机号、地区与详细地址';
    return;
  }
  addressForm.saving = true;
  addressForm.error = '';
  addressDone.value = '';
  try {
    if (addressForm.id) {
      await put(`/me/addresses/${addressForm.id}`, payload);
    } else {
      await post('/me/addresses', payload);
    }
    if (!current(version)) return;
    addressForm.visible = false;
    addressDone.value = '收货地址已保存';
    await loadAddresses();
  } catch (e) {
    if (!current(version)) return;
    addressForm.error = (e as ApiError).message || '保存失败，请稍后重试';
  } finally {
    if (current(version)) addressForm.saving = false;
  }
}

async function onDeleteAddress(addr: Address) {
  if (addressForm.saving || deletingAddress.value !== null) return;
  const version = accountVersion;
  deletingAddress.value = addr.id;
  addressDone.value = '';
  try {
    const accepted = await askConfirmation(
      '确定删除收货地址「' + addr.receiver + ' ' + addr.region + '」吗？',
    );
    if (!accepted || !current(version)) return;
    await del('/me/addresses/' + addr.id);
    if (!current(version)) return;
    addressDone.value = '收货地址已删除';
    await loadAddresses();
  } catch (e) {
    if (current(version))
      addressError.value = (e as ApiError).message || '删除失败，请稍后重试';
  } finally {
    if (current(version)) deletingAddress.value = null;
  }
}

/* ---------- 卖家入驻 ---------- */

const sellerApp = ref<SellerApplication | null>(null);
const sellerLoading = ref(false),
  sellerLoadError = ref(''),
  sellerDone = ref(false);
const sellerForm = reactive({ intro: '', submitting: false, error: '' });

const sellerStatusTone = computed(() => {
  switch (sellerApp.value?.status) {
    case 'APPROVED':
      return 'success';
    case 'PENDING':
      return 'warning';
    case 'SUPPLEMENT':
      return 'info';
    case 'REJECTED':
    case 'SUSPENDED':
      return 'danger';
    default:
      return 'neutral';
  }
});

const channelStatusTone = computed(() => {
  switch (sellerApp.value?.channelStatus) {
    case 'QUALIFIED':
      return 'success';
    case 'PENDING':
      return 'warning';
    case 'REJECTED':
      return 'danger';
    default:
      return 'neutral';
  }
});

async function loadSellerApp() {
  const version = accountVersion;
  sellerLoading.value = true;
  sellerLoadError.value = '';
  try {
    const result = await get<SellerApplication | null>(
      '/me/seller-application',
    );
    if (current(version)) sellerApp.value = result?.id ? result : null;
  } catch (e) {
    if (current(version))
      sellerLoadError.value = (e as ApiError).message || '入驻状态加载失败';
  } finally {
    if (current(version)) sellerLoading.value = false;
  }
}

async function onApplySeller() {
  if (sellerForm.submitting) return;
  const version = accountVersion;
  sellerForm.submitting = true;
  sellerForm.error = '';
  sellerDone.value = false;
  try {
    const result = await post<SellerApplication>('/me/seller-application', {
      intro: sellerForm.intro.trim(),
    });
    if (!current(version)) return;
    sellerApp.value = result;
    sellerForm.intro = '';
    sellerDone.value = true;
    if (auth.me) auth.me.sellerStatus = result.status;
  } catch (e) {
    if (!current(version)) return;
    sellerForm.error = (e as ApiError).message || '提交失败，请稍后重试';
  } finally {
    if (current(version)) sellerForm.submitting = false;
  }
}

/* ---------- 站内通知 ---------- */

const NOTIFICATION_PAGE_SIZE = 10;
const notifications = ref<Notification[]>([]);
const notificationPage = ref(0);
const notificationTotalPages = ref(1);
const notificationLoading = ref(false);
const notificationError = ref('');
const unreadCount = ref(0);
const markingAll = ref(false);
const markingIds = ref<number[]>([]);
let notificationSequence = 0;

async function loadUnreadCount() {
  const version = accountVersion;
  try {
    const data = await get<{ count: number }>('/me/notifications/unread-count');
    if (current(version)) unreadCount.value = data.count;
  } catch {
    /* 未读数失败不阻塞列表 */
  }
}

async function loadNotifications() {
  const version = accountVersion,
    sequence = ++notificationSequence;
  notificationLoading.value = true;
  notificationError.value = '';
  try {
    const data = await get<Page<Notification>>('/me/notifications', {
      page: notificationPage.value,
      size: NOTIFICATION_PAGE_SIZE,
    });
    if (!current(version) || sequence !== notificationSequence) return;
    notifications.value = data.content;
    notificationTotalPages.value = Math.max(1, data.totalPages);
  } catch (e) {
    if (current(version) && sequence === notificationSequence)
      notificationError.value = (e as ApiError).message || '通知加载失败';
  } finally {
    if (current(version) && sequence === notificationSequence)
      notificationLoading.value = false;
  }
}

function onNotificationPage(next: number) {
  notificationPage.value = next;
  loadNotifications();
}

async function onMarkRead(id: number) {
  if (markingAll.value || markingIds.value.includes(id)) return;
  const version = accountVersion;
  markingIds.value.push(id);
  try {
    await post('/me/notifications/read', { ids: [id] });
    if (!current(version)) return;
    const item = notifications.value.find((n) => n.id === id);
    if (item) item.read = true;
    unreadCount.value = Math.max(0, unreadCount.value - 1);
  } catch (e) {
    if (current(version))
      notificationError.value = (e as ApiError).message || '操作失败';
  } finally {
    if (current(version))
      markingIds.value = markingIds.value.filter((item) => item !== id);
  }
}

async function onMarkAllRead() {
  if (markingAll.value || markingIds.value.length > 0) return;
  const version = accountVersion;
  const ids = notifications.value.filter((n) => !n.read).map((n) => n.id);
  if (ids.length === 0) return;
  markingAll.value = true;
  try {
    await post('/me/notifications/read', { ids });
    if (!current(version)) return;
    await Promise.all([loadNotifications(), loadUnreadCount()]);
  } catch (e) {
    if (current(version))
      notificationError.value = (e as ApiError).message || '操作失败';
  } finally {
    if (current(version)) markingAll.value = false;
  }
}

watch(
  () => auth.me?.id,
  (id) => {
    accountVersion++;
    addresses.value = [];
    notifications.value = [];
    sellerApp.value = null;
    avatarDone.value = false;
    avatarError.value = '';
    avatarSaving.value = false;
    Object.assign(nicknameForm, {
      nickname: auth.me?.nickname ?? '',
      error: '',
      done: false,
      saving: false,
    });
    Object.assign(addressForm, {
      visible: false,
      id: 0,
      receiver: '',
      phone: '',
      region: '',
      detail: '',
      isDefault: false,
      saving: false,
      error: '',
    });
    Object.assign(sellerForm, { intro: '', submitting: false, error: '' });
    addressDone.value = '';
    addressError.value = '';
    deletingAddress.value = null;
    notificationPage.value = 0;
    notificationTotalPages.value = 1;
    unreadCount.value = 0;
    notificationError.value = '';
    markingAll.value = false;
    markingIds.value = [];
    sellerDone.value = false;
    sellerLoadError.value = '';
    if (id) {
      void loadAddresses();
      void loadSellerApp();
      void loadNotifications();
      void loadUnreadCount();
    }
  },
  { immediate: true, flush: 'sync' },
);
onUnmounted(() => {
  disposed = true;
  accountVersion++;
});
</script>

<style scoped>
.mm-account {
  width: 100%;
  max-width: 1040px;
  margin: 0 auto;
  padding: 32px 24px;
  display: grid;
  gap: 24px;
}
.mm-account__identity {
  display: flex;
  gap: 22px;
  align-items: center;
  padding: 26px;
  background: #f4ecdf;
  border: 1px solid #e2d4be;
  border-radius: 16px;
  border-top: 4px solid #a0825a;
}
.mm-account__identity > div:nth-child(2) {
  min-width: 0;
}
.mm-account__portrait {
  width: 88px;
  height: 88px;
  flex-shrink: 0;
  border-radius: 50%;
  background: #705737;
  border: 5px solid #fffaf2;
  box-shadow: 0 3px 10px #54402815;
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
  letter-spacing: 0.1em;
  color: var(--mm-muted);
  margin-bottom: 6px;
  overflow-wrap: anywhere;
}
.mm-account__heading {
  font-size: 28px;
  margin-bottom: 8px;
}
.mm-account__orders {
  margin-left: auto;
  white-space: nowrap;
  font-size: 14px;
  background: white;
  border: 1px solid #d5c5ac;
  border-radius: 8px;
  padding: 10px 14px;
  color: #634f34;
}
.mm-account__tabs {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  border: 1px solid #ded5c7;
  background: #eee8dd;
  border-radius: 11px;
  padding: 5px;
  gap: 4px;
}
.mm-account__tab {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 6px;
  padding: 14px 8px;
  min-height: 48px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: var(--mm-muted);
  font-size: 15px;
}
.mm-account__tab.is-active {
  color: #63492f;
  background: white;
  box-shadow: 0 2px 5px #3e2d1a12;
  font-weight: 700;
}
.mm-account__tab:focus-visible {
  outline: 2px solid var(--mm-primary);
  outline-offset: -2px;
}
.mm-account__badge {
  min-width: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: var(--mm-danger);
  color: var(--mm-white);
  font-size: 11px;
  text-align: center;
}
.mm-account :deep(.mm-card) {
  box-shadow: none;
  border-radius: 10px;
}
.mm-account :deep(.mm-card__header) {
  padding: 18px 28px;
  gap: 16px;
  flex-wrap: wrap;
}
.mm-account :deep(.mm-card__body) {
  padding: 24px 28px 28px;
}
.mm-account__profile {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.25fr);
  gap: 32px;
}
.mm-account__avatar-settings {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 14px;
  padding-right: 32px;
  border-right: 1px solid var(--mm-border);
}
.mm-account__avatar-settings h3 {
  font-size: 18px;
  margin-bottom: 8px;
}
.mm-account__avatar-presets {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  width: 100%;
  max-width: 320px;
}
.mm-account__avatar-presets button {
  display: grid;
  justify-items: center;
  gap: 7px;
  padding: 9px 4px;
  border: 1px solid var(--mm-border);
  border-radius: 12px;
  background: var(--mm-white);
  font-size: 12px;
  color: var(--mm-ink);
  cursor: pointer;
}
.mm-account__avatar-presets img {
  width: 52px;
  height: 52px;
  max-width: 100%;
  object-fit: cover;
  border-radius: 50%;
}
.mm-account__avatar-presets button:hover:not(:disabled),
.mm-account__avatar-presets button:focus-visible {
  outline: 2px solid var(--mm-primary);
  outline-offset: 2px;
  background: var(--mm-canvas);
}
.mm-account__avatar-presets button:disabled {
  opacity: 0.55;
  cursor: wait;
}
.mm-account__avatar-upload {
  width: max-content;
  max-width: 100%;
  min-height: 44px;
  position: relative;
  padding: 10px 16px;
  border: 1px solid var(--mm-border);
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 600;
  overflow: hidden;
  background: var(--mm-canvas);
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
.mm-account__profile-details {
  min-width: 0;
}
.mm-account__email {
  display: grid;
  gap: 6px;
  padding: 16px 18px;
  background: #f7f8f5;
  border: 1px solid var(--mm-border);
  border-radius: 9px;
  font-size: 14px;
  overflow-wrap: anywhere;
}
.mm-account__email > span {
  color: var(--mm-muted);
  font-size: 12px;
}
.mm-account__email > strong {
  font-weight: 600;
}
.mm-account__email > p {
  color: var(--mm-muted);
  font-size: 13px;
}
.mm-account__form,
.mm-account__address-fields {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}
.mm-account__form {
  margin-top: 20px;
}
.mm-account__profile-details .mm-button {
  align-self: flex-start;
  min-width: 128px;
}
.mm-account__address-fields {
  border: 0;
  padding: 0;
  margin: 0;
}
.mm-account__form-actions,
.mm-account__address-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.mm-account__meta {
  font-size: 14px;
  color: var(--mm-muted);
  line-height: 1.8;
  overflow-wrap: anywhere;
}
.mm-account__error {
  color: var(--mm-danger);
  font-size: 14px;
  line-height: 1.6;
}
.mm-account__error button {
  margin-left: 8px;
  text-decoration: underline;
  background: none;
  border: 0;
  color: inherit;
  padding: 4px;
}
.mm-account__ok {
  color: var(--mm-success);
  font-size: 14px;
  line-height: 1.6;
}
.mm-account__subheading {
  font-size: 17px;
  font-weight: 600;
}
.mm-account__address-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.mm-account__address {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 20px;
  padding: 20px 0;
  border-bottom: 1px solid var(--mm-border);
}
.mm-account__address:first-child {
  padding-top: 0;
}
.mm-account__address > div:first-child {
  min-width: 0;
}
.mm-account__address strong {
  display: inline-block;
  margin-right: 8px;
}
.mm-account__address-actions {
  flex-shrink: 0;
}
.mm-account__address-form {
  border-top: 1px solid var(--mm-border);
  padding-top: 24px;
}
.mm-account__checkbox {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  min-height: 36px;
}
.mm-account__seller-status {
  display: grid;
  gap: 14px;
}
.mm-account__field {
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
}
.mm-account__field textarea {
  padding: 12px;
  border: 1px solid var(--mm-border);
  border-radius: 6px;
  resize: vertical;
  font: inherit;
  font-weight: 400;
}
.mm-account__notification-list {
  display: grid;
}
.mm-account__notification {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 20px;
  padding: 20px 16px;
  border-bottom: 1px solid var(--mm-border);
}
.mm-account__notification > div {
  min-width: 0;
}
.mm-account__notification.is-unread {
  background: var(--mm-canvas);
}
.mm-account__notification-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  overflow-wrap: anywhere;
}
.mm-account__unread-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--mm-primary);
  flex-shrink: 0;
}
.mm-account__notification-content {
  font-size: 14px;
  margin: 8px 0;
  line-height: 1.8;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.mm-account__privacy {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 16px;
  font-size: 13px;
  color: var(--mm-muted);
}
@media (max-width: 700px) {
  .mm-account {
    padding: 24px 16px;
    gap: 20px;
  }
  .mm-account__identity {
    flex-wrap: wrap;
    gap: 14px;
    padding: 18px 16px;
  }
  .mm-account__portrait {
    width: 64px;
    height: 64px;
    font-size: 26px;
  }
  .mm-account__identity > div:nth-child(2) {
    flex: 1;
  }
  .mm-account__heading {
    font-size: 25px;
  }
  .mm-account__orders {
    margin: 0;
    flex-basis: 100%;
    text-align: center;
  }
  .mm-account__tabs {
    gap: 0;
  }
  .mm-account__tab {
    font-size: 13px;
    padding: 12px 2px;
    flex-wrap: wrap;
    gap: 4px;
  }
  .mm-account :deep(.mm-card__header) {
    padding: 16px 20px;
  }
  .mm-account :deep(.mm-card__body) {
    padding: 20px;
  }
  .mm-account__profile {
    grid-template-columns: minmax(0, 1fr);
    gap: 24px;
  }
  .mm-account__avatar-settings {
    border-right: 0;
    border-bottom: 1px solid var(--mm-border);
    padding: 0 0 24px;
  }
  .mm-account__address,
  .mm-account__notification {
    flex-direction: column;
    gap: 14px;
  }
  .mm-account__profile-details .mm-button {
    align-self: stretch;
  }
}
</style>
