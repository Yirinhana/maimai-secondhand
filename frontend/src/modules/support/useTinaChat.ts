import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { del, get, post, type ApiError } from '../../shared/api';
import { useAuthStore } from '../../shared/stores/auth';
import type { Faq } from './types';

export interface TinaTurn {
  id: number;
  requestId: string;
  question: string;
  answer: string | null;
  status: 'PENDING' | 'COMPLETE' | 'FAILED';
  errorCode: string | null;
  createdAt: string;
}
interface Assistant {
  name: string;
  enabled: boolean;
  maxMessageChars: number;
  notice: string;
}
export function useTinaChat() {
  const auth = useAuthStore();
  const assistant = ref<Assistant | null>(null),
    faqs = ref<Faq[]>([]),
    turns = ref<TinaTurn[]>([]);
  const loading = ref(false),
    sending = ref(false),
    error = ref(''),
    draft = ref('');
  const pendingRequest = ref<{ requestId: string; message: string } | null>(
    null,
  );
  let generation = 0,
    loadGeneration = 0;
  const enabled = computed(
    () => !!assistant.value?.enabled && !!auth.me && !loading.value,
  );
  const pending = computed(() =>
    turns.value.some((turn) => turn.status === 'PENDING'),
  );
  async function load() {
    if (sending.value) return;
    const run = ++loadGeneration,
      account = generation;
    loading.value = true;
    error.value = '';
    const owner = auth.me?.id;
    const results = await Promise.allSettled([
      get<Assistant>('/support/assistant'),
      get<Faq[]>('/support/faq'),
      owner
        ? get<TinaTurn[]>('/support/chat')
        : Promise.resolve([] as TinaTurn[]),
    ]);
    if (run !== loadGeneration || account !== generation) return;
    if (results[0].status === 'fulfilled') assistant.value = results[0].value;
    else {
      assistant.value = null;
      error.value = '客服状态暂时读取失败，请重试。';
    }
    if (results[1].status === 'fulfilled') faqs.value = results[1].value;
    if (results[2].status === 'fulfilled') turns.value = results[2].value;
    else error.value = '对话记录暂时读取失败，请重试。';
    loading.value = false;
  }
  async function send(retry?: TinaTurn) {
    if (sending.value || !enabled.value || pending.value) return;
    const message = retry?.question ?? draft.value.trim();
    if (!message || message.length > 1000) {
      error.value = '请输入1到1000字的问题。';
      return;
    }
    const request = retry
      ? { requestId: retry.requestId, message }
      : pendingRequest.value?.message === message
        ? pendingRequest.value
        : { requestId: crypto.randomUUID(), message };
    pendingRequest.value = request;
    const account = generation;
    sending.value = true;
    error.value = '';
    try {
      const turn = await post<TinaTurn>('/support/chat', request);
      if (account !== generation) return;
      const index = turns.value.findIndex(
        (item) => item.requestId === turn.requestId,
      );
      if (index < 0) turns.value.push(turn);
      else turns.value[index] = turn;
      turns.value = turns.value.slice(-20);
      if (!retry) draft.value = '';
      pendingRequest.value = null;
    } catch (cause) {
      if (account !== generation) return;
      error.value = (cause as ApiError).message || '消息发送失败，可以重试。';
      // A timed-out request may have reached the server. Retrying keeps its ID.
    } finally {
      if (account === generation) sending.value = false;
    }
  }
  async function clear() {
    const account = generation;
    if (sending.value || loading.value) return;
    sending.value = true;
    error.value = '';
    try {
      await del('/support/chat');
      if (account !== generation) return;
      turns.value = [];
      draft.value = '';
      pendingRequest.value = null;
    } catch (cause) {
      if (account === generation)
        error.value = (cause as ApiError).message || '暂时无法清空对话。';
    } finally {
      if (account === generation) sending.value = false;
    }
  }
  watch(
    () => auth.me?.id,
    () => {
      generation++;
      loadGeneration++;
      turns.value = [];
      draft.value = '';
      error.value = '';
      loading.value = false;
      sending.value = false;
      pendingRequest.value = null;
    },
    { flush: 'sync' },
  );
  onBeforeUnmount(() => {
    generation++;
    loadGeneration++;
  });
  return {
    auth,
    assistant,
    faqs,
    turns,
    loading,
    sending,
    error,
    draft,
    enabled,
    pending,
    load,
    send,
    clear,
  };
}
