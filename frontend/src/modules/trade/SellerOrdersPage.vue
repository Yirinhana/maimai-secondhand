<template>
  <section class="mm-page"><h1>我卖出的</h1><nav class="mm-actions"><RouterLink to="/orders">查看我买到的</RouterLink><RouterLink to="/seller/aftersales">处理售后</RouterLink><RouterLink to="/seller/bargains">收到的议价</RouterLink></nav><OrderListView role="seller" /></section>
</template>

<script setup lang="ts">
import OrderListView from './components/OrderListView.vue'
</script>
