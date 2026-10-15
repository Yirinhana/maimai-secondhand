<script setup lang="ts">
import GovernanceNotice from '../../shared/components/GovernanceNotice.vue';
import UserAvatar from '../../shared/components/UserAvatar.vue';
import { askConfirmation } from '../../shared/confirm';
import { onMounted, nextTick, ref } from 'vue';
import { useRoute } from 'vue-router';
import { get, post, put, del, type ApiError } from '../../shared/api';
import { useAuthStore } from '../../shared/stores/auth';
import { formatTime } from '../../shared/format';
import { moderationText, type CommunityPage, type DemandReply } from './types';
import MmButton from '../../shared/components/MmButton.vue';
import MmPagination from '../../shared/components/MmPagination.vue';
import ReportButton from './ReportButton.vue';
const route = useRoute();
const props = defineProps<{ demandId: number; closed: boolean }>(),
  auth = useAuthStore(),
  items = ref<DemandReply[]>([]),
  mine = ref<DemandReply[]>([]),
  page = ref(0),
  pages = ref(0),
  content = ref(''),
  editing = ref<number | null>(null),
  error = ref(''),
  hint = ref(''),
  busy = ref(false);
async function load() {
  try {
    const r = await get<CommunityPage<DemandReply>>(
      `/community/demands/${props.demandId}/replies`,
      { page: page.value, size: 15 },
    );
    items.value = r.items;
    const target = String(route.query.replyId ?? '');
    if (
      /^\d+$/.test(target) &&
      !r.items.some((item) => item.id === Number(target))
    ) {
      const linked = await get<DemandReply>(
        `/community/demands/${props.demandId}/replies/${target}`,
      );
      items.value = [linked, ...r.items];
    }
    pages.value = r.totalPages;
    if (auth.me) {
      const own = await get<CommunityPage<DemandReply>>(
        '/community/replies/me',
        { size: 50 },
      );
      mine.value = own.items.filter(
        (i) =>
          i.demandId === props.demandId &&
          !items.value.some((p) => p.id === i.id),
      );
    }
    if (target) {
      await nextTick();
      document
        .getElementById(`demand-reply-${target}`)
        ?.scrollIntoView({ block: 'center' });
    }
  } catch (e) {
    error.value = (e as ApiError).message;
  }
}
function edit(r: DemandReply) {
  editing.value = r.id;
  content.value = r.content;
}
async function submit() {
  busy.value = true;
  error.value = '';
  try {
    if (editing.value)
      await put(`/community/replies/${editing.value}`, {
        content: content.value,
      });
    else
      await post(`/community/demands/${props.demandId}/replies`, {
        content: content.value,
      });
    content.value = '';
    editing.value = null;
    hint.value = '回复已提交，审核通过后公开。';
    await load();
  } catch (e) {
    error.value = (e as ApiError).message;
  } finally {
    busy.value = false;
  }
}
async function remove(id: number) {
  if (!(await askConfirmation('确认删除自己的回复？'))) return;
  busy.value = true;
  try {
    await del(`/community/replies/${id}`);
    await load();
  } catch (e) {
    error.value = (e as ApiError).message;
  } finally {
    busy.value = false;
  }
}
onMounted(load);
</script>
<template>
  <section class="mm-stack demand-replies">
    <header class="demand-replies__heading">
      <h3>交流与回复</h3>
      <p>补充物品情况，或向发布者确认具体要求。</p>
    </header>
    <p v-if="error" class="mm-error" role="alert">{{ error }}</p>
    <article
      v-for="r in [...items, ...mine]"
      :key="r.id"
      :id="`demand-reply-${r.id}`"
      class="demand-replies__item"
    >
      <div class="demand-reply-author">
        <UserAvatar
          :src="r.authorAvatarUrl"
          :nickname="r.authorNickname"
          :size="30"
        /><RouterLink :to="`/sellers/${r.authorId}`">{{
          r.authorNickname
        }}</RouterLink>
      </div>
      <p style="white-space: pre-wrap">{{ r.content }}</p>
      <p class="mm-muted">
        {{ formatTime(r.createdAt) }} ·
        {{ moderationText[r.status] || r.status }}
      </p>
      <p
        v-if="
          r.reviewReason &&
          r.authorId === auth.me?.id &&
          r.status !== 'PUBLISHED'
        "
        class="mm-muted"
      >
        审核说明：{{ r.reviewReason }}
      </p>
      <div v-if="auth.me?.id === r.authorId" class="mm-actions">
        <MmButton variant="ghost" :disabled="busy" @click="edit(r)"
          >编辑</MmButton
        ><MmButton variant="ghost" :disabled="busy" @click="remove(r.id)"
          >删除</MmButton
        >
      </div>
      <ReportButton
        v-else
        class="demand-replies__report"
        resource-type="DEMAND_REPLY"
        :resource-id="r.id"
      />
    </article>
    <MmPagination
      :page="page"
      :total-pages="pages"
      @change="
        page = $event;
        load();
      "
    />
    <form
      v-if="auth.me && !closed"
      class="mm-form demand-replies__compose"
      @submit.prevent="submit"
    >
      <label
        >{{ editing ? '编辑回复' : '我能提供'
        }}<textarea
          v-model="content"
          required
          maxlength="800"
          placeholder="介绍你拥有的闲置或补充需求信息，请勿公开联系方式和私人地址"
        />
      </label>
      <div class="mm-actions">
        <MmButton type="submit" :loading="busy">提交审核</MmButton
        ><MmButton
          v-if="editing"
          variant="ghost"
          @click="
            editing = null;
            content = '';
          "
          >取消编辑</MmButton
        >
      </div>
      <p v-if="hint" class="mm-muted" role="status">{{ hint }}</p>
      <GovernanceNotice />
    </form>
    <RouterLink v-else-if="!auth.me" to="/login">登录后回复</RouterLink>
  </section>
</template>

<style scoped>
.demand-replies {
  padding-top: 20px;
  border-top: 1px solid var(--mm-border);
  gap: 0;
}
.demand-replies__heading {
  margin-bottom: 12px;
}
.demand-replies__heading h3 {
  font-size: 17px;
}
.demand-replies__heading p {
  margin-top: 4px;
  color: var(--mm-muted);
  font-size: 13px;
}
.demand-replies__item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 10px;
  padding: 18px 0;
  border-bottom: 1px solid var(--mm-border);
  min-width: 0;
  overflow-wrap: anywhere;
}
.demand-reply-author {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  font-weight: 650;
}
.demand-replies__item > p {
  line-height: 1.85;
  font-size: 14px;
}
.demand-replies__report {
  align-self: stretch;
}
.demand-replies__report :deep(> .mm-button) {
  border-color: transparent;
  background: transparent;
  color: var(--mm-muted);
  font-weight: 500;
  padding-inline: 8px;
}
.demand-replies__report :deep(form) {
  max-width: none;
  width: 100%;
}
.demand-replies__compose {
  width: 100%;
  max-width: none;
  padding: 20px;
  margin-top: 20px;
  background: var(--mm-canvas);
  border-radius: 12px;
  border: 1px solid var(--mm-border);
}
.demand-replies__compose textarea {
  min-height: 110px;
}
@media (max-width: 760px) {
  .demand-replies__compose {
    padding: 14px;
  }
}
</style>
