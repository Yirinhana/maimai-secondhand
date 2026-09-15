<script setup lang="ts">
import { computed, ref } from 'vue';
import type { ProductSummary } from '../../../shared/types';
import ItemImage from '../../../shared/components/ItemImage.vue';
import MmIcon from '../../../shared/components/MmIcon.vue';
import PriceText from '../../../shared/components/PriceText.vue';
import { useHomeMotion } from '../useHomeMotion';

const props = defineProps<{ products: ProductSummary[] }>();
const introduction = ref<HTMLElement | null>(null);
useHomeMotion(introduction);
defineEmits<{ enter: [event: MouseEvent] }>();
const discoveries = computed(() =>
  props.products.filter((item) => item.coverImage).slice(0, 3),
);
const steps = [
  {
    number: '01',
    icon: 'eye',
    title: '先看清，再心动',
    text: '翻翻照片，看看成色与瑕疵。喜欢一件旧物，也了解它用过的痕迹。',
  },
  {
    number: '02',
    icon: 'message',
    title: '有疑问，直接聊',
    text: '从商品页联系卖家，带着商品卡片问细节、商量价格，沟通更明白。',
  },
  {
    number: '03',
    icon: 'box',
    title: '约好方式，再交付',
    text: '快递寄送，或同城见一面。提前确认时间地点，在订单里留下交付记录。',
  },
];
</script>

<template>
  <div ref="introduction" class="home-introduction">
    <section
      id="home-story"
      class="home-story"
      tabindex="-1"
      aria-labelledby="home-story-title"
    >
      <div class="home-story__copy" data-reveal>
        <p class="home-section-label">
          <span>01 / 发现</span> 日常里，还有新的喜欢
        </p>
        <h2 id="home-story-title">
          不一定是新的，<br />也可以<span>刚刚好。</span>
        </h2>
        <p class="home-story__text">
          一本读到一半的书，一把换下来的椅子，一台陪人记录过风景的相机。它们的故事还没结束，也许正适合现在的你。
        </p>
        <a class="home-text-link" href="/" @click="$emit('enter', $event)"
          >去找我的下一件喜欢 <MmIcon name="arrow"
        /></a>
      </div>
      <div class="home-discoveries" data-reveal>
        <template v-if="discoveries.length">
          <RouterLink
            v-for="(product, index) in discoveries"
            :key="product.id"
            :to="`/products/${product.id}`"
            class="home-discoveries__item"
            :class="`home-discoveries__item--${index}`"
            :aria-label="`查看商品：${product.title}`"
          >
            <div class="home-discoveries__image">
              <ItemImage :src="product.coverImage" :alt="product.title" />
            </div>
            <div class="home-discoveries__caption">
              <span>{{ product.title }}</span
              ><PriceText :cents="product.priceCents" />
            </div>
          </RouterLink>
          <p class="home-discoveries__note">此刻在麦麦，等待下一次相遇</p>
        </template>
        <div v-else class="home-discoveries__empty">
          <MmIcon name="book" /><MmIcon name="phone" /><MmIcon name="home" />
          <p>从一件小小的闲置，<br />发现生活的另一种可能。</p>
        </div>
      </div>
    </section>

    <section class="home-how" aria-labelledby="home-how-title">
      <div class="home-how__heading" data-reveal>
        <div>
          <p class="home-section-label">02 / 了解</p>
          <h2 id="home-how-title">喜欢之前，先聊个明白。</h2>
        </div>
        <RouterLink
          class="home-text-link"
          to="/official/buying-and-selling-guide"
          >看看完整交易指南 <MmIcon name="arrow"
        /></RouterLink>
      </div>
      <ol class="home-how__steps">
        <li v-for="step in steps" :key="step.number" data-reveal>
          <div class="home-how__step-top">
            <span>{{ step.number }}</span
            ><MmIcon :name="step.icon" />
          </div>
          <h3>{{ step.title }}</h3>
          <p>{{ step.text }}</p>
        </li>
      </ol>
    </section>

    <section class="home-share" aria-labelledby="home-share-title">
      <div data-reveal>
        <p class="home-section-label">03 / 流转</p>
        <h2 id="home-share-title">
          你的闲置，<br />也许正被<span>需要。</span>
        </h2>
      </div>
      <div class="home-share__detail" data-reveal>
        <p>
          把使用情况写清楚，给它拍几张照片。<br />腾出一点空间，让好物去往下一个日常。
        </p>
        <div class="home-share__actions">
          <RouterLink class="home-link home-link--light" to="/publish"
            >发布一件闲置 <MmIcon name="plus" /></RouterLink
          ><RouterLink class="home-text-link" to="/community/demands"
            >看看大家在找什么 <MmIcon name="arrow"
          /></RouterLink>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.home-introduction {
  --home-paper: #faf9f6;
  --home-accent: #b94e18;
}
.home-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 24px;
  min-height: 48px;
  padding: 12px 22px;
  border: 1px solid transparent;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 600;
  transition:
    background-color 180ms,
    transform 180ms;
}
.home-link:hover {
  text-decoration: none;
  transform: translateY(-2px);
}
.home-link--filled {
  background: var(--home-accent);
  color: #fff;
}
.home-link--filled:hover {
  background: #9a3d10;
}
.home-link--quiet {
  color: var(--mm-ink);
}
.home-link--quiet:hover {
  background: #efece5;
}
.home-link .mm-icon {
  width: 18px;
  height: 18px;
}
.home-section-label {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 18px;
  font-size: 12px;
  letter-spacing: 1px;
  color: var(--mm-muted);
  margin-bottom: 27px;
}
.home-section-label span {
  color: var(--home-accent);
}
.home-story {
  padding: 104px 0;
  display: grid;
  grid-template-columns: 0.9fr 1.1fr;
  align-items: center;
  gap: 60px;
  scroll-margin-top: 30px;
}
.home-story:focus {
  outline: none;
}
.home-story h2,
.home-share h2 {
  font-size: clamp(32px, 3.6vw, 48px);
  letter-spacing: -1.5px;
  font-weight: 650;
  line-height: 1.5;
}
.home-story h2 span {
  color: var(--home-accent);
}
.home-story__text {
  max-width: 380px;
  margin: 24px 0 28px;
  font-size: 15px;
  color: var(--mm-muted);
  line-height: 2;
}
.home-text-link {
  display: inline-flex;
  align-items: center;
  gap: 18px;
  min-height: 44px;
  font-size: 13px;
  color: var(--mm-ink);
}
.home-text-link .mm-icon {
  width: 18px;
  height: 18px;
  transition: transform 180ms;
}
.home-text-link:hover .mm-icon {
  transform: translateX(4px);
}
.home-discoveries {
  min-height: 425px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  grid-template-rows: auto auto auto;
  align-items: start;
  gap: 18px;
  padding: 8px 6px;
}
.home-discoveries__item {
  display: block;
  min-width: 0;
  color: var(--mm-ink);
}
.home-discoveries__item:hover {
  text-decoration: none;
}
.home-discoveries__item--0 {
  grid-column: 1;
  grid-row: 1 / 3;
  margin-top: 42px;
}
.home-discoveries__item--1 {
  grid-column: 2;
  grid-row: 1;
  width: 84%;
  margin-left: auto;
}
.home-discoveries__item--2 {
  grid-column: 2;
  grid-row: 2;
  width: 76%;
}
.home-discoveries__image {
  padding: 8px;
  background: #fff;
  border: 1px solid #e8e4da;
  border-radius: 4px;
  box-shadow: 0 8px 26px #39322608;
  transition: transform 300ms ease;
}
.home-discoveries__item--0 .home-discoveries__image {
  transform: rotate(-5deg);
}
.home-discoveries__item--1 .home-discoveries__image {
  transform: rotate(5deg);
}
.home-discoveries__item--2 .home-discoveries__image {
  transform: rotate(-3deg);
}
.home-discoveries__item:hover .home-discoveries__image {
  transform: rotate(0);
}
.home-discoveries__image img {
  width: 100%;
  aspect-ratio: 1;
  object-fit: cover;
  border-radius: 2px;
}
.home-discoveries__caption {
  margin-top: 14px;
  padding: 0 3px;
  display: flex;
  align-items: baseline;
  gap: 10px;
  justify-content: space-between;
  font-size: 11px;
}
.home-discoveries__caption > span {
  white-space: nowrap;
  text-overflow: ellipsis;
  overflow: hidden;
}
.home-discoveries__caption :deep(.mm-price) {
  flex: none;
  font-size: 13px;
}
.home-discoveries__note {
  grid-column: 1 / -1;
  text-align: right;
  color: var(--mm-muted);
  font-size: 11px;
  letter-spacing: 1px;
  padding-top: 8px;
}
.home-discoveries__empty {
  grid-column: 1 / -1;
  align-self: center;
  text-align: center;
  color: var(--mm-muted);
  padding: 50px 20px;
  border: 1px solid var(--mm-border);
}
.home-discoveries__empty > .mm-icon {
  width: 38px;
  height: 38px;
  margin: 15px;
  color: var(--home-accent);
}
.home-discoveries__empty p {
  margin-top: 20px;
  line-height: 2;
}
.home-how {
  padding: 50px 42px 44px;
  background: #f0ece3;
  border-radius: 8px;
}
.home-how__heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 20px;
  margin-bottom: 44px;
}
.home-how .home-section-label {
  margin-bottom: 14px;
}
.home-how h2 {
  font-size: clamp(26px, 3vw, 36px);
  line-height: 1.5;
  font-weight: 650;
  letter-spacing: -1px;
}
.home-how__steps {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
}
.home-how__steps li {
  padding: 0 30px;
  border-left: 1px solid #d7d1c4;
}
.home-how__steps li:first-child {
  padding-left: 0;
  border: 0;
}
.home-how__steps li:last-child {
  padding-right: 0;
}
.home-how__step-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--home-accent);
  margin-bottom: 25px;
}
.home-how__step-top > span {
  font-family: Georgia, serif;
  font-size: 27px;
}
.home-how__step-top > .mm-icon {
  width: 26px;
  height: 26px;
}
.home-how h3 {
  font-size: 19px;
  font-weight: 600;
  margin-bottom: 14px;
}
.home-how__steps p {
  font-size: 14px;
  line-height: 1.95;
  color: #686459;
}
.home-share {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 52px;
  align-items: center;
  margin-top: 28px;
  padding: 54px 48px;
  background: #292b27;
  color: #fffaf0;
  border-radius: 8px;
}
.home-share .home-section-label {
  color: #c1b9aa;
  margin-bottom: 20px;
}
.home-share h2 {
  font-size: clamp(30px, 3vw, 40px);
}
.home-share h2 span {
  color: #ecaf7c;
}
.home-share__detail > p {
  font-size: 14px;
  color: #d0cbbf;
  line-height: 2;
}
.home-share__actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px 24px;
  margin-top: 28px;
}
.home-link--light {
  background: #fff9ed;
  color: #292b27;
}
.home-link--light:hover {
  background: #efd9bd;
}
.home-share .home-text-link {
  color: #f2ece1;
}
[data-reveal] {
  transition:
    opacity 1100ms ease,
    transform 1400ms cubic-bezier(0.2, 0.7, 0.2, 1);
}
[data-reveal].is-waiting {
  opacity: 0;
  transform: translateY(28px);
}
[data-reveal]:focus-within {
  opacity: 1;
  transform: none;
}
@media (max-width: 1000px) {
  .home-story {
    gap: 36px;
    padding: 76px 0;
  }
  .home-discoveries {
    min-height: 360px;
  }
  .home-how,
  .home-share {
    padding: 36px 28px;
  }
  .home-how__steps li {
    padding: 0 22px;
  }
  .home-share {
    gap: 28px;
  }
}
@media (max-width: 760px) {
  .home-story {
    grid-template-columns: 1fr;
    padding: 58px 0;
    gap: 32px;
  }
  .home-section-label {
    margin-bottom: 20px;
    font-size: 10px;
    gap: 13px;
  }
  .home-story h2 {
    font-size: 34px;
  }
  .home-story__text {
    margin: 20px 0 17px;
    font-size: 14px;
    max-width: 450px;
  }
  .home-discoveries {
    min-height: 310px;
    max-width: 480px;
    width: 100%;
    margin: auto;
    gap: 16px;
    padding: 0 8px;
  }
  .home-discoveries__item--0 {
    margin-top: 35px;
  }
  .home-discoveries__caption {
    flex-wrap: wrap;
    gap: 1px 6px;
    margin-top: 11px;
  }
  .home-discoveries__caption > span {
    max-width: 100%;
  }
  .home-discoveries__image {
    padding: 5px;
  }
  .home-discoveries__note {
    font-size: 10px;
  }
  .home-how {
    padding: 28px 22px;
  }
  .home-how__heading {
    margin-bottom: 26px;
    gap: 10px;
  }
  .home-how h2 {
    font-size: 26px;
    max-width: 290px;
  }
  .home-how__steps {
    grid-template-columns: 1fr;
  }
  .home-how__steps li,
  .home-how__steps li:first-child,
  .home-how__steps li:last-child {
    padding: 22px 0;
    border-left: 0;
    border-top: 1px solid #d7d1c4;
  }
  .home-how__steps li:last-child {
    padding-bottom: 0;
  }
  .home-how__step-top {
    margin-bottom: 12px;
  }
  .home-how h3 {
    font-size: 18px;
    margin-bottom: 9px;
  }
  .home-how__steps p {
    font-size: 13px;
  }
  .home-share {
    grid-template-columns: 1fr;
    padding: 32px 24px;
    gap: 23px;
    margin-top: 20px;
  }
  .home-share h2 {
    font-size: 32px;
  }
  .home-share__detail > p {
    font-size: 13px;
  }
  .home-share__actions {
    margin-top: 22px;
    gap: 10px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .home-introduction *,
  .home-introduction *::before,
  .home-introduction *::after {
    animation: none !important;
    transition: none !important;
  }
  [data-reveal].is-waiting {
    opacity: 1;
    transform: none;
  }
  .home-link:hover,
  .home-text-link:hover .mm-icon {
    transform: none;
  }
}
@media print {
  [data-reveal].is-waiting {
    opacity: 1;
    transform: none;
  }
}
</style>
