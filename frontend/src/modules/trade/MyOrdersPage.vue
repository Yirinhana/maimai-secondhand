<template>
  <section class="mm-page"><h1>我买到的</h1><nav class="mm-actions"><RouterLink to="/seller/orders">查看我卖出的</RouterLink><RouterLink to="/me/aftersales">售后记录</RouterLink></nav><OrderListView role="buyer" /></section>
</template>

<script setup lang="ts">
import OrderListView from './components/OrderListView.vue'
</script>
