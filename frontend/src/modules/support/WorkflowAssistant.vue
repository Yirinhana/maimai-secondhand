<script setup lang="ts">
import WorkflowConversation from './WorkflowConversation.vue';
defineProps<{
  stage: string;
  resourceId?: number;
  draft?: string;
  title?: string;
}>();
</script>
<template>
  <WorkflowConversation
    :stage="stage"
    :resource-id="resourceId"
    :draft="draft"
    :title="title"
  />
</template>
