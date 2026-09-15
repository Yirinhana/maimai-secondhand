<script setup lang="ts">
import { reactive, ref } from 'vue';
import { post } from '../../../shared/api';
import type { Address } from '../../../shared/types';
import MapPicker from '../../../shared/components/MapPicker.vue';
const emit = defineEmits<{ saved: [id: number] }>();
const open = ref(false),
  busy = ref(false),
  error = ref('');
const fields = reactive({
  receiver: '',
  phone: '',
  region: '',
  detail: '',
  isDefault: false,
});
async function save() {
  if (busy.value) return;
  error.value = '';
  if (
    ![fields.receiver, fields.phone, fields.region, fields.detail].every((v) =>
      v.trim(),
    )
  ) {
    error.value = '请填写姓名、电话、地区和详细地址。';
    return;
  }
  busy.value = true;
  try {
    const created = await post<Address>('/me/addresses', {
      ...fields,
      receiver: fields.receiver.trim(),
      phone: fields.phone.trim(),
      region: fields.region.trim(),
      detail: fields.detail.trim(),
    });
    emit('saved', created.id);
    open.value = false;
    Object.assign(fields, {
      receiver: '',
      phone: '',
      region: '',
      detail: '',
      isDefault: false,
    });
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <div class="checkout-address-editor">
    <button
      type="button"
      class="mm-text-link"
      :aria-expanded="open"
      @click="open = !open"
    >
      {{ open ? '取消新增地址' : '＋ 在这里新增收货地址' }}
    </button>
    <fieldset v-if="open" :disabled="busy">
      <legend>新增收货地址</legend>
      <div @keydown.enter.prevent.stop="save">
        <label
          >收件人<input
            v-model="fields.receiver"
            autocomplete="shipping name"
            maxlength="50" /></label
        ><label
          >联系电话<input
            v-model="fields.phone"
            autocomplete="shipping tel"
            inputmode="tel"
            maxlength="20" /></label
        ><label
          >省市区<input
            v-model="fields.region"
            autocomplete="shipping address-level2"
            maxlength="100" /></label
        ><label
          >详细地址<input
            v-model="fields.detail"
            autocomplete="shipping street-address"
            maxlength="200"
            placeholder="街道、楼栋和门牌号"
        /></label>
      </div>
      <MapPicker
        @select="
          fields.region = $event.region;
          fields.detail = $event.detail;
        "
      />
      <p v-if="error" class="mm-error" role="alert">{{ error }}</p>
      <button type="button" class="mm-button" @click="save">
        {{ busy ? '保存中…' : '保存并选用此地址' }}
      </button>
    </fieldset>
  </div>
</template>
<style scoped>
.checkout-address-editor > button {
  padding: 10px 0;
  background: none;
  border: 0;
  font-size: 13px;
}
fieldset {
  border: 1px solid var(--mm-border);
  border-radius: 8px;
  padding: 18px;
  display: grid;
  gap: 16px;
  margin-top: 8px;
}
legend {
  padding: 0 8px;
  font-size: 14px;
  font-weight: 650;
}
fieldset > div {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}
label {
  display: grid;
  gap: 6px;
  font-size: 12px;
}
input {
  width: 100%;
  min-width: 0;
  border: 1px solid var(--mm-border);
  border-radius: 5px;
  padding: 10px;
}
@media (max-width: 600px) {
  fieldset > div {
    grid-template-columns: 1fr;
  }
}
</style>
