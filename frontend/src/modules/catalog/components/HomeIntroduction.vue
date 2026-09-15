<script setup lang="ts">
import { computed } from 'vue';
import type { ProductSummary } from '../../../shared/types';
import ItemImage from '../../../shared/components/ItemImage.vue';
import MmIcon from '../../../shared/components/MmIcon.vue';
import PriceText from '../../../shared/components/PriceText.vue';

const props = defineProps<{ products: ProductSummary[]; stage: number }>();
const discoveries = computed(() =>
  props.products.filter((item) => item.coverImage).slice(0, 3),
);
const steps = [
  {
    icon: 'eye',
    title: '先看清，再心动',
    text: '翻翻照片，看看成色与瑕疵，了解它用过的痕迹。',
  },
  {
    icon: 'message',
    title: '有疑问，直接聊',
    text: '带着商品卡片联系卖家，问细节、商量价格。',
  },
  {
    icon: 'box',
    title: '约好方式，再交付',
    text: '快递寄送或同城见面，在订单里留下交付记录。',
  },
];
</script>

<template>
  <section
    v-if="stage === 1"
    class="welcome-story welcome-story--discover"
    aria-labelledby="discovery-title"
  >
    <div class="welcome-story__copy">
      <p class="welcome-story__label">01 / 发现</p>
      <h2 id="discovery-title">
        不一定是新的，<br />也可以<span>刚刚好。</span>
      </h2>
      <p class="welcome-story__text">
        一本读过的书，一件陪伴过日常的好物。<br
          class="desktop-break"
        />它们的故事还没结束，也许正适合现在的你。
      </p>
      <RouterLink class="welcome-story__link" to="/search"
        >去找我的下一件喜欢 <MmIcon name="arrow"
      /></RouterLink>
    </div>
    <div v-if="discoveries.length" class="welcome-finds">
      <RouterLink
        v-for="(product, index) in discoveries"
        :key="product.id"
        :to="'/products/' + product.id"
        class="welcome-find"
        :class="'welcome-find--' + index"
        :aria-label="'查看商品：' + product.title"
      >
        <div class="welcome-find__photo">
          <ItemImage :src="product.coverImage" :alt="product.title" />
        </div>
        <span class="welcome-find__name">{{ product.title }}</span>
        <PriceText :cents="product.priceCents" />
      </RouterLink>
    </div>
    <div
      v-else
      class="welcome-finds welcome-finds--empty"
      aria-label="闲置好物"
    >
      <MmIcon name="book" /><MmIcon name="phone" /><MmIcon name="home" />
      <p>从一件小小的闲置，发现生活的另一种可能。</p>
    </div>
  </section>

  <section
    v-else-if="stage === 2"
    class="welcome-story welcome-story--understand"
    aria-labelledby="understand-title"
  >
    <div class="welcome-story__copy">
      <p class="welcome-story__label">02 / 了解</p>
      <h2 id="understand-title">喜欢之前，<span>先聊个明白。</span></h2>
      <p class="welcome-story__text">让每一次决定，都多一点了解。</p>
    </div>
    <ol class="welcome-steps">
      <li v-for="(step, index) in steps" :key="step.icon">
        <div class="welcome-steps__symbol">
          <span>0{{ index + 1 }}</span
          ><MmIcon :name="step.icon" />
        </div>
        <div>
          <h3>{{ step.title }}</h3>
          <p>{{ step.text }}</p>
        </div>
      </li>
    </ol>
    <RouterLink
      class="welcome-story__link"
      to="/official/buying-and-selling-guide"
      >看看完整交易指南 <MmIcon name="arrow"
    /></RouterLink>
  </section>

  <section
    v-else
    class="welcome-story welcome-story--share"
    aria-labelledby="share-title"
  >
    <div class="welcome-story__copy">
      <p class="welcome-story__label">03 / 流转</p>
      <h2 id="share-title">你的闲置，<br />也许正被<span>需要。</span></h2>
      <p class="welcome-story__text">
        腾出一点空间，让好物去往下一个日常。<br
          class="desktop-break"
        />写清使用情况，拍几张照片，就从这里开始。
      </p>
      <div class="welcome-story__actions">
        <RouterLink class="welcome-story__publish" to="/publish"
          >发布一件闲置 <MmIcon name="plus"
        /></RouterLink>
        <RouterLink class="welcome-story__link" to="/community/demands"
          >看看大家在找什么 <MmIcon name="arrow"
        /></RouterLink>
      </div>
    </div>
    <div class="welcome-circulation" aria-hidden="true">
      <span class="welcome-circulation__ring"></span>
      <svg viewBox="0 0 300 270" fill="none">
        <path
          d="M85 83h133l15 141H70L85 83Z"
          fill="#e7e4da"
          stroke="currentColor"
          stroke-width="2.5"
        />
        <path
          d="M113 92V62c0-47 75-47 75 0v30"
          stroke="currentColor"
          stroke-width="5"
          stroke-linecap="round"
        />
        <path
          d="M110 153h83m-12-12 12 12-12 12M191 187h-83m12-12-12 12 12 12"
          stroke="currentColor"
          stroke-width="5"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
        <path
          d="m230 44 4 12 12 4-12 4-4 12-4-12-12-4 12-4Z"
          fill="currentColor"
        />
        <circle cx="60" cy="189" r="6" fill="currentColor" />
      </svg>
      <span class="welcome-circulation__caption">GOOD THINGS, AGAIN.</span>
    </div>
  </section>
</template>

<style scoped>
.welcome-story {
  width: min(1180px, 100%);
  margin: auto;
  padding: 26px 12px;
  color: #292b26;
}
.welcome-story--discover,
.welcome-story--share {
  display: grid;
  grid-template-columns: 1fr 1fr;
  align-items: center;
  gap: 6%;
  min-height: 100%;
}
.welcome-story__label {
  color: #827f72;
  font-size: 11px;
  letter-spacing: 2px;
  margin-bottom: 25px;
}
.welcome-story h2 {
  font-size: clamp(32px, 4.2vw, 57px);
  font-weight: 650;
  line-height: 1.42;
  letter-spacing: -0.04em;
}
.welcome-story h2 span {
  color: #a04b26;
}
.welcome-story__text {
  margin-top: 22px;
  color: #6e7065;
  font-size: 14px;
  line-height: 1.95;
}
.welcome-story__link {
  display: inline-flex;
  align-items: center;
  gap: 18px;
  color: #41443a;
  min-height: 44px;
  font-size: 12px;
  margin-top: 24px;
}
.welcome-story .mm-icon {
  width: 18px;
  height: 18px;
  flex: none;
}
.welcome-finds {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  align-items: center;
  gap: 16px;
  padding: 25px 12px;
}
.welcome-find {
  display: block;
  min-width: 0;
  color: #34372e;
  transition: transform 260ms;
}
.welcome-find:hover {
  text-decoration: none;
  transform: translateY(-6px);
}
.welcome-find--0 {
  margin-top: 75px;
}
.welcome-find--1 {
  margin-bottom: 75px;
}
.welcome-find--2 {
  margin-top: 25px;
}
.welcome-find__photo {
  padding: 6px;
  border: 1px solid #e3dfd3;
  border-radius: 3px;
  background: #fff;
  box-shadow: 0 12px 20px #363e2608;
}
.welcome-find--0 .welcome-find__photo {
  transform: rotate(-7deg);
}
.welcome-find--1 .welcome-find__photo {
  transform: rotate(5deg);
}
.welcome-find--2 .welcome-find__photo {
  transform: rotate(-4deg);
}
.welcome-find__photo img {
  width: 100%;
  aspect-ratio: 4 / 5;
  object-fit: cover;
}
.welcome-find__name {
  display: block;
  margin-top: 15px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 11px;
}
.welcome-find :deep(.mm-price) {
  font-size: 13px;
  margin-top: 6px;
  color: #676b5d;
}
.welcome-finds--empty {
  text-align: center;
  color: #8b8d80;
}
.welcome-finds--empty .mm-icon {
  width: 55px;
  height: 55px;
  margin: auto;
}
.welcome-finds--empty p {
  grid-column: 1 / -1;
  font-size: 13px;
  line-height: 1.8;
}
.welcome-story--understand {
  max-width: 1050px;
  text-align: center;
}
.welcome-steps {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  text-align: left;
  margin-top: 35px;
}
.welcome-steps li {
  padding: 0 32px;
  border-left: 1px solid #dbd8cb;
}
.welcome-steps li:first-child {
  border-left: 0;
  padding-left: 0;
}
.welcome-steps li:last-child {
  padding-right: 0;
}
.welcome-steps__symbol {
  display: flex;
  justify-content: space-between;
  color: #7a806b;
  align-items: center;
  margin-bottom: 20px;
}
.welcome-steps__symbol span {
  font:
    28px/1 Georgia,
    serif;
}
.welcome-steps__symbol .mm-icon {
  width: 25px;
  height: 25px;
}
.welcome-steps h3 {
  font-size: 20px;
  font-weight: 600;
  margin-bottom: 12px;
}
.welcome-steps p {
  font-size: 13px;
  color: #6e7065;
  line-height: 1.85;
}
.welcome-story__actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px 24px;
  margin-top: 28px;
}
.welcome-story__actions .welcome-story__link {
  margin: 0;
}
.welcome-story__publish {
  background: #34372e;
  color: #faf9f6;
  border-radius: 4px;
  display: inline-flex;
  align-items: center;
  gap: 20px;
  min-height: 48px;
  padding: 12px 20px;
  font-size: 13px;
}
.welcome-circulation {
  width: min(360px, 100%);
  margin: auto;
  position: relative;
  color: #747a64;
  text-align: center;
}
.welcome-circulation svg {
  position: relative;
  width: 85%;
  transform: translate(var(--drift-x, 0px), var(--drift-y, 0px));
  transition: transform 180ms ease-out;
}
.welcome-circulation__ring {
  position: absolute;
  inset: -10px;
  border: 1px solid #deded1;
  border-radius: 50%;
}
.welcome-circulation__caption {
  position: relative;
  display: block;
  margin-top: 15px;
  letter-spacing: 2px;
  font-size: 10px;
}
@media (max-width: 760px) {
  .welcome-story {
    padding: 8px 0;
  }
  .welcome-story--discover,
  .welcome-story--share {
    grid-template-columns: 1fr;
    gap: 12px;
    min-height: 0;
  }
  .welcome-story__label {
    margin-bottom: 13px;
    font-size: 10px;
  }
  .welcome-story h2 {
    font-size: clamp(28px, 8vw, 38px);
  }
  .welcome-story__text {
    margin-top: 13px;
    font-size: 12px;
    line-height: 1.85;
  }
  .desktop-break {
    display: none;
  }
  .welcome-story__link {
    margin-top: 12px;
    font-size: 11px;
  }
  .welcome-finds {
    width: min(470px, 100%);
    margin: auto;
    gap: 12px;
    padding: 10px 5px;
  }
  .welcome-find--0 {
    margin-top: 30px;
  }
  .welcome-find--1 {
    margin-bottom: 24px;
  }
  .welcome-find--2 {
    margin-top: 15px;
  }
  .welcome-find__photo {
    padding: 4px;
  }
  .welcome-find__photo img {
    aspect-ratio: 1;
  }
  .welcome-find__name {
    margin-top: 10px;
    font-size: 10px;
  }
  .welcome-find :deep(.mm-price) {
    font-size: 12px;
  }
  .welcome-story--understand {
    text-align: left;
  }
  .welcome-steps {
    grid-template-columns: 1fr;
    margin-top: 20px;
    gap: 17px;
  }
  .welcome-steps li,
  .welcome-steps li:first-child,
  .welcome-steps li:last-child {
    display: grid;
    grid-template-columns: 35px 1fr;
    gap: 15px;
    border: 0;
    padding: 0;
  }
  .welcome-steps__symbol {
    flex-direction: column;
    justify-content: flex-start;
    gap: 10px;
    margin: 0;
  }
  .welcome-steps__symbol span {
    font-size: 21px;
  }
  .welcome-steps__symbol .mm-icon {
    width: 20px;
    height: 20px;
  }
  .welcome-steps h3 {
    font-size: 16px;
    margin-bottom: 6px;
  }
  .welcome-steps p {
    font-size: 12px;
    line-height: 1.8;
  }
  .welcome-circulation {
    width: min(190px, 30vh);
    margin: 16px auto 0;
  }
  .welcome-circulation__ring {
    inset: -3px;
  }
  .welcome-circulation__caption {
    font-size: 8px;
    margin-top: 5px;
  }
  .welcome-story__actions {
    gap: 6px 16px;
    margin-top: 17px;
  }
  .welcome-story__publish {
    font-size: 12px;
    min-height: 44px;
    padding: 10px 15px;
    gap: 14px;
  }
}
@media (max-height: 680px) and (max-width: 760px) {
  .welcome-story h2 {
    font-size: 26px;
  }
  .welcome-story__text {
    margin-top: 9px;
  }
  .welcome-finds {
    max-width: 290px;
    padding: 2px 5px;
  }
  .welcome-find--0,
  .welcome-find--1,
  .welcome-find--2 {
    margin-top: 0;
    margin-bottom: 0;
  }
  .welcome-story__label {
    margin-bottom: 9px;
  }
  .welcome-circulation {
    width: 105px;
    margin-top: 6px;
  }
}
</style>
