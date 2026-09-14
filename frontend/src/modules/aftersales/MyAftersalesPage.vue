<template>
  <div class="mm-my-aftersales">
    <h1 class="mm-my-aftersales__heading">我的售后</h1>
    <AftersaleList
      api-path="/me/aftersales"
      empty-description="你还没有发起过售后申请，可在订单详情页申请"
    />
  </div>
</template>

<script setup lang="ts">
import AftersaleList from './AftersaleList.vue'
</script>

<style scoped>
.mm-my-aftersales__heading {
  max-width: 960px;
  margin: 0 auto;
  padding: var(--mm-space-5) var(--mm-space-4) 0;
  font-size: var(--mm-font-xl);
}
</style>
