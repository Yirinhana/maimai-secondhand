<template>
  <div
    class="mm-field"
    :class="[{ 'has-error': !!error }, $attrs.class]"
    :style="$attrs.style as StyleValue"
  >
    <label v-if="label" class="mm-field__label" :for="inputId">{{
      label
    }}</label>
    <div
      class="mm-field__control"
      :class="{ 'has-reveal': type === 'password' }"
    >
      <input
        v-bind="forwardedAttrs()"
        ref="inputElement"
        :id="inputId"
        class="mm-field__input"
        :type="type === 'password' && passwordVisible ? 'text' : type"
        :value="modelValue"
        :placeholder="placeholder"
        :maxlength="maxlength"
        :autocomplete="autocomplete"
        :aria-invalid="!!error"
        :aria-describedby="
          [
            attrs['aria-describedby'],
            error ? `${inputId}-error` : hint ? `${inputId}-hint` : '',
          ]
            .filter(Boolean)
            .join(' ') || undefined
        "
        @input="onInput"
      />
      <button
        v-if="type === 'password'"
        class="mm-field__reveal"
        type="button"
        :aria-label="`${passwordVisible ? '隐藏' : '显示'}${label || '密码'}`"
        :aria-pressed="passwordVisible"
        :aria-controls="inputId"
        :disabled="attrs.disabled != null && attrs.disabled !== false"
        @click="togglePassword"
      >
        <MmIcon :name="passwordVisible ? 'eye-off' : 'eye'" />
      </button>
    </div>
    <p
      v-if="error"
      :id="`${inputId}-error`"
      class="mm-field__error"
      role="alert"
    >
      {{ error }}
    </p>
    <p v-else-if="hint" :id="`${inputId}-hint`" class="mm-field__hint">
      {{ hint }}
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, useAttrs, useId, type StyleValue } from 'vue';
import MmIcon from './MmIcon.vue';

defineOptions({ inheritAttrs: false });
const attrs = useAttrs();
function forwardedAttrs() {
  const { class: _class, style: _style, ...rest } = attrs;
  return rest;
}
const inputElement = ref<HTMLInputElement | null>(null);
const passwordVisible = ref(false);
async function togglePassword() {
  passwordVisible.value = !passwordVisible.value;
  await nextTick();
  inputElement.value?.focus({ preventScroll: true });
}

const props = withDefaults(
  defineProps<{
    modelValue: string;
    id?: string;
    label?: string;
    type?: string;
    placeholder?: string;
    error?: string;
    hint?: string;
    maxlength?: number | string;
    autocomplete?: string;
  }>(),
  {
    type: 'text',
    placeholder: '',
    error: '',
    hint: '',
    label: '',
    maxlength: undefined,
    autocomplete: undefined,
  },
);

const emit = defineEmits<{ 'update:modelValue': [value: string] }>();

const generatedId = `mm-input-${useId()}`;
const inputId = computed(() => props.id || generatedId);

function onInput(event: Event) {
  emit('update:modelValue', (event.target as HTMLInputElement).value);
}
</script>

<style scoped>
.mm-field {
  display: flex;
  flex-direction: column;
  gap: 7px;
  min-width: 0;
}

.mm-field__control {
  position: relative;
  min-width: 0;
}
.mm-field__reveal {
  position: absolute;
  right: 4px;
  top: 50%;
  transform: translateY(-50%);
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  padding: 0;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--mm-muted);
}
.mm-field__reveal:hover {
  background: var(--mm-canvas);
  color: var(--mm-ink);
}
.mm-field__reveal .mm-icon {
  width: 20px;
  height: 20px;
}
.has-reveal .mm-field__input {
  padding-right: 48px;
}

.mm-field__label {
  font-size: var(--mm-font-s);
  font-weight: 600;
  color: var(--mm-ink);
}

.mm-field__input {
  min-height: 46px;
  width: 100%;
  min-width: 0;
  padding: 0 var(--mm-space-3);
  border: 1px solid #c8cdc5;
  border-radius: var(--mm-radius-m);
  background-color: var(--mm-white);
  transition:
    border-color 0.15s,
    box-shadow 0.15s;
}
.mm-field__input:focus {
  border-color: var(--mm-primary);
  box-shadow: 0 0 0 3px var(--mm-accent-soft);
}
.mm-field__input:disabled {
  background: var(--mm-canvas);
  cursor: not-allowed;
}
@media (max-width: 600px) {
  .mm-field__input {
    font-size: 16px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .mm-field__input {
    transition: none;
  }
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
