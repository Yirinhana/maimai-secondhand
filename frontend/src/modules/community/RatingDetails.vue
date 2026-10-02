<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { get, post } from '../../shared/api';
import { formatTime } from '../../shared/format';
const props = defineProps<{ id: number; canFollowup?: boolean }>();
interface Details {
  description: number | null;
  communication: number | null;
  fulfillment: number | null;
  followup: string | null;
  followedUpAt: string | null;
  images: string[];
}
const data = ref<Details>(),
  expanded = ref(false),
  text = ref(''),
  error = ref(''),
  busy = ref(false);
async function load() {
  try {
    data.value = await get(`/community/ratings/${props.id}/details`);
  } catch (e) {
    error.value = (e as Error).message;
  }
}
async function submit() {
  busy.value = true;
  error.value = '';
  try {
    data.value = await post(`/community/ratings/${props.id}/followup`, {
      text: text.value,
    });
    expanded.value = false;
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    busy.value = false;
  }
}
onMounted(load);
</script>
<template>
  <div class="rating-details">
    <template v-if="data"
      ><dl v-if="data.description || data.communication || data.fulfillment">
        <div
          v-for="(value, key) in {
            描述相符: data.description,
            沟通体验: data.communication,
            履约情况: data.fulfillment,
          }"
          :key="key"
        >
          <dt>{{ key }}</dt>
          <dd>{{ value ? `${value} / 5` : '未分项评分' }}</dd>
        </div>
      </dl>
      <div v-if="data.images.length" class="rating-images">
        <a
          v-for="(url, i) in data.images"
          :key="url"
          :href="url"
          target="_blank"
          rel="noopener"
          ><img
            :src="url"
            :alt="`评价实拍图 ${i + 1}，点击查看大图`"
            loading="lazy"
            width="112"
            height="112"
        /></a>
      </div>
      <blockquote v-if="data.followup">
        <strong>追加评价</strong
        ><time v-if="data.followedUpAt">{{
          formatTime(data.followedUpAt)
        }}</time>
        <p>{{ data.followup }}</p>
      </blockquote>
      <button v-else-if="canFollowup" @click="expanded = !expanded">
        {{ expanded ? '收起追评' : '补充使用体验' }}
      </button>
      <form
        v-if="canFollowup && expanded && !data.followup"
        @submit.prevent="submit"
      >
        <label
          >追加评价（每条可补充一次）<textarea
            v-model="text"
            required
            maxlength="500"
          /></label
        ><button :disabled="busy || !text.trim()">保存追评</button>
      </form></template
    >
    <p v-if="error" class="mm-error" role="alert">
      {{ error }} <button @click="load">重试</button>
    </p>
  </div>
</template>
<style scoped>
.rating-details {
  margin: 10px 0;
  font-size: 13px;
}
.rating-details dl {
  display: flex;
  gap: 10px 24px;
  flex-wrap: wrap;
}
.rating-details dl > div {
  display: flex;
  gap: 8px;
}
.rating-details dt {
  color: var(--mm-muted);
}
.rating-details dd {
  margin: 0;
}
.rating-images {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
.rating-images img {
  object-fit: cover;
  border-radius: 10px;
  max-width: 25vw;
}
.rating-details blockquote {
  margin: 14px 0;
  padding: 14px;
  background: var(--mm-bg, #f8f8f6);
  border-left: 3px solid var(--mm-primary);
}
.rating-details time {
  margin-left: 12px;
  color: var(--mm-muted);
  font-size: 12px;
}
.rating-details p {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.rating-details label {
  display: grid;
  gap: 6px;
}
.rating-details textarea {
  min-height: 90px;
  max-width: 100%;
  padding: 10px;
}
.rating-details button {
  min-height: 40px;
}
</style>
