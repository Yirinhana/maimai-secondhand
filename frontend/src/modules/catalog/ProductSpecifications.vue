<script setup lang="ts">
import { ref, watch } from 'vue';
import { get } from '../../shared/api';
const props = defineProps<{
    categoryId?: number | string;
    modelValue?: Record<string, string>;
    editable?: boolean;
  }>(),
  emit = defineEmits<{ 'update:modelValue': [Record<string, string>] }>();
const fields = ref<{ key: string; label: string; hint: string }[]>([]),
  error = ref('');
let generation = 0;
watch(
  () => props.categoryId,
  async (id) => {
    const current = ++generation;
    fields.value = [];
    error.value = '';
    if (!id) return;
    try {
      const result = await get<typeof fields.value>(
        `/categories/${id}/specifications`,
      );
      if (current === generation) {
        fields.value = result;
        if (props.editable)
          emit(
            'update:modelValue',
            Object.fromEntries(
              Object.entries(props.modelValue ?? {}).filter(([k]) =>
                result.some((f) => f.key === k),
              ),
            ),
          );
      }
    } catch (e) {
      if (current === generation) error.value = (e as Error).message;
    }
  },
  { immediate: true },
);
function update(key: string, event: Event) {
  emit('update:modelValue', {
    ...props.modelValue,
    [key]: (event.target as HTMLInputElement).value,
  });
}
</script>
<template>
  <section v-if="categoryId" class="product-specs">
    <h3>商品参数</h3>
    <p class="mm-muted">
      {{
        editable
          ? '按实物填写，不确定的项目可以留空；修改后会随商品重新审核。'
          : '以下信息由卖家填写，不清楚的细节可在购买前联系确认。'
      }}
    </p>
    <p v-if="error" class="mm-error" role="alert">{{ error }}</p>
    <div v-if="editable" class="spec-fields">
      <label v-for="field in fields" :key="field.key"
        >{{ field.label
        }}<input
          :value="modelValue?.[field.key] ?? ''"
          :placeholder="field.hint"
          maxlength="200"
          @input="update(field.key, $event)"
      /></label>
    </div>
    <dl v-else>
      <div v-for="field in fields" :key="field.key">
        <dt>{{ field.label }}</dt>
        <dd>{{ modelValue?.[field.key] || '未提供' }}</dd>
      </div>
    </dl>
  </section>
</template>
<style scoped>
.product-specs {
  margin: 18px 0;
}
.product-specs h3 {
  font-size: 16px;
}
.product-specs p {
  font-size: 13px;
  line-height: 1.7;
}
.spec-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}
.spec-fields label {
  display: grid;
  gap: 8px;
  font-size: 14px;
}
.spec-fields input {
  padding: 12px;
  border: 1px solid var(--mm-border);
  border-radius: 8px;
  font: inherit;
  min-width: 0;
}
.product-specs dl > div {
  display: grid;
  grid-template-columns: 110px minmax(0, 1fr);
  gap: 16px;
  padding: 12px 0;
  border-bottom: 1px solid var(--mm-border);
  font-size: 14px;
}
.product-specs dt {
  color: var(--mm-muted);
}
.product-specs dd {
  margin: 0;
  overflow-wrap: anywhere;
}
@media (max-width: 600px) {
  .spec-fields {
    grid-template-columns: 1fr;
  }
}
</style>
