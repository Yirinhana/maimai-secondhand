<template>
  <div class="mm-field" :class="{ 'has-error': !!error }">
    <label v-if="label" class="mm-field__label" :for="inputId">{{ label }}</label>
    <input
      :id="inputId"
      class="mm-field__input"
      :type="type"
      :value="modelValue"
      :placeholder="placeholder"
      :maxlength="maxlength"
      :autocomplete="autocomplete"
      :aria-invalid="!!error"
      :aria-describedby="error ? `${inputId}-error` : undefined"
      @input="onInput"
    />
    <p v-if="error" :id="`${inputId}-error`" class="mm-field__error" role="alert">
      {{ error }}
    </p>
    <p v-else-if="hint" class="mm-field__hint">{{ hint }}</p>
  </div>
</template>

<script setup lang="ts">
import { useId } from 'vue'

withDefaults(
  defineProps<{
    modelValue: string
    label?: string
    type?: string
    placeholder?: string
    error?: string
    hint?: string
    maxlength?: number | string
    autocomplete?: string
  }>(),
  { type: 'text', placeholder: '', error: '', hint: '', label: '', maxlength: undefined, autocomplete: undefined },
)

const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const inputId = `mm-input-${useId()}`

function onInput(event: Event) {
  emit('update:modelValue', (event.target as HTMLInputElement).value)
}
</script>

<style scoped>
.mm-field {
  display: flex;
  flex-direction: column;
  gap: var(--mm-space-1);
}

.mm-field__label {
  font-size: var(--mm-font-s);
  font-weight: 600;
  color: var(--mm-ink);
}

.mm-field__input {
  min-height: 40px;
  padding: 0 var(--mm-space-3);
  border: 1px solid var(--mm-border);
  border-radius: var(--mm-radius-m);
  background-color: var(--mm-white);
}

.mm-field__input::placeholder {
  color: var(--mm-muted);
}

.has-error .mm-field__input {
  border-color: var(--mm-danger);
}

.mm-field__error {
  font-size: var(--mm-font-s);
  color: var(--mm-danger);
}

.mm-field__hint {
  font-size: var(--mm-font-s);
  color: var(--mm-muted);
}
</style>
