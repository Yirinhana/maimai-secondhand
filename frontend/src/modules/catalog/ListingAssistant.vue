<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { get, post, type ApiError } from '../../shared/api';
import { askConfirmation } from '../../shared/confirm';
import { parseYuan } from '../../shared/moneyInput';
import MmButton from '../../shared/components/MmButton.vue';
import AiActivity from '../support/AiActivity.vue';
import PriceComparisonChart from './PriceComparisonChart.vue';
import type { ListingRecognition, PriceComparison } from './listingAssistant';

interface Availability {
  enabled: boolean;
  remainingToday: number;
  remainingUserToday: number;
  maxImages: number;
}
interface RecognitionResult {
  id: number | string;
  status: 'SUCCEEDED' | 'FAILED' | 'PROCESSING';
  cached: boolean;
  recognition: ListingRecognition | null;
  message: string;
}
const props = withDefaults(
  defineProps<{
    ownerId: number;
    draftImageIds: string[];
    title: string;
    categoryId: string;
    currentPrice: string;
    productId: number | null;
    uploading?: boolean;
    feedback?: string;
  }>(),
  { uploading: false, feedback: '' },
);
const emit = defineEmits<{
  'recognition-start': [];
  recognition: [ListingRecognition];
  'use-price': [string];
}>();
const route = useRoute();
const availability = ref<Availability | null>(null),
  availabilityError = ref('');
const selectedImages = ref<string[]>([]),
  busy = ref(false),
  checkingCache = ref(false),
  error = ref(''),
  message = ref('');
const outcome = ref<'idle' | 'success' | 'error'>('idle');
const recognition = ref<ListingRecognition | null>(null),
  pendingId = ref<number | string | null>(null);
const comparison = ref<PriceComparison | null>(null),
  comparisonBusy = ref(false),
  comparisonError = ref('');
const query = ref(props.title),
  queryEdited = ref(false);
const maxImages = computed(() =>
  Math.min(3, Math.max(1, availability.value?.maxImages ?? 3)),
);
const remaining = computed(() =>
  Math.min(
    availability.value?.remainingToday ?? 0,
    availability.value?.remainingUserToday ?? 0,
  ),
);
const canRecognize = computed(
  () =>
    !!availability.value?.enabled &&
    selectedImages.value.length > 0 &&
    !props.uploading,
);
const cacheOnly = computed(
  () => !!availability.value?.enabled && remaining.value <= 0,
);
const context = computed(
  () => `${props.ownerId}:${props.productId ?? 'new'}:${route.fullPath}`,
);
let epoch = 0,
  comparisonEpoch = 0,
  disposed = false;
let pollTimer: ReturnType<typeof setTimeout> | undefined,
  endWait: (() => void) | undefined;
const current = (generation: number) => !disposed && epoch === generation;
function stopWaiting() {
  clearTimeout(pollTimer);
  endWait?.();
  endWait = undefined;
}
function waitForPoll() {
  return new Promise<void>((resolve) => {
    endWait = resolve;
    pollTimer = setTimeout(() => {
      endWait = undefined;
      resolve();
    }, 2000);
  });
}

watch(
  () => props.title,
  (title) => {
    if (!queryEdited.value) query.value = title;
  },
);
watch(
  () => props.draftImageIds.join(','),
  () => {
    const available = props.draftImageIds;
    selectedImages.value = [
      ...new Set([
        ...selectedImages.value.filter((id) => available.includes(id)),
        ...available,
      ]),
    ].slice(0, maxImages.value);
    // Removing or replacing photographs invalidates a pending suggestion.
    epoch++;
    stopWaiting();
    busy.value = false;
    pendingId.value = null;
    error.value = '';
    message.value = '';
    recognition.value = null;
    outcome.value = 'idle';
  },
  { immediate: true },
);
watch(
  context,
  () => {
    epoch++;
    comparisonEpoch++;
    stopWaiting();
    busy.value = false;
    comparisonBusy.value = false;
    pendingId.value = null;
    recognition.value = null;
    comparison.value = null;
    availability.value = null;
    error.value = '';
    message.value = '';
    comparisonError.value = '';
    outcome.value = 'idle';
    void loadAvailability();
  },
  { immediate: true },
);
watch([query, () => props.categoryId], () => {
  comparisonEpoch++;
  comparison.value = null;
  comparisonBusy.value = false;
  comparisonError.value = '';
});
onBeforeUnmount(() => {
  disposed = true;
  epoch++;
  comparisonEpoch++;
  stopWaiting();
});

async function loadAvailability() {
  const identity = context.value;
  availabilityError.value = '';
  try {
    const result = await get<Availability>(
      '/me/listing-assistant/availability',
    );
    if (!disposed && context.value === identity) availability.value = result;
  } catch (e) {
    if (!disposed && context.value === identity)
      availabilityError.value =
        (e as ApiError).message || '暂时无法读取识图服务状态';
  }
}
function selectImage(id: string, checked: boolean) {
  if (busy.value || pendingId.value !== null) return;
  selectedImages.value = checked
    ? [...selectedImages.value, id].slice(0, maxImages.value)
    : selectedImages.value.filter((value) => value !== id);
}

async function settle(
  result: RecognitionResult,
  generation: number,
): Promise<void> {
  if (!current(generation)) return;
  pendingId.value = result.id;
  // Poll the existing job only. Never repeat the model-creating POST automatically.
  for (let count = 0; result.status === 'PROCESSING' && count < 30; count++) {
    await waitForPoll();
    if (!current(generation)) return;
    result = await get<RecognitionResult>(
      `/me/listing-assistant/runs/${encodeURIComponent(String(result.id))}`,
    );
    if (!current(generation)) return;
  }
  if (result.status === 'PROCESSING') {
    message.value =
      '服务器仍在处理照片。可以继续填写，稍后点击“查看识图结果”，不会重复发起模型调用。';
    return;
  }
  pendingId.value = null;
  if (result.status !== 'SUCCEEDED' || !result.recognition) {
    outcome.value = 'error';
    error.value =
      result.message ||
      '本次未能识别商品，请换一张清晰照片后重试，也可以直接手动填写。';
    return;
  }
  recognition.value = result.recognition;
  outcome.value = 'success';
  message.value = result.cached
    ? '已取回这组照片的识图结果，本次未重复调用模型。'
    : '麦仔已整理照片信息，请核对下方草稿后继续填写。';
  emit('recognition', result.recognition);
  await nextTick();
  if (!current(generation)) return;
  if (!queryEdited.value) query.value = props.title || result.recognition.title;
  await nextTick();
  if (!current(generation)) return;
  if (query.value.trim() || props.categoryId) void compare();
}

async function recognize() {
  if (busy.value || !canRecognize.value || pendingId.value !== null) return;
  const generation = ++epoch;
  busy.value = true;
  checkingCache.value = cacheOnly.value;
  error.value = '';
  message.value = '';
  outcome.value = 'idle';
  recognition.value = null;
  emit('recognition-start');
  try {
    const result = await post<RecognitionResult>(
      '/me/listing-assistant/recognize',
      {
        ownerId: props.ownerId,
        imageIds: [...selectedImages.value],
        requestKey: crypto.randomUUID(),
        cacheOnly: checkingCache.value,
      },
    );
    await settle(result, generation);
  } catch (e) {
    if (!current(generation)) return;
    outcome.value = 'error';
    error.value =
      (e as ApiError).message || '识图暂时未完成，您填写的内容仍然保留。';
  } finally {
    if (current(generation)) {
      busy.value = false;
      void loadAvailability();
    }
  }
}
async function resumeResult() {
  if (busy.value || pendingId.value === null) return;
  const generation = epoch,
    id = pendingId.value;
  busy.value = true;
  error.value = '';
  message.value = '';
  outcome.value = 'idle';
  try {
    await settle(
      await get<RecognitionResult>(
        `/me/listing-assistant/runs/${encodeURIComponent(String(id))}`,
      ),
      generation,
    );
  } catch (e) {
    if (current(generation)) {
      error.value = (e as ApiError).message || '暂未读到结果，可稍后再查看。';
      outcome.value = 'error';
    }
  } finally {
    if (current(generation)) {
      busy.value = false;
      void loadAvailability();
    }
  }
}
async function compare() {
  if (comparisonBusy.value) return;
  if (!query.value.trim() && !props.categoryId) {
    comparisonError.value = '请先输入商品关键词或选择分类，再查看本站报价。';
    return;
  }
  const generation = ++comparisonEpoch;
  comparisonBusy.value = true;
  comparisonError.value = '';
  try {
    const result = await get<PriceComparison>(
      '/me/listing-assistant/comparables',
      {
        query: query.value.trim(),
        categoryId: props.categoryId || undefined,
        excludeProductId: props.productId ?? undefined,
      },
    );
    if (!disposed && generation === comparisonEpoch) comparison.value = result;
  } catch (e) {
    if (!disposed && generation === comparisonEpoch)
      comparisonError.value =
        (e as ApiError).message || '本站报价暂时无法读取，请稍后重试。';
  } finally {
    if (!disposed && generation === comparisonEpoch)
      comparisonBusy.value = false;
  }
}
async function useReferencePrice() {
  const cents = comparison.value?.medianCents;
  if (cents == null || cents <= 0) return;
  const identity = context.value,
    original = props.currentPrice,
    generation = comparisonEpoch;
  const price = (cents / 100).toFixed(2);
  if (
    original.trim() &&
    !(await askConfirmation(
      `将已填写的价格 ${original} 元改为中位参考价 ${price} 元？挂牌价仅供参考，最终由你定价。`,
    ))
  )
    return;
  if (
    disposed ||
    identity !== context.value ||
    original !== props.currentPrice ||
    generation !== comparisonEpoch
  )
    return;
  emit('use-price', price);
}
</script>

<template>
  <section class="listing-assistant" aria-label="麦仔草稿与本站比价">
    <div v-if="productId === null" class="listing-assistant__recognition">
      <header>
        <span class="listing-assistant__badge">麦仔识图</span>
        <h3>拍下闲置，先帮你写一份草稿</h3>
        <p>
          选最多
          {{ maxImages }}
          张清晰照片，识别标题、分类和外观描述。草稿可以修改，核对后由你提交审核。
        </p>
      </header>
      <fieldset
        v-if="draftImageIds.length"
        class="listing-assistant__selection"
        :disabled="busy || pendingId !== null"
      >
        <legend>
          用于识别的照片（{{ selectedImages.length }}/{{ maxImages }}）
        </legend>
        <label v-for="(id, index) in draftImageIds" :key="id"
          ><input
            type="checkbox"
            :checked="selectedImages.includes(id)"
            :disabled="
              !selectedImages.includes(id) && selectedImages.length >= maxImages
            "
            @change="
              selectImage(id, ($event.target as HTMLInputElement).checked)
            "
          />第 {{ index + 1 }} 张</label
        >
      </fieldset>
      <p v-else class="listing-assistant__tip">
        先在上方添加商品照片，再让麦仔帮忙填写。
      </p>
      <p v-if="cacheOnly" class="listing-assistant__privacy">
        当前仅可查找这组照片已有的成功识别结果。没有缓存时不会调用模型或消耗额度，可继续手动填写。
      </p>
      <p v-else class="listing-assistant__privacy">
        点击“识图填写草稿”后，选中的照片会发送给 MiniMax
        识别。请避开人脸、地址和证件信息；价格、成色、库存与交易承诺由你填写。
      </p>
      <div class="listing-assistant__actions">
        <MmButton
          v-if="pendingId !== null"
          :loading="busy"
          @click="resumeResult"
          >查看识图结果</MmButton
        >
        <MmButton
          v-else
          :loading="busy"
          :disabled="!canRecognize"
          @click="recognize"
          >{{
            cacheOnly
              ? '查看已有识图结果'
              : recognition
                ? '重新识图填写草稿'
                : '识图填写草稿'
          }}</MmButton
        >
        <span v-if="availability?.enabled && remaining > 0"
          >今日剩余 {{ remaining }} 次识图</span
        >
        <span v-else-if="availability?.enabled"
          >今日新识图次数已用完，可尝试复用相同照片的已有结果。手动填写与本站比价仍可使用。</span
        >
        <span v-else-if="availability"
          >识图服务暂未开启，可继续手动填写和本站比价。</span
        >
        <span v-else-if="!availabilityError">正在检查识图服务…</span>
      </div>
      <p v-if="availabilityError" class="listing-assistant__error" role="alert">
        {{ availabilityError }}
        <button type="button" @click="loadAvailability">重新检查</button>
      </p>
      <AiActivity
        :active="busy"
        :outcome="outcome"
        :title="
          checkingCache
            ? '麦仔正在查找已有识图结果'
            : '麦仔正在识别照片、整理草稿'
        "
      />
      <p v-if="error" class="listing-assistant__error" role="alert">
        {{ error }}
      </p>
      <p v-if="message" class="listing-assistant__feedback" role="status">
        {{ message }}
      </p>
      <p v-if="feedback" class="listing-assistant__feedback" role="status">
        {{ feedback }}
      </p>
      <div v-if="recognition" class="listing-assistant__review">
        <div v-if="recognition.visibleFeatures.length">
          <strong>照片中可见</strong>
          <ul>
            <li v-for="item in recognition.visibleFeatures" :key="item">
              {{ item }}
            </li>
          </ul>
        </div>
        <div v-if="recognition.checklist.length">
          <strong>发布前请确认</strong>
          <ul>
            <li v-for="item in recognition.checklist" :key="item">
              {{ item }}
            </li>
          </ul>
        </div>
      </div>
    </div>
    <div class="listing-assistant__comparison">
      <header>
        <span class="listing-assistant__badge listing-assistant__badge--price"
          >本站比价</span
        >
        <h3>看看类似闲置，定价更有数</h3>
        <p>直接查询本站当前在售商品，不调用模型、不消耗识图额度。</p>
      </header>
      <div class="listing-assistant__search">
        <label
          ><span>比价关键词</span
          ><input
            v-model="query"
            type="search"
            maxlength="120"
            placeholder="如 相机、机械键盘、咖啡机"
            @input="queryEdited = true"
            @keydown.enter.prevent="compare"
        /></label>
        <MmButton variant="ghost" :loading="comparisonBusy" @click="compare"
          >查看本站报价</MmButton
        >
      </div>
      <p v-if="comparisonError" class="listing-assistant__error" role="alert">
        {{ comparisonError }}
      </p>
      <p v-if="comparisonBusy" role="status" class="listing-assistant__tip">
        正在查找相近商品的当前挂牌价…
      </p>
      <PriceComparisonChart
        v-if="comparison"
        :result="comparison"
        :current-price-cents="parseYuan(currentPrice)"
      />
      <div
        v-if="comparison?.medianCents"
        class="listing-assistant__actions listing-assistant__price-action"
      >
        <MmButton variant="ghost" @click="useReferencePrice"
          >采用中位参考价</MmButton
        ><span>仅点击后填入价格；可继续修改。</span>
      </div>
    </div>
  </section>
</template>

<style scoped>
.listing-assistant {
  display: grid;
  gap: 16px;
  min-width: 0;
}
.listing-assistant__recognition,
.listing-assistant__comparison {
  padding: 20px;
  border: 1px solid #d8decf;
  border-radius: 12px;
  background: #f6f8f2;
  min-width: 0;
}
.listing-assistant__comparison {
  background: #faf6f0;
  border-color: #e1d3c3;
}
.listing-assistant header h3 {
  margin: 10px 0 8px;
  font-size: 19px;
  line-height: 1.5;
}
.listing-assistant header p {
  font-size: 13px;
  color: var(--mm-muted);
  line-height: 1.8;
  margin: 0 0 16px;
}
.listing-assistant__badge {
  display: inline-block;
  background: #e4ebdb;
  color: #415335;
  font-size: 12px;
  font-weight: 700;
  padding: 5px 9px;
  border-radius: 6px;
}
.listing-assistant__badge--price {
  background: #efdfcf;
  color: #784b30;
}
.listing-assistant__selection {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 12px 0;
  margin: 0;
  border: 0;
}
.listing-assistant__selection legend {
  font-size: 13px;
  font-weight: 600;
}
.listing-assistant__selection label {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  min-height: 42px;
  padding: 0 10px;
  border: 1px solid #d3dacb;
  border-radius: 8px;
  background: white;
  font-size: 13px;
}
.listing-assistant__selection input {
  accent-color: #657650;
  width: 17px;
  height: 17px;
}
.listing-assistant__privacy,
.listing-assistant__tip {
  font-size: 12px;
  line-height: 1.8;
  color: #5e6857;
  margin: 10px 0;
}
.listing-assistant__actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin: 14px 0;
}
.listing-assistant__actions > span {
  font-size: 12px;
  line-height: 1.7;
  color: var(--mm-muted);
}
.listing-assistant__feedback {
  padding: 10px 12px;
  border-left: 3px solid #70865a;
  background: #eaf0e3;
  color: #405334;
  font-size: 13px;
  line-height: 1.8;
}
.listing-assistant__error {
  font-size: 13px;
  line-height: 1.8;
  color: var(--mm-danger);
}
.listing-assistant__error button {
  text-decoration: underline;
  margin-left: 8px;
  min-height: 36px;
}
.listing-assistant__review {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin-top: 14px;
  font-size: 13px;
  line-height: 1.8;
}
.listing-assistant__review ul {
  padding-left: 18px;
  margin: 8px 0 0;
}
.listing-assistant__search {
  display: flex;
  gap: 10px;
  align-items: end;
  margin: 16px 0;
}
.listing-assistant__search label {
  display: grid;
  gap: 7px;
  min-width: 0;
  flex: 1;
  font-size: 13px;
  font-weight: 600;
}
.listing-assistant__search input {
  box-sizing: border-box;
  width: 100%;
  min-width: 0;
  min-height: 44px;
  padding: 9px 12px;
  border: 1px solid var(--mm-border-strong);
  background: white;
  border-radius: 8px;
  font: inherit;
  font-weight: 400;
}
.listing-assistant__search button {
  flex-shrink: 0;
}
.listing-assistant__price-action {
  border-top: 1px solid #e1d3c3;
  padding-top: 16px;
  margin-bottom: 0;
}
@media (max-width: 600px) {
  .listing-assistant__recognition,
  .listing-assistant__comparison {
    padding: 14px;
  }
  .listing-assistant__search {
    flex-direction: column;
    align-items: stretch;
  }
  .listing-assistant__search input {
    font-size: 16px;
  }
  .listing-assistant__review {
    grid-template-columns: minmax(0, 1fr);
    gap: 12px;
  }
  .listing-assistant header h3 {
    font-size: 17px;
  }
}
</style>
