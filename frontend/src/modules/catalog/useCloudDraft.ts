import { ref, watch, onBeforeUnmount, type Ref } from 'vue';
import { onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router';
import { get, put, type ApiError } from '../../shared/api';
interface CloudDraft {
  version: number;
  payload: unknown;
  updatedAt: string | null;
}
/** Local persistence remains the immediate fallback; remote writes always use a known version. */
export function useCloudDraft(
  key: () => string,
  source: Ref<string>,
  restore: (payload: unknown) => void,
  ownerId: number,
) {
  const status = ref('正在连接云端草稿…'),
    conflict = ref<CloudDraft | null>(null),
    ready = ref(false),
    busy = ref(false);
  let version = 0,
    baseline = '',
    timer: ReturnType<typeof setTimeout> | undefined,
    disposed = false,
    inFlight: Promise<boolean> | undefined,
    epoch = 0,
    activeKey = key();
  const path = () => `/me/listing-drafts/${activeKey}?ownerId=${ownerId}`;
  async function initialize(hasLocal: boolean) {
    ready.value = false;
    clearTimeout(timer);
    if (inFlight) await inFlight;
    const generation = ++epoch;
    activeKey = key();
    conflict.value = null;
    try {
      const data = await get<CloudDraft>(path());
      if (disposed || generation !== epoch) return;
      version = data.version;
      if (
        data.payload &&
        hasLocal &&
        JSON.stringify(data.payload) !== source.value
      ) {
        conflict.value = data;
        status.value = '本机与云端内容不同，请选择要继续编辑的版本。';
        return;
      }
      if (data.payload && !hasLocal) restore(data.payload);
      baseline = data.payload || !hasLocal ? source.value : '';
      ready.value = true;
      status.value = data.payload
        ? '已恢复云端草稿'
        : '云端已连接，填写后自动同步';
      if (hasLocal) void flush();
    } catch (e) {
      status.value = `暂未连接云端：${(e as ApiError).message}。本机仍会保留文字。`;
    }
  }
  async function flush(): Promise<boolean> {
    clearTimeout(timer);
    if (disposed || !ready.value || conflict.value) return false;
    if (inFlight) {
      await inFlight;
      if (disposed) return false;
      return flush();
    }
    const snapshot = source.value;
    if (snapshot === baseline) return true;
    const generation = epoch;
    const endpoint = path();
    busy.value = true;
    status.value = '正在同步云端…';
    inFlight = (async () => {
      try {
        const result = await put<CloudDraft>(endpoint, {
          ownerId,
          version,
          payload: JSON.parse(snapshot),
        });
        if (disposed || generation !== epoch) return false;
        version = result.version;
        baseline = snapshot;
        status.value = '已同步云端，可在其他设备继续填写';
        return true;
      } catch (e) {
        const err = e as ApiError;
        if (err.code === 'DRAFT_CONFLICT') {
          ready.value = false;
          try {
            conflict.value = await get<CloudDraft>(path());
          } catch {
            /* Keep local form and require reconnect. */
          }
          status.value = '另一台设备更新了草稿，请核对后继续。';
        } else
          status.value = `云端暂未保存：${err.message}。文字已保留在本机，可稍后重试。`;
        return false;
      } finally {
        busy.value = false;
        inFlight = undefined;
      }
    })();
    return inFlight;
  }
  async function choose(useCloud: boolean) {
    if (!conflict.value) return;
    version = conflict.value.version;
    if (useCloud) restore(conflict.value.payload);
    baseline = useCloud ? source.value : '';
    conflict.value = null;
    ready.value = true;
    if (useCloud) status.value = '已采用云端内容';
    else await flush();
  }
  async function clear() {
    clearTimeout(timer);
    if (inFlight) await inFlight;
    if (!ready.value || conflict.value) return false;
    try {
      const result = await put<CloudDraft>(path(), {
        ownerId,
        version,
        payload: null,
      });
      version = result.version;
      baseline = source.value;
      status.value = '云端未完成草稿已清除';
      return true;
    } catch (e) {
      status.value = '云端草稿尚未清除，本机内容仍保留，请稍后重试。';
      return false;
    }
  }
  watch(source, () => {
    if (!ready.value || disposed || conflict.value) return;
    status.value = '内容已保留在本机，等待同步…';
    clearTimeout(timer);
    timer = setTimeout(() => void flush(), 1200);
  });
  onBeforeRouteLeave(async () => {
    await flush();
  });
  onBeforeRouteUpdate(async () => {
    await flush();
    ready.value = false;
    clearTimeout(timer);
  });
  onBeforeUnmount(() => {
    disposed = true;
    clearTimeout(timer);
  });
  return { status, conflict, ready, busy, initialize, flush, choose, clear };
}
