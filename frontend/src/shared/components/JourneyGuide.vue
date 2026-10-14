<script setup lang="ts">
defineProps<{
  title: string;
  steps: { title: string; body: string; to?: string; action?: string }[];
}>();
</script>
<template>
  <details class="journey-guide">
    <summary>{{ title }}<span>操作指引</span></summary>
    <ol>
      <li v-for="(step, index) in steps" :key="step.title">
        <span class="journey-guide__number">{{ index + 1 }}</span>
        <div>
          <strong>{{ step.title }}</strong>
          <p>{{ step.body }}</p>
          <RouterLink v-if="step.to" :to="step.to"
            >{{ step.action || '去看看' }} →</RouterLink
          >
        </div>
      </li>
    </ol>
  </details>
</template>
<style scoped>
.journey-guide {
  margin: 18px 0;
  background: #fff;
  border: 1px solid var(--mm-border);
  border-radius: 12px;
  color: var(--mm-ink);
  min-width: 0;
}
.journey-guide summary {
  cursor: pointer;
  padding: 18px;
  font-weight: 600;
  line-height: 1.6;
}
.journey-guide summary > span {
  float: right;
  color: var(--mm-muted);
  font-size: 12px;
  font-weight: 400;
  margin-left: 12px;
}
.journey-guide ol {
  list-style: none;
  padding: 0 18px 18px;
  margin: 0;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 20px;
}
.journey-guide li {
  display: flex;
  gap: 10px;
  min-width: 0;
}
.journey-guide__number {
  background: var(--mm-canvas, #f5f3ee);
  border-radius: 50%;
  width: 26px;
  height: 26px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  font-size: 12px;
  color: var(--mm-muted);
}
.journey-guide strong {
  font-size: 14px;
  line-height: 26px;
}
.journey-guide p {
  font-size: 13px;
  line-height: 1.8;
  color: var(--mm-muted);
  margin: 6px 0 10px;
}
.journey-guide a {
  font-size: 13px;
  color: var(--mm-primary);
  display: inline-block;
  padding: 5px 0;
}
.journey-guide summary:focus-visible {
  outline: 2px solid var(--mm-primary);
  outline-offset: 2px;
}
@media (max-width: 700px) {
  .journey-guide ol {
    grid-template-columns: 1fr;
    gap: 16px;
  }
  .journey-guide summary {
    padding: 14px;
  }
  .journey-guide ol {
    padding: 0 14px 16px;
  }
  .journey-guide a {
    min-height: 32px;
    line-height: 32px;
  }
}
</style>
