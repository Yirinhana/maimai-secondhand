<script setup lang="ts">
import MmIcon from '../../../shared/components/MmIcon.vue';
import { scrollToSection } from '../useHomeMotion';

defineEmits<{ enter: [event: MouseEvent, skip?: boolean] }>();
</script>

<template>
  <section class="welcome-opening" aria-labelledby="welcome-title">
    <div class="welcome-opening__top">
      <img
        src="/brand/maimai-symbol.svg"
        alt="麦麦二手 Logo"
        width="44"
        height="44"
      />
      <span aria-hidden="true">MAIMAI MARKET</span>
      <a href="/" @click="$emit('enter', $event, true)"
        >跳过开场 <MmIcon name="arrow"
      /></a>
    </div>

    <div class="welcome-opening__stage">
      <p class="welcome-opening__eyebrow">旧物的新故事，从这里开始</p>
      <h1 id="welcome-title">麦麦二手<span aria-hidden="true">。</span></h1>
      <p class="welcome-opening__line">让好物，<span>继续被喜欢。</span></p>
      <a
        class="welcome-opening__enter"
        href="/"
        @click="$emit('enter', $event)"
      >
        <span>进入麦麦</span><MmIcon name="arrow" />
      </a>
    </div>

    <div class="welcome-opening__bottom">
      <span aria-hidden="true">每一次转手，都是新的开始。</span>
      <a href="#home-story" @click="scrollToSection($event, 'home-story')">
        向下，慢慢认识麦麦 <MmIcon name="arrow" />
      </a>
      <span aria-hidden="true">GOOD THINGS, AGAIN.</span>
    </div>
  </section>
</template>

<style scoped>
.welcome-opening {
  min-height: 100svh;
  display: flex;
  flex-direction: column;
  position: relative;
  isolation: isolate;
  overflow: hidden;
  background: #faf9f6;
  color: #252621;
}
.welcome-opening__top {
  display: flex;
  align-items: center;
  gap: 15px;
  padding: 28px clamp(24px, 4vw, 72px);
  z-index: 2;
}
.welcome-opening__top > span {
  font-size: 10px;
  letter-spacing: 2px;
  color: #77766b;
}
.welcome-opening__top > a {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 15px;
  min-height: 44px;
  color: #53544b;
  font-size: 12px;
}
.welcome-opening .mm-icon {
  width: 18px;
  height: 18px;
}
.welcome-opening__stage {
  flex: 1;
  min-height: 430px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 32px 0 48px;
  text-align: center;
}
.welcome-opening__eyebrow {
  color: #75766c;
  font-size: 12px;
  letter-spacing: 0.35em;
  margin-bottom: 27px;
  animation: welcome-detail 1.2s ease 0.7s both;
}
.welcome-opening h1 {
  position: relative;
  width: max-content;
  font-size: clamp(88px, 21vw, 350px);
  font-weight: 850;
  line-height: 1.12;
  letter-spacing: -0.065em;
  padding-right: 0.065em;
  white-space: nowrap;
  margin: 0;
  transform-origin: 50% 54%;
  animation: welcome-arrival 2.6s cubic-bezier(0.16, 0.55, 0.2, 1) 0.1s both;
}
.welcome-opening h1 > span {
  position: absolute;
  right: -0.34em;
  bottom: 0.02em;
  font-size: 0.36em;
  color: #b94e18;
  animation: welcome-detail 1s ease 1.5s both;
}
.welcome-opening__line {
  font-size: clamp(18px, 2.6vw, 34px);
  letter-spacing: 0.12em;
  line-height: 1.8;
  margin: 32px 20px 0;
  animation: welcome-detail 1.2s ease 1.35s both;
}
.welcome-opening__line span {
  color: #9a451d;
}
.welcome-opening__enter {
  display: inline-flex;
  justify-content: center;
  align-items: center;
  gap: 55px;
  min-height: 55px;
  padding: 14px 24px;
  color: #faf9f6;
  background: #292b26;
  border-radius: 4px;
  font-size: 14px;
  margin-top: 34px;
  z-index: 1;
  transition:
    background-color 350ms,
    transform 350ms;
}
.welcome-opening__enter:hover {
  background: #984518;
  text-decoration: none;
  transform: translateY(-3px);
}
.welcome-opening__enter .mm-icon {
  transition: transform 350ms;
}
.welcome-opening__enter:hover .mm-icon {
  transform: translateX(4px);
}
.welcome-opening__bottom {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 20px;
  padding: 16px clamp(24px, 4vw, 72px) 28px;
  font-size: 10px;
  letter-spacing: 1px;
  color: #76766d;
  z-index: 2;
}
.welcome-opening__bottom > span:last-child {
  text-align: right;
}
.welcome-opening__bottom > a {
  display: flex;
  align-items: center;
  gap: 17px;
  min-height: 44px;
  color: #53544b;
  font-size: 12px;
}
.welcome-opening__bottom .mm-icon {
  transform: rotate(90deg);
  animation: welcome-scroll 3.2s ease-in-out 3;
}
.welcome-opening a:focus-visible {
  outline: 2px solid #a64b20;
  outline-offset: 6px;
}
@keyframes welcome-arrival {
  0% {
    opacity: 0;
    transform: scale(1.7) translateY(5%);
    filter: blur(22px);
  }
  22% {
    opacity: 0.45;
  }
  65% {
    opacity: 1;
    filter: blur(0);
  }
  100% {
    opacity: 1;
    transform: scale(1) translateY(0);
    filter: blur(0);
  }
}
@keyframes welcome-detail {
  from {
    opacity: 0;
    transform: translateY(18px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
@keyframes welcome-scroll {
  0%,
  100% {
    transform: rotate(90deg) translateX(0);
  }
  50% {
    transform: rotate(90deg) translateX(7px);
  }
}
@media (min-width: 761px) and (max-height: 850px) {
  .welcome-opening__top {
    padding-top: 18px;
    padding-bottom: 18px;
  }
  .welcome-opening__stage {
    min-height: 0;
    padding: 8px 0 20px;
  }
  .welcome-opening__eyebrow {
    margin-bottom: 20px;
  }
  .welcome-opening h1 {
    font-size: min(21vw, 32svh);
  }
  .welcome-opening__line {
    font-size: 24px;
    margin-top: 18px;
  }
  .welcome-opening__enter {
    min-height: 48px;
    margin-top: 22px;
  }
  .welcome-opening__bottom {
    padding-top: 8px;
    padding-bottom: 18px;
  }
}
@media (max-width: 760px) {
  .welcome-opening__top {
    padding: 18px 23px;
    gap: 11px;
  }
  .welcome-opening__top img {
    width: 36px;
    height: 36px;
  }
  .welcome-opening__top > span {
    font-size: 8px;
    letter-spacing: 1px;
  }
  .welcome-opening__top > a {
    font-size: 11px;
    gap: 9px;
  }
  .welcome-opening__stage {
    min-height: 360px;
    padding: 20px 0 48px;
  }
  .welcome-opening__eyebrow {
    font-size: 10px;
    letter-spacing: 0.2em;
    margin-bottom: 30px;
  }
  .welcome-opening h1 {
    font-size: 21vw;
  }
  .welcome-opening__line {
    font-size: 19px;
    margin-top: 30px;
    letter-spacing: 0.05em;
  }
  .welcome-opening__enter {
    margin-top: 33px;
    min-height: 52px;
    font-size: 13px;
  }
  .welcome-opening__bottom {
    grid-template-columns: 1fr;
    justify-items: center;
    padding-bottom: 28px;
  }
  .welcome-opening__bottom > span {
    display: none;
  }
  .welcome-opening__bottom > a {
    font-size: 11px;
  }
}
@media (max-height: 550px) and (min-width: 761px) {
  .welcome-opening__stage {
    min-height: 340px;
  }
  .welcome-opening h1 {
    font-size: 18vw;
  }
}
@media (prefers-reduced-motion: reduce) {
  .welcome-opening *,
  .welcome-opening *::after {
    animation: none !important;
    transition: none !important;
  }
  .welcome-opening__enter:hover {
    transform: none;
  }
}
@media print {
  .welcome-opening * {
    animation: none !important;
  }
}
</style>
