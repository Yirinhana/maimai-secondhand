<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';
import { post } from '../../shared/api';
import type { TinaTurn } from './useTinaChat';
import { plainReply } from './plainText';
const props = defineProps<{ turns: TinaTurn[] }>(),
  emit = defineEmits<{ done: []; cancel: [] }>(),
  router = useRouter();
const selected = ref<string[]>([]),
  title = ref(''),
  body = ref(''),
  orderNo = ref(''),
  busy = ref(false),
  error = ref(''),
  preview = ref(false);
const available = computed(() =>
  props.turns.filter((t) => t.status === 'COMPLETE' && t.id > 0).slice(-10),
);
const chosen = computed(() =>
  available.value.filter((t) => selected.value.includes(t.requestId)),
);
async function submit() {
  busy.value = true;
  error.value = '';
  try {
    const result = await post<{ id: number }>('/support/tickets', {
      title: title.value,
      body: body.value,
      orderNo: orderNo.value.trim() || null,
      aiRequestIds: chosen.value.map((t) => t.requestId),
    });
    emit('done');
    await router.push(`/support/tickets/${result.id}`);
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <section class="handoff">
    <h3>把问题交给人工客服</h3>
    <p>
      先写下希望解决的问题，选择要转交的问答。只有确认后，客服才能看到这些内容。
    </p>
    <form v-if="!preview" @submit.prevent="preview = true">
      <label>问题标题<input v-model="title" required maxlength="100" /></label
      ><label
        >希望客服协助什么<textarea
          v-model="body"
          required
          maxlength="2000"
          rows="3"
        /></label
      ><label
        >关联订单号（可选）<input v-model="orderNo" maxlength="32"
      /></label>
      <fieldset>
        <legend>附上与麦仔的问答（最多5段）</legend>
        <label
          v-for="turn in available"
          :key="turn.requestId"
          class="handoff-choice"
          ><input
            v-model="selected"
            :value="turn.requestId"
            type="checkbox"
            :disabled="
              selected.length >= 5 && !selected.includes(turn.requestId)
            "
          /><span>{{ turn.question }}</span></label
        >
        <p v-if="!available.length">暂无已完成问答，可以直接提交问题。</p>
      </fieldset>
      <button type="submit">预览交接内容</button
      ><button type="button" @click="emit('cancel')">返回对话</button>
    </form>
    <div v-else>
      <h4>{{ title }}</h4>
      <p>{{ body }}</p>
      <p v-if="orderNo">订单：{{ orderNo }}</p>
      <article v-for="turn in chosen" :key="turn.requestId">
        <strong>我的提问</strong>
        <p>{{ turn.question }}</p>
        <strong>麦仔当时的回复</strong>
        <p>{{ plainReply(turn.answer || '') }}</p>
      </article>
      <p class="mm-muted">
        附上 {{ chosen.length }} 段问答。提交后可在工单中查看人工处理进度。
      </p>
      <button :disabled="busy" @click="submit">
        {{ busy ? '正在提交…' : '确认转交人工' }}</button
      ><button :disabled="busy" @click="preview = false">返回修改</button>
    </div>
    <p v-if="error" role="alert" class="mm-error">{{ error }}</p>
  </section>
</template>
<style scoped>
.handoff {
  padding: 18px;
  overflow-y: auto;
  min-height: 0;
  flex: 1;
}
.handoff p {
  line-height: 1.7;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.handoff form {
  display: grid;
  gap: 12px;
}
.handoff label {
  display: grid;
  gap: 7px;
}
.handoff input:not([type='checkbox']),
.handoff textarea {
  padding: 10px;
  border: 1px solid var(--mm-border);
  border-radius: 8px;
  font: inherit;
  min-width: 0;
  width: 100%;
  box-sizing: border-box;
}
.handoff fieldset {
  border: 1px solid var(--mm-border);
  border-radius: 8px;
  min-width: 0;
  padding: 12px;
}
.handoff .handoff-choice {
  display: flex;
  gap: 8px;
  padding: 8px 0;
  font-size: 13px;
  align-items: start;
}
.handoff-choice span {
  overflow-wrap: anywhere;
}
.handoff article {
  background: #f6f7f3;
  border-radius: 8px;
  padding: 12px;
  margin: 12px 0;
}
.handoff button {
  padding: 10px 14px;
  margin: 4px;
  border-radius: 8px;
  border: 1px solid var(--mm-border);
  background: white;
  color: var(--mm-ink);
}
</style>
