<script setup lang="ts">
import { defineAsyncComponent, ref } from 'vue';
import CatalogDialog from '../../modules/catalog/components/CatalogDialog.vue';
const open = ref(false);
const Policies = defineAsyncComponent(
  () => import('../../modules/policies/PoliciesPage.vue'),
);
</script>
<template>
  <button class="policy-link" type="button" @click.stop.prevent="open = true">
    用户协议与交易售后规则</button
  ><Teleport to="body"
    ><CatalogDialog
      v-model:open="open"
      id="policy-preview"
      title="用户协议与交易售后规则"
      wide
      ><Policies v-if="open" /></CatalogDialog
  ></Teleport>
</template>
<style scoped>
.policy-link {
  background: none;
  border: 0;
  padding: 0;
  color: var(--mm-primary);
  text-decoration: underline;
  font: inherit;
  text-align: left;
}
</style>
