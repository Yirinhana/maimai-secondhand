<template>
  <aside class="mm-auth-intro">
    <p class="mm-eyebrow">WELCOME TO MAIMAI</p>
    <h1>好物不止一程，<br />喜欢总会再相逢。</h1>
    <p class="mm-auth-intro__text">
      一个分享闲置、发现所需的地方。<br />从整理生活，到遇见下一个好故事。
    </p>
    <div class="mm-auth-intro__art" aria-hidden="true">
      <div>旧物<span>01 / KEEP THE STORY</span></div>
      <div>新故事<span>02 / PASS IT ON</span></div>
      <div>再相逢<span>03 / FIND YOUR NEXT</span></div>
    </div>
    <p class="mm-auth-intro__bottom">认真描述每件物品，珍惜每一次信任。</p>
  </aside>
</template>
<style scoped>
.mm-auth-intro {
  padding: 18px 28px 15px 0;
  min-width: 0;
}
.mm-auth-intro h1 {
  font-size: 35px;
  line-height: 1.5;
  letter-spacing: -1.3px;
  margin: 18px 0;
}
.mm-auth-intro__text {
  color: var(--mm-muted);
  font-size: 13px;
  line-height: 2;
}
.mm-auth-intro__art {
  position: relative;
  height: 215px;
  margin-top: 29px;
  max-width: 365px;
}
.mm-auth-intro__art > div {
  width: 145px;
  height: 166px;
  position: absolute;
  top: 18px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 20px 16px;
  font-size: 25px;
  font-weight: 800;
  border: 1px solid #cfcbbf;
  border-radius: 4px;
  box-shadow: 4px 8px 12px #3932280b;
}
.mm-auth-intro__art > div span {
  font-size: 7px;
  font-weight: 500;
  letter-spacing: 1px;
}
.mm-auth-intro__art > div:nth-child(1) {
  left: 6px;
  transform: rotate(-12deg);
  background: #e3dfd2;
  color: #4d5148;
}
.mm-auth-intro__art > div:nth-child(2) {
  left: 106px;
  transform: rotate(4deg);
  background: #6f7b61;
  color: white;
  border-color: #6f7b61;
}
.mm-auth-intro__art > div:nth-child(3) {
  left: 205px;
  transform: rotate(14deg);
  background: #ba542b;
  color: #fff4e8;
  border-color: #ba542b;
}
.mm-auth-intro__bottom {
  font-size: 11px;
  color: var(--mm-muted);
}
@media (max-width: 760px) {
  .mm-auth-intro {
    padding: 0;
  }
  .mm-auth-intro h1 {
    font-size: 26px;
    line-height: 1.4;
    margin: 11px 0;
  }
  .mm-auth-intro__art,
  .mm-auth-intro__bottom {
    display: none;
  }
  .mm-auth-intro__text {
    font-size: 12px;
  }
}
</style>
