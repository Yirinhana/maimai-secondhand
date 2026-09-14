<template>
  <aside class="registration-visual" aria-label="整理闲置，开启新的生活故事">
    <figure
      class="registration-visual__image"
      :class="{ 'is-loaded': imageLoaded }"
    >
      <img
        v-show="!imageFailed"
        :src="registrationImage"
        alt="暖白室内整理二手闲置的生活场景示意图"
        width="1024"
        height="1536"
        loading="eager"
        decoding="async"
        @load="imageLoaded = true"
        @error="imageFailed = true"
      />
      <figcaption v-if="imageLoaded">生活场景示意图</figcaption>
    </figure>
    <div class="registration-visual__copy">
      <p class="registration-visual__eyebrow">A LITTLE ROOM, A NEW STORY</p>
      <h2>给生活，<br />留点空间。</h2>
      <p class="registration-visual__description">
        一件闲置的新故事，从你开始。
      </p>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { ref } from 'vue';

const registrationImage = '/images/auth/registration-life.jpg';
const imageLoaded = ref(false);
const imageFailed = ref(false);
</script>

<style scoped>
.registration-visual {
  position: sticky;
  top: 28px;
  align-self: start;
  min-width: 0;
  isolation: isolate;
}
.registration-visual__image {
  position: relative;
  margin: 0;
  width: 100%;
  aspect-ratio: 2 / 3;
  min-height: 640px;
  overflow: hidden;
  border-radius: 14px;
  background: #ebe6dc;
}
.registration-visual__image img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center;
}
.registration-visual__image:not(.is-loaded) img {
  visibility: hidden;
}
.registration-visual__image figcaption {
  position: absolute;
  top: 18px;
  right: 18px;
  padding: 5px 8px;
  border-radius: 4px;
  background: #fffdf6e8;
  color: #61584c;
  font-size: 11px;
  line-height: 1.5;
}
.registration-visual__copy {
  position: absolute;
  bottom: 28px;
  left: 28px;
  right: 28px;
  padding: 23px 25px;
  border: 1px solid #fffcf7;
  border-radius: 9px;
  background: #fffdf7f5;
  color: #282824;
}
.registration-visual__eyebrow {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 1.5px;
  line-height: 1.7;
  color: #786751;
}
.registration-visual h2 {
  margin: 12px 0 13px;
  font-size: clamp(30px, 3vw, 42px);
  line-height: 1.3;
  font-weight: 650;
  letter-spacing: -1px;
}
.registration-visual__description {
  font-size: 13px;
  line-height: 1.8;
  color: #615b52;
}
@media (max-width: 900px) {
  .registration-visual__image {
    min-height: 680px;
  }
  .registration-visual__copy {
    left: 18px;
    right: 18px;
    bottom: 18px;
    padding: 20px;
  }
}
@media (max-width: 760px) {
  .registration-visual {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 110px;
    align-items: center;
    gap: 16px;
    position: static;
    border-bottom: 1px solid var(--mm-border);
    padding-bottom: 18px;
  }
  .registration-visual__image {
    grid-column: 2;
    grid-row: 1;
    width: 110px;
    height: 116px;
    min-height: 0;
    aspect-ratio: auto;
    border-radius: 8px;
  }
  .registration-visual__image img {
    object-position: center 48%;
  }
  .registration-visual__image figcaption {
    display: none;
  }
  .registration-visual__copy {
    grid-column: 1;
    grid-row: 1;
    position: static;
    padding: 0;
    border: 0;
    border-radius: 0;
    background: transparent;
  }
  .registration-visual__eyebrow {
    font-size: 9px;
    letter-spacing: 0.8px;
  }
  .registration-visual h2 {
    margin: 6px 0;
    font-size: 25px;
    line-height: 1.3;
    letter-spacing: -0.6px;
  }
  .registration-visual h2 br {
    display: none;
  }
  .registration-visual__description {
    font-size: 12px;
    line-height: 1.7;
  }
}
@media (max-width: 390px) {
  .registration-visual {
    grid-template-columns: minmax(0, 1fr) 88px;
    gap: 12px;
  }
  .registration-visual__image {
    width: 88px;
    height: 108px;
  }
  .registration-visual h2 {
    font-size: 23px;
  }
  .registration-visual__eyebrow {
    max-width: 172px;
    font-size: 8px;
  }
}
</style>
