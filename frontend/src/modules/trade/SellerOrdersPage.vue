<template>
  <section class="mm-workspace">
    <header class="mm-page-heading">
      <div>
        <p class="mm-eyebrow">SALES & FULFILLMENT</p>
        <h1>我卖出的</h1>
        <p>及时处理待发货与面交订单，交付后的售后申请同样值得认真回应。</p>
      </div>
      <RouterLink to="/seller/aftersales" class="mm-text-link"
        >处理售后 →</RouterLink
      >
    </header>
    <OrderListView role="seller" />
  </section>
</template>
<script setup lang="ts">
import OrderListView from './components/OrderListView.vue';
</script>
