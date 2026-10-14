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
    <JourneyGuide
      title="第一次买二手？从这三步开始"
      :steps="[
        {
          title: '先看清，再下单',
          body: '核对实拍、瑕疵和含运费总价。联系卖家时会带上商品链接，避免沟通串单。',
          to: '/search',
          action: '挑选闲置',
        },
        {
          title: '看订单进度，不重复付款',
          body: '本站体验付款不扣真实资金。下单后，以订单页的付款和交付状态为准；确认收货前先检查物品。',
        },
        {
          title: '有问题，从原订单处理',
          body: '退货或退款走订单售后，保留照片和沟通记录；评价用来记录体验，不能代替退款申请。',
          to: '/me/aftersales',
          action: '查看售后',
        },
      ]"
    />
    <OrderListView role="buyer" />
    <WorkflowAssistant stage="GUIDE" title="还不确定怎么操作？问问麦仔" />
  </section>
</template>
<script setup lang="ts">
import OrderListView from './components/OrderListView.vue';
import JourneyGuide from '../../shared/components/JourneyGuide.vue';
import WorkflowAssistant from '../support/WorkflowAssistant.vue';
</script>
