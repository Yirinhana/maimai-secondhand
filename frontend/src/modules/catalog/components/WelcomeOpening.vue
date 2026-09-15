<template>
  <section class="welcome-opening" aria-labelledby="welcome-title">
    <p class="welcome-opening__eyebrow">旧物的新故事，从这里开始</p>
    <h1 id="welcome-title">麦麦二手<span aria-hidden="true">。</span></h1>
    <p class="welcome-opening__line">让好物，<span>继续被喜欢。</span></p>
  </section>
</template>
<style scoped>
.welcome-opening {
  width: 100%;
  text-align: center;
  color: #252621;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 12px 0 20px;
}
.welcome-opening__eyebrow {
  color: #77786e;
  font-size: 11px;
  letter-spacing: 0.3em;
  margin-bottom: 24px;
  animation: welcome-detail 1.2s ease 0.7s both;
}
.welcome-opening h1 {
  position: relative;
  width: max-content;
  font-size: min(21vw, 32svh, 340px);
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
  font-size: clamp(18px, 2.6vw, 31px);
  letter-spacing: 0.12em;
  line-height: 1.8;
  margin: 23px 12px 0;
  animation: welcome-detail 1.2s ease 1.35s both;
}
.welcome-opening__line span {
  color: #9a451d;
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
@media (max-width: 760px) {
  .welcome-opening {
    padding-bottom: 32px;
  }
  .welcome-opening h1 {
    font-size: 21vw;
  }
  .welcome-opening__eyebrow {
    font-size: 10px;
    letter-spacing: 0.17em;
    margin-bottom: 27px;
  }
  .welcome-opening__line {
    font-size: 18px;
    letter-spacing: 0.05em;
    margin-top: 26px;
  }
}
@media (max-height: 550px) and (min-width: 761px) {
  .welcome-opening h1 {
    font-size: 27svh;
  }
  .welcome-opening__eyebrow {
    margin-bottom: 10px;
  }
  .welcome-opening__line {
    margin-top: 12px;
    font-size: 18px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .welcome-opening * {
    animation: none !important;
    transition: none !important;
  }
}
</style>
