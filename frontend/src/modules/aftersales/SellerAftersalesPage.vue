<template>
  <div class="mm-seller-aftersales">
    <h1 class="mm-seller-aftersales__heading">售后处理</h1>
    <p class="mm-seller-aftersales__tip">
      买家发起售后后，请在 48 小时内答复；同意退货后买家有 7 天寄回窗口。
    </p>
    <AftersaleList
      api-path="/seller/aftersales"
      empty-description="暂时没有买家发起的售后申请"
    />
  </div>
</template>

<script setup lang="ts">
import AftersaleList from './AftersaleList.vue'
</script>

<style scoped>
.mm-seller-aftersales__heading {
  max-width: 960px;
  margin: 0 auto;
  padding: var(--mm-space-5) var(--mm-space-4) 0;
  font-size: var(--mm-font-xl);
}

.mm-seller-aftersales__tip {
  max-width: 960px;
  margin: 0 auto;
  padding: var(--mm-space-1) var(--mm-space-4) 0;
  font-size: var(--mm-font-s);
  color: var(--mm-muted);
}
</style>
