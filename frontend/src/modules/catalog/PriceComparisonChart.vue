<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import {
  askingPricePoints,
  type PriceComparison,
} from './listingAssistant';
import { CONDITION_TEXT } from '../../shared/types';
import ItemImage from '../../shared/components/ItemImage.vue';

const props = defineProps<{
  result: PriceComparison;
  currentPriceCents: number | null;
}>();
const container = ref<HTMLElement | null>(null),
  chartWidth = ref(640);
let observer: ResizeObserver | undefined;
onMounted(() => {
  observer = new ResizeObserver(([entry]) => {
    chartWidth.value = entry!.contentRect.width < 480 ? 360 : 640;
  });
  if (container.value) observer.observe(container.value);
});
onBeforeUnmount(() => observer?.disconnect());
const graph = computed(() =>
  askingPricePoints(
    props.result.items,
    props.currentPriceCents,
    chartWidth.value,
  ),
);
const line = computed(() =>
  graph.value.points.map((p) => `${p.x},${p.y}`).join(' '),
);
const money = (cents: number | null) =>
  cents === null ? '暂无' : `¥${(cents / 100).toFixed(2)}`;
const tickMoney = (cents: number) =>
  cents >= 1000000
    ? `${(cents / 1000000).toFixed(1)}万`
    : (cents / 100).toFixed(0);
const condition = (value: string) =>
  CONDITION_TEXT[value as keyof typeof CONDITION_TEXT] || '成色待核对';
const hasExperience = computed(() =>
  props.result.items.some((item) => !!item.experienceSource),
);
</script>

<template>
  <div ref="container" class="price-comparison">
    <div class="price-comparison__stats" aria-label="本站挂牌价参考">
      <div>
        <span>最低挂牌价</span
        ><strong>{{ money(result.minCents) }}</strong>
      </div>
      <div class="price-comparison__median">
        <span>中位参考价</span
        ><strong>{{ money(result.medianCents) }}</strong>
      </div>
      <div>
        <span>最高挂牌价</span
        ><strong>{{ money(result.maxCents) }}</strong>
      </div>
    </div>
    <figure v-if="graph.points.length" class="price-comparison__figure">
      <figcaption>
        <strong>本站商品报价对比</strong
        ><span
          >{{ result.sampleCount }} 件当前在售样本 ·
          按价格从低到高排列</span
        >
      </figcaption>
      <svg
        :viewBox="`0 0 ${chartWidth} 250`"
        class="price-comparison__chart"
        role="img"
        aria-label="本站当前商品挂牌价对比图，横轴为样本序号，纵轴为人民币价格，并非历史价格走势"
      >
        <text x="7" y="15" class="price-comparison__axis-label">
          价格 / 元
        </text>
        <g v-for="(tick, i) in graph.ticks" :key="i">
          <line
            :x1="graph.left"
            :y1="tick.y"
            :x2="graph.right"
            :y2="tick.y"
            class="price-comparison__grid"
          />
          <text :x="graph.left - 8" :y="tick.y + 4" text-anchor="end">
            {{ tickMoney(tick.cents) }}
          </text>
        </g>
        <line
          v-if="graph.referenceY !== null"
          :x1="graph.left"
          :y1="graph.referenceY"
          :x2="graph.right"
          :y2="graph.referenceY"
          class="price-comparison__reference"
        />
        <polyline
          v-if="graph.points.length > 1"
          :points="line"
          class="price-comparison__line"
        />
        <g v-for="(point, i) in graph.points" :key="point.item.id">
          <a
            :href="`/products/${point.item.id}`"
            :aria-label="`样本 ${i + 1}：${point.item.title}，${money(point.item.priceCents)}，查看商品`"
          >
            <title>
              {{ point.item.title }} · {{ money(point.item.priceCents) }}
            </title>
            <circle
              :cx="point.x"
              :cy="point.y"
              r="13"
              fill="transparent"
            />
            <circle
              :cx="point.x"
              :cy="point.y"
              r="4.5"
              class="price-comparison__point"
            />
          </a>
          <text
            v-if="
              i % Math.max(1, Math.ceil(graph.points.length / 6)) === 0 ||
              i === graph.points.length - 1
            "
            :x="point.x"
            y="224"
            text-anchor="middle"
          >
            {{ i + 1 }}
          </text>
        </g>
        <text
          :x="(graph.left + graph.right) / 2"
          y="244"
          text-anchor="middle"
        >
          样本商品序号（非时间轴）
        </text>
      </svg>
      <div class="price-comparison__legend">
        <span><i></i>当前挂牌价</span
        ><span v-if="graph.referenceY !== null"
          ><i class="is-reference"></i>你填写的价格
          {{ money(currentPriceCents) }}</span
        >
      </div>
      <p v-if="graph.points.length === 1" class="price-comparison__note">
        目前只有 1 件匹配商品，仅显示单点参考，不生成折线。
      </p>
    </figure>
    <p v-else class="price-comparison__empty" role="status">
      暂时没有匹配的在售商品。可修改关键词或选择更合适的分类后再查。
    </p>
    <p class="price-comparison__note">
      {{
        result.notice ||
        '这里展示的是本站卖家当前挂牌价，不是成交价或历史走势；成色、配件与交付条件不同，价格仅供参考。'
      }}
    </p>
    <p
      v-if="result.matchLevel === 'CATEGORY'"
      class="price-comparison__note"
    >
      当前按分类展示在售商品，可能不是同款。
    </p>
    <p v-if="hasExperience" class="price-comparison__note">
      含体验来源商品；其资料与价格用于体验，不代表真实供货或成交。
    </p>
    <ol class="price-comparison__items" aria-label="比价样本商品">
      <li v-for="(item, i) in graph.sorted" :key="item.id">
        <RouterLink
          :to="`/products/${item.id}`"
          class="price-comparison__item"
        >
          <ItemImage
            :src="item.coverImage"
            :alt="item.title"
            loading="lazy"
          />
          <div>
            <span class="price-comparison__index">样本 {{ i + 1 }}</span
            ><strong>{{ item.title }}</strong
            ><span
              >{{ condition(item.condition) }} ·
              {{ item.region || '地区未填写' }}</span
            ><small
              >{{ item.sellerNickname
              }}{{ item.experienceSource ? ' · 体验来源' : '' }}</small
            >
          </div>
          <b>{{ money(item.priceCents) }}</b>
        </RouterLink>
      </li>
    </ol>
  </div>
</template>

<style scoped>
.price-comparison {
  min-width: 0;
}
.price-comparison__stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}
.price-comparison__stats > div {
  display: grid;
  gap: 6px;
  padding: 12px;
  background: #fff;
  border: 1px solid var(--mm-border);
  border-radius: 10px;
  min-width: 0;
}
.price-comparison__stats span {
  font-size: 12px;
  color: var(--mm-muted);
}
.price-comparison__stats strong {
  font-size: clamp(15px, 2vw, 21px);
  overflow-wrap: anywhere;
  font-variant-numeric: tabular-nums;
}
.price-comparison__stats .price-comparison__median {
  background: #f6ece0;
  border-color: #d9b89c;
  color: #6d3b22;
}
.price-comparison__figure {
  margin: 18px 0 12px;
  padding: 12px 6px;
  border-radius: 12px;
  background: white;
  border: 1px solid var(--mm-border);
}
.price-comparison__figure figcaption {
  display: grid;
  gap: 6px;
  padding: 0 8px 8px;
}
.price-comparison__figure figcaption span,
.price-comparison__note {
  font-size: 12px;
  line-height: 1.8;
  color: var(--mm-muted);
}
.price-comparison__chart {
  display: block;
  width: 100%;
  height: auto;
  overflow: visible;
}
.price-comparison__chart text {
  fill: #61635a;
  font-size: 12px;
  font-family: inherit;
}
.price-comparison__grid {
  stroke: #e6e8e0;
  stroke-width: 1;
}
.price-comparison__line {
  fill: none;
  stroke: #8d492b;
  stroke-width: 2.5;
  stroke-linejoin: round;
}
.price-comparison__point {
  fill: #8d492b;
  stroke: white;
  stroke-width: 2;
}
.price-comparison__chart a:focus .price-comparison__point {
  stroke: #243f5e;
  stroke-width: 4;
}
.price-comparison__reference {
  stroke: #536b7d;
  stroke-width: 2;
  stroke-dasharray: 6 5;
}
.price-comparison__legend {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  padding: 8px;
  font-size: 12px;
  color: #5c635e;
}
.price-comparison__legend span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.price-comparison__legend i {
  width: 18px;
  border-top: 2px solid #8d492b;
}
.price-comparison__legend i.is-reference {
  border-top: 2px dashed #536b7d;
}
.price-comparison__empty {
  padding: 20px 0;
  line-height: 1.8;
}
.price-comparison__note {
  margin: 8px 0;
}
.price-comparison__items {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  list-style: none;
  padding: 0;
  margin: 16px 0 0;
}
.price-comparison__item {
  display: grid;
  grid-template-columns: 52px minmax(0, 1fr);
  gap: 10px;
  height: 100%;
  padding: 12px;
  border: 1px solid var(--mm-border);
  border-radius: 10px;
  background: white;
  text-decoration: none;
  color: inherit;
}
.price-comparison__item :deep(img),
.price-comparison__item :deep(.mm-item-image) {
  width: 52px;
  height: 58px;
  border-radius: 7px;
  object-fit: cover;
}
.price-comparison__item > div {
  min-width: 0;
  display: grid;
  gap: 4px;
}
.price-comparison__item strong {
  overflow-wrap: anywhere;
  font-size: 13px;
  line-height: 1.6;
}
.price-comparison__item span,
.price-comparison__item small {
  color: var(--mm-muted);
  font-size: 11px;
  line-height: 1.6;
}
.price-comparison__item b {
  grid-column: 2;
  font-size: 16px;
  color: #8d492b;
}
.price-comparison__item:hover {
  border-color: #ab7253;
  background: #fffaf4;
}
@media (max-width: 600px) {
  .price-comparison__items {
    grid-template-columns: minmax(0, 1fr);
  }
  .price-comparison__stats > div {
    padding: 9px 7px;
  }
}
</style>
