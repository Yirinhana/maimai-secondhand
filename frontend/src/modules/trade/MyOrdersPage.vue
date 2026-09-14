<template>
  <section class="mm-workspace">
    <header class="mm-page-heading">
      <div>
        <p class="mm-eyebrow">MY PURCHASES</p>
        <h1>我买到的</h1>
        <p>查看付款、交付和售后进度，让每次交易都有迹可循。</p>
      </div>
      <RouterLink to="/search" class="mm-text-link">继续逛逛 →</RouterLink>
    </header>
    <OrderListView role="buyer" />
  </section>
</template>
<script setup lang="ts">
import OrderListView from './components/OrderListView.vue';
</script>
