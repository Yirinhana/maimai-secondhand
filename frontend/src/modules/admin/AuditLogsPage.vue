<template>
  <section class="audit-page mm-stack">
    <header class="mm-page-heading">
      <div>
        <p class="mm-eyebrow">安全与记录</p>
        <h1>操作审计</h1>
        <p>核对谁在什么时候改变了什么，敏感操作的依据与前后状态均保留。</p>
      </div>
    </header>
    <p v-if="error" class="mm-error" role="alert">{{ error }}</p>
    <p v-if="loading" role="status">正在读取操作记录…</p>
    <div v-else class="audit-list">
      <article v-for="item in items" :key="item.id" class="audit-record">
        <div class="audit-record__heading">
          <div>
            <strong>{{ item.action }}</strong>
            <p>操作对象：{{ item.targetType }} #{{ item.targetId }}</p>
          </div>
          <div class="audit-record__meta">
            <span>管理员 #{{ item.adminId }}</span
            ><time>{{ formatTime(item.createdAt) }}</time>
          </div>
        </div>
        <p class="audit-record__reason">
          <span>操作说明</span>{{ item.reason || '无补充原因' }}
        </p>
        <details class="audit-record__changes">
          <summary>查看变更前后</summary>
          <div class="audit-record__comparison">
            <section>
              <h2>变更前</h2>
              <p>{{ item.beforeState || '无记录' }}</p>
            </section>
            <section>
              <h2>变更后</h2>
              <p>{{ item.afterState || '无记录' }}</p>
            </section>
          </div>
        </details>
      </article>
      <div v-if="!items.length && !error" class="audit-list__empty">
        <h2>暂无操作记录</h2>
        <p>完成敏感操作后，对应记录会显示在这里。</p>
      </div>
    </div>
    <MmPagination
      :page="page"
      :total-pages="pages"
      @change="
        page = $event;
        load();
      "
    />
  </section>
</template>

<script setup lang="ts">
import { useAdminList } from './useAdminList';
import { formatTime } from '../../shared/format';
import type { AuditLog } from '../../shared/types';
import MmPagination from '../../shared/components/MmPagination.vue';
const { items, page, pages, loading, error, load } =
  useAdminList<AuditLog>('/admin/audit-logs');
</script>
<style scoped>
.audit-list {
  display: grid;
  gap: 12px;
}
.audit-record {
  padding: 20px;
  background: white;
  border: 1px solid var(--mm-zone-border);
  border-radius: 12px;
  min-width: 0;
}
.audit-record__heading {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  align-items: start;
}
.audit-record__heading > div {
  min-width: 0;
  overflow-wrap: anywhere;
}
.audit-record__heading strong {
  font-size: 15px;
  color: var(--mm-zone-accent);
}
.audit-record__heading p {
  font-size: 13px;
  color: var(--mm-muted);
  margin-top: 5px;
}
.audit-record__meta {
  display: grid;
  gap: 5px;
  justify-items: end;
  font-size: 12px;
  color: var(--mm-muted);
  flex-shrink: 0;
}
.audit-record__meta span {
  color: var(--mm-ink);
  font-weight: 600;
}
.audit-record__reason {
  display: grid;
  grid-template-columns: 70px minmax(0, 1fr);
  gap: 12px;
  margin-top: 16px;
  font-size: 14px;
  overflow-wrap: anywhere;
}
.audit-record__reason > span {
  color: var(--mm-muted);
  font-size: 12px;
  padding-top: 2px;
}
.audit-record__changes {
  margin-top: 12px;
  border-top: 1px solid var(--mm-border);
}
.audit-record__changes summary {
  display: list-item;
  padding: 12px 0 0;
  min-height: 44px;
  font-size: 13px;
  color: var(--mm-zone-accent);
}
.audit-record__comparison {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  margin-top: 12px;
}
.audit-record__comparison > section {
  padding: 16px;
  background: var(--mm-zone-soft);
  border: 1px solid var(--mm-zone-border);
  border-radius: 9px;
  min-width: 0;
}
.audit-record__comparison h2 {
  font-size: 13px;
  margin-bottom: 10px;
}
.audit-record__comparison p {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font-size: 13px;
  line-height: 1.8;
}
.audit-list__empty {
  padding: 36px 20px;
  text-align: center;
  border: 1px solid var(--mm-zone-border);
  border-radius: 12px;
  background: white;
}
.audit-list__empty h2 {
  font-size: 18px;
  margin-bottom: 10px;
}
.audit-list__empty p {
  color: var(--mm-muted);
}
@media (max-width: 650px) {
  .audit-record {
    padding: 16px;
  }
  .audit-record__heading {
    flex-direction: column;
    gap: 10px;
  }
  .audit-record__meta {
    justify-items: start;
    gap: 6px;
    display: flex;
    flex-wrap: wrap;
  }
  .audit-record__comparison {
    grid-template-columns: 1fr;
  }
  .audit-record__reason {
    grid-template-columns: 1fr;
    gap: 5px;
  }
}
</style>
