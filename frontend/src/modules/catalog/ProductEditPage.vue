<template>
  <div class="mm-edit">
    <h1 class="mm-edit__heading">{{ isEdit ? '编辑商品' : '发布闲置' }}</h1>

    <p v-if="pageLoading" class="mm-edit__loading">加载中……</p>
    <div v-else-if="pageError" role="alert">
      <EmptyState title="无法编辑该商品" :description="pageError" />
    </div>

    <template v-else>
      <p v-if="isEdit && product && (product.status === 'ON_SALE' || product.status === 'CHANGES_REVIEW')" class="mm-edit__review-banner" role="alert">
        关键信息（标题/描述/分类/图片/成色/缺陷/价格/交付/运费）修改后将重新审核，审核期间暂停新成交；仅调整库存不会触发重新审核。
      </p>

      <form class="mm-edit__form" @submit.prevent>
        <MmCard title="基本信息" class="mm-edit__section">
          <div class="mm-edit__fields">
            <MmInput
              v-model="form.title"
              label="标题"
              :maxlength="120"
              placeholder="一句话说明这件闲置"
              :error="fieldErrors.title"
            />
            <label class="mm-edit__field">
              <span class="mm-edit__label">分类</span>
              <select v-model="form.categoryId">
                <option value="" disabled>请选择分类</option>
                <option v-for="opt in categoryOptions" :key="opt.id" :value="String(opt.id)">
                  {{ opt.name }}
                </option>
              </select>

              <span v-if="fieldErrors.categoryId" class="mm-edit__field-error" role="alert">{{ fieldErrors.categoryId }}</span>
            </label>
            <label class="mm-edit__field">
              <span class="mm-edit__label">商品描述</span>
              <textarea v-model="form.description" rows="5" placeholder="品牌、入手渠道、使用情况、出手原因……"></textarea>
            </label>
            <label class="mm-edit__field">
              <span class="mm-edit__label">成色</span>
              <select v-model="form.condition">
                <option v-for="(text, value) in CONDITION_TEXT" :key="value" :value="value">
                  {{ text }}
                </option>
              </select>
            </label>
            <label class="mm-edit__field">
              <span class="mm-edit__label">缺陷说明（会向买家显著展示，请如实填写）</span>
              <textarea
                v-model="form.defects"
                rows="3"
                maxlength="500"
                placeholder="划痕、磕碰、功能异常等；无缺陷可留空"
              ></textarea>
            </label>
          </div>
        </MmCard>

        <MmCard title="价格与库存" class="mm-edit__section">
          <div class="mm-edit__fields mm-edit__fields--row">
            <label class="mm-edit__field">
              <span class="mm-edit__label">价格（元）</span>
              <input v-model="form.priceYuan" type="number" min="0.01" step="0.01" placeholder="如 25.00" />
              <span v-if="fieldErrors.priceYuan" class="mm-edit__field-error" role="alert">{{ fieldErrors.priceYuan }}</span>
            </label>
            <label v-if="!isEdit" class="mm-edit__field">
              <span class="mm-edit__label">库存</span>
              <input v-model.number="form.stock" type="number" min="0" step="1" />
              <span v-if="fieldErrors.stock" class="mm-edit__field-error" role="alert">{{ fieldErrors.stock }}</span>
            </label>
            <label class="mm-edit__field">
              <span class="mm-edit__label">所在地区</span>
              <input v-model="form.region" type="text" maxlength="100" placeholder="如：成都市 武侯区" />
              <span v-if="fieldErrors.region" class="mm-edit__field-error" role="alert">{{ fieldErrors.region }}</span>
            </label>
          </div>
        </MmCard>

        <MmCard title="交付与售后" class="mm-edit__section">
          <div class="mm-edit__fields">
            <div class="mm-stack">
              <strong>公开交接区域（选填）</strong><p class="mm-edit__hint">用于附近商品排序。请选择公共场所，不要标记私人住址；地图距离不用于计算快递运费。</p>
              <MapPicker @select="setPublicLocation" />
              <p v-if="form.latitude!==null&&form.longitude!==null" class="mm-edit__hint">已选择公开位置（GCJ-02）：{{form.latitude.toFixed(5)}}，{{form.longitude.toFixed(5)}} <button type="button" @click="form.latitude=null;form.longitude=null">清除位置</button></p>
            </div>
            <fieldset class="mm-edit__fieldset">
              <legend class="mm-edit__label">交付方式（可多选）</legend>
              <label class="mm-edit__checkbox">
                <input v-model="form.deliveryMethods" type="checkbox" value="EXPRESS" /> 快递
              </label>
              <label class="mm-edit__checkbox">
                <input v-model="form.deliveryMethods" type="checkbox" value="MEETUP" /> 面交
              </label>
              <span v-if="fieldErrors.deliveryMethods" class="mm-edit__field-error" role="alert">{{ fieldErrors.deliveryMethods }}</span>
            </fieldset>
            <label class="mm-edit__field">
              <span class="mm-edit__label">快递运费（元，买家下单时一并结算；填 0 为免运费）</span>
              <input v-model="form.freightYuan" type="number" min="0" step="0.01" placeholder="如 8.00" />
              <span v-if="fieldErrors.freightYuan" class="mm-edit__field-error" role="alert">{{ fieldErrors.freightYuan }}</span>
            </label>
            <fieldset class="mm-edit__fieldset">
              <legend class="mm-edit__label">快递配送地区</legend>
              <label class="mm-edit__checkbox"><input v-model="limitShipping" type="checkbox" />仅配送到选定省份（不勾选表示全国）</label>
              <div v-if="limitShipping" class="mm-edit__province-list"><label v-for="province in provinceOptions" :key="province" class="mm-edit__checkbox"><input v-model="form.shippingProvinces" type="checkbox" :value="province" />{{province}}</label></div>
              <p v-if="provinceError" class="mm-edit__field-error">{{provinceError}}</p>
              <p v-if="limitShipping" class="mm-edit__hint">请至少选一项。收货地区须填写省级名称，系统将在下单时核对是否支持配送。</p>
            </fieldset>
            <label class="mm-edit__field">
              <span class="mm-edit__label">退货承诺（选填）</span>
              <textarea
                v-model="form.returnPromise"
                rows="2"
                maxlength="200"
                placeholder="如：签收后 48 小时内与描述不符可退货"
              ></textarea>
            </label>
          </div>
        </MmCard>

        <!-- 图片管理：仅编辑模式（新建需先保存草稿获得商品 ID） -->
        <MmCard title="商品图片" class="mm-edit__section">
          <template v-if="isEdit">
            <p class="mm-edit__hint">合计 1~9 张，单张 ≤5MB，仅支持 JPG / PNG，首图将作为封面；图片最多 1600 万像素，服务端会压缩最长边。</p>
            <ul v-if="product?.images.length" class="mm-edit__images">
              <li v-for="(img, i) in product.images" :key="img.id" class="mm-edit__image">
                <ItemImage :src="img.path" :alt="`${form.title || '商品'} 图片 ${i + 1}`" loading="lazy" />
                <button
                  type="button"
                  class="mm-edit__image-delete"
                  :disabled="imageLoading"
                  @click="deleteImage(img.id)"
                >
                  删除
                </button>
              </li>
            </ul>
            <div class="mm-edit__upload">
              <input
                ref="fileInput"
                type="file"
                accept="image/jpeg,image/png"
                multiple
                @change="onFilesSelected"
              />
              <MmButton variant="ghost" :loading="imageLoading" :disabled="!pendingFiles.length" @click="uploadImages">
                上传所选图片
              </MmButton>
            </div>
            <p v-if="imageError" class="mm-edit__field-error" role="alert">{{ imageError }}</p>
          </template>
          <p v-else class="mm-edit__hint">新建商品需先「保存草稿」，之后即可在本页上传图片。</p>
        </MmCard>

        <!-- 库存调整：编辑模式独立入口 -->
        <MmCard v-if="isEdit && product" title="库存调整" class="mm-edit__section">
          <p class="mm-edit__hint">
            当前可售 {{ product.stockAvailable }} 件。增减库存不会触发重新审核。
          </p>
          <div class="mm-edit__stock-row">
            <label class="mm-edit__field">
              <span class="mm-edit__label">调整量（正数增加 / 负数减少）</span>
              <input v-model.number="stockDelta" type="number" step="1" placeholder="如 2 或 -1" />
            </label>
            <MmButton variant="ghost" :loading="stockLoading" @click="adjustStock">调整库存</MmButton>
          </div>
          <p v-if="stockMessage" class="mm-edit__ok" role="status">{{ stockMessage }}</p>
          <p v-if="stockError" class="mm-edit__field-error" role="alert">{{ stockError }}</p>
        </MmCard>

        <p v-if="saveError" class="mm-edit__field-error" role="alert">{{ saveError }}</p>
        <p v-if="saveMessage" class="mm-edit__ok" role="status">{{ saveMessage }}</p>
        <div class="mm-edit__actions">
          <template v-if="!isEdit">
            <MmButton variant="ghost" :loading="savingDraft" @click="save(false)">保存草稿</MmButton>
            <p class="mm-edit__hint">保存草稿并上传实拍图片后，即可提交审核。</p>
          </template>
          <template v-else>
            <MmButton variant="ghost" :loading="savingDraft" @click="save(false)">保存修改</MmButton>
            <MmButton
              v-if="product && (product.status === 'DRAFT' || product.status === 'REJECTED')"
              :loading="savingSubmit"
              @click="saveAndSubmit"
            >
              保存并提交审核
            </MmButton>
          </template>
          <MmButton variant="ghost" @click="router.push('/seller/products')">返回我的商品</MmButton>
        </div>
      </form>
      <ProductRevisions v-if="productId" :key="productId" :product-id="productId" />
    </template>
  </div>
</template>

<script setup lang="ts">
import ItemImage from '../../shared/components/ItemImage.vue'
import MapPicker,{type SelectedAddress} from '../../shared/components/MapPicker.vue'
import ProductRevisions from './ProductRevisions.vue'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { del, get, post, put, upload, type ApiError } from '../../shared/api'
import EmptyState from '../../shared/components/EmptyState.vue'
import MmButton from '../../shared/components/MmButton.vue'
import MmCard from '../../shared/components/MmCard.vue'
import MmInput from '../../shared/components/MmInput.vue'
import {
  CONDITION_TEXT,
  type Category,
  type Condition,
  type DeliveryMethod,
  type ProductDetail,
  type ProductUpdateRequest,
  type ProductUpsertRequest,
} from '../../shared/types'

const MAX_IMAGE_BYTES = 5 * 1024 * 1024
const MAX_IMAGES = 9
const IMAGE_TYPES = ['image/jpeg', 'image/png']

const route = useRoute()
const router = useRouter()

const productId = computed(() => {
  const id = route.params.id
  return typeof id === 'string' ? Number(id) : null
})
const isEdit = computed(() => productId.value !== null)

interface CategoryOption {
  id: number
  name: string
}

const categoryOptions = ref<CategoryOption[]>([])
const product = ref<ProductDetail | null>(null)
const pageLoading = ref(true)
const pageError = ref('')

const form = reactive({
  title: '',
  categoryId: '',
  description: '',
  condition: 'GOOD' as Condition,
  defects: '',
  priceYuan: '',
  stock: 1,
  region: '',
  deliveryMethods: ['EXPRESS'] as DeliveryMethod[],
  freightYuan: '0',
  returnPromise: '',
  latitude:null as number|null,
  longitude:null as number|null,
  shippingProvinces:[] as string[],
})
const limitShipping=ref(false),provinceOptions=ref<string[]>([]),provinceError=ref('')
function setPublicLocation(address:SelectedAddress){form.latitude=address.latitude;form.longitude=address.longitude;form.region=address.region}

const fieldErrors = reactive({
  title: '',
  categoryId: '',
  priceYuan: '',
  stock: '',
  region: '',
  deliveryMethods: '',
  freightYuan: '',
})

const savingDraft = ref(false)
const savingSubmit = ref(false)
const saveError = ref('')
const saveMessage = ref('')

const fileInput = ref<HTMLInputElement | null>(null)
const pendingFiles = ref<File[]>([])
const imageLoading = ref(false)
const imageError = ref('')

const stockDelta = ref<number | null>(null)
const stockLoading = ref(false)
const stockError = ref('')
const stockMessage = ref('')

function flattenCategories(tree: Category[], prefix: string, out: CategoryOption[]) {
  for (const c of tree) {
    out.push({ id: c.id, name: prefix + c.name })
    if (c.children?.length) flattenCategories(c.children, prefix + c.name + ' / ', out)
  }
}

function fillForm(p: ProductDetail) {
  form.title = p.title
  form.description = p.description ?? ''
  form.condition = p.condition
  form.defects = p.defects ?? ''
  form.priceYuan = (p.priceCents / 100).toFixed(2)
  form.region = p.region
  form.deliveryMethods = [...p.deliveryMethods]
  form.freightYuan = (p.freightCents / 100).toFixed(2)
  form.returnPromise = p.returnPromise ?? ''
  form.latitude=p.latitude??null;form.longitude=p.longitude??null
  form.shippingProvinces=[...(p.shippingProvinces??[])];limitShipping.value=form.shippingProvinces.length>0
}

async function loadProduct() {
  if (productId.value === null) return
  const p = await get<ProductDetail>(`/seller/products/${productId.value}`)
  product.value = p
  fillForm(p)
  form.categoryId = String(p.categoryId ?? '')
}

onMounted(async () => {
  try{provinceOptions.value=await get<string[]>('/shipping-provinces')}catch(e){provinceError.value=(e as ApiError).message||'配送省份列表加载失败'}
  try {
    const tree = await get<Category[]>('/categories')
    const out: CategoryOption[] = []
    flattenCategories(tree, '', out)
    categoryOptions.value = out
  } catch (e) {
    pageError.value = (e as ApiError).message || '分类加载失败'
    pageLoading.value = false
    return
  }
  if (isEdit.value) {
    try {
      await loadProduct()
    } catch (e) {
      pageError.value = (e as ApiError).message || '商品加载失败'
    }
  }
  pageLoading.value = false
})

function yuanToCents(yuan: string): number | null {
  const n = Number(yuan)
  if (!yuan.trim() || !Number.isFinite(n)) return null
  return Math.round(n * 100)
}

function validate(): boolean {
  if(limitShipping.value&&form.shippingProvinces.length===0){saveError.value='请至少选择一个配送省份，或取消限定配送';return false}
  fieldErrors.title = form.title.trim() ? '' : '请填写标题'
  fieldErrors.categoryId = form.categoryId ? '' : '请选择分类'
  const price = yuanToCents(form.priceYuan)
  fieldErrors.priceYuan = price !== null && price >= 1 ? '' : '请输入有效价格（≥0.01 元）'
  fieldErrors.stock = !isEdit.value && (!Number.isInteger(form.stock) || form.stock < 0) ? '库存须为不小于 0 的整数' : ''
  fieldErrors.region = form.region.trim() ? '' : '请填写所在地区'
  fieldErrors.deliveryMethods = form.deliveryMethods.length > 0 ? '' : '请至少选择一种交付方式'
  const freight = yuanToCents(form.freightYuan)
  fieldErrors.freightYuan = freight !== null && freight >= 0 ? '' : '请输入有效运费（≥0）'
  return Object.values(fieldErrors).every((e) => !e)
}

function buildBasePayload() {
  return {
    title: form.title.trim(),
    categoryId: Number(form.categoryId),
    description: form.description,
    condition: form.condition,
    defects: form.defects.trim() || null,
    priceCents: yuanToCents(form.priceYuan) ?? 0,
    region: form.region.trim(),
    deliveryMethods: form.deliveryMethods,
    freightCents: yuanToCents(form.freightYuan) ?? 0,
    returnPromise: form.returnPromise.trim() || null,
    latitude:form.latitude,longitude:form.longitude,
    shippingProvinces:limitShipping.value?[...form.shippingProvinces]:[],
  }
}

async function save(submit: boolean) {
  if (!validate()) return
  saveError.value = ''
  saveMessage.value = ''
  if (submit) savingSubmit.value = true
  else savingDraft.value = true
  try {
    if (!isEdit.value) {
      const payload: ProductUpsertRequest = { ...buildBasePayload(), stock: form.stock, submit }
      const res = await post<{ id: number }>('/seller/products', payload)
      if (submit) {
        saveMessage.value = '已提交审核，可在「我的商品」查看进度'
        router.replace(`/seller/products`)
      } else {
        // 保存草稿后进入编辑模式，可继续上传图片
        router.replace(`/publish/${res.id}`)
      }
    } else {
      const payload: ProductUpdateRequest = buildBasePayload()
      await put(`/seller/products/${productId.value}`, payload)
      await loadProduct()
      saveMessage.value =
        product.value?.status === 'CHANGES_REVIEW'
          ? '已保存，商品进入变更审核，审核期间暂停新成交'
          : '已保存'
    }
  } catch (e) {
    saveError.value = (e as ApiError).message || '保存失败，请稍后重试'
  } finally {
    savingDraft.value = false
    savingSubmit.value = false
  }
}

async function saveAndSubmit() {
  if (!validate() || productId.value === null) return
  saveError.value = ''
  saveMessage.value = ''
  savingSubmit.value = true
  try {
    await put(`/seller/products/${productId.value}`, buildBasePayload() satisfies ProductUpdateRequest)
    await post(`/seller/products/${productId.value}/submit`)
    saveMessage.value = '已提交审核，可在「我的商品」查看进度'
    await loadProduct()
  } catch (e) {
    saveError.value = (e as ApiError).message || '提交失败，请稍后重试'
  } finally {
    savingSubmit.value = false
  }
}

function onFilesSelected(event: Event) {
  const files = Array.from((event.target as HTMLInputElement).files ?? [])
  imageError.value = ''
  const existing = product.value?.images.length ?? 0
  if (existing + files.length > MAX_IMAGES) {
    imageError.value = `图片合计最多 ${MAX_IMAGES} 张（当前已有 ${existing} 张）`
    pendingFiles.value = []
    return
  }
  for (const f of files) {
    if (!IMAGE_TYPES.includes(f.type)) {
      imageError.value = `「${f.name}」格式不支持，仅 JPG / PNG`
      pendingFiles.value = []
      return
    }
    if (f.size > MAX_IMAGE_BYTES) {
      imageError.value = `「${f.name}」超过 5MB，请压缩后再上传`
      pendingFiles.value = []
      return
    }
  }
  pendingFiles.value = files
}

async function uploadImages() {
  if (productId.value === null || pendingFiles.value.length === 0) return
  imageLoading.value = true
  imageError.value = ''
  try {
    const data = new FormData()
    for (const f of pendingFiles.value) data.append('files', f)
    await upload(`/seller/products/${productId.value}/images`, data)
    pendingFiles.value = []
    if (fileInput.value) fileInput.value.value = ''
    await loadProduct()
  } catch (e) {
    imageError.value = (e as ApiError).message || '上传失败，请稍后重试'
  } finally {
    imageLoading.value = false
  }
}

async function deleteImage(imageId: number) {
  if (productId.value === null) return
  imageLoading.value = true
  imageError.value = ''
  try {
    await del(`/seller/products/${productId.value}/images/${imageId}`)
    await loadProduct()
  } catch (e) {
    imageError.value = (e as ApiError).message || '删除失败，请稍后重试'
  } finally {
    imageLoading.value = false
  }
}

async function adjustStock() {
  if (productId.value === null || stockDelta.value === null) return
  const delta = Math.floor(stockDelta.value)
  if (!Number.isFinite(delta) || delta === 0) {
    stockError.value = '请输入非零整数调整量'
    return
  }
  stockLoading.value = true
  stockError.value = ''
  stockMessage.value = ''
  try {
    await put(`/seller/products/${productId.value}/stock`, { delta })
    stockMessage.value = `库存已调整（${delta > 0 ? '+' : ''}${delta}）`
    stockDelta.value = null
    await loadProduct()
  } catch (e) {
    stockError.value = (e as ApiError).message || '库存调整失败'
  } finally {
    stockLoading.value = false
  }
}
watch(()=>route.params.id,async(id,previous)=>{
  if(id===previous||!id)return
  pageLoading.value=true;pageError.value=''
  try{await loadProduct()}catch(e){pageError.value=(e as ApiError).message}
  finally{pageLoading.value=false}
})
</script>

<style scoped>
.mm-edit__province-list{display:grid;grid-template-columns:repeat(auto-fit,minmax(160px,1fr));gap:8px;max-height:240px;overflow:auto;padding:12px;background:var(--mm-canvas);border-radius:10px}
.mm-edit {
  max-width: 760px;
  margin: 0 auto;
  padding: var(--mm-space-4) var(--mm-space-4) var(--mm-space-6);
}

.mm-edit__heading {
  font-size: var(--mm-font-xl);
  margin-bottom: var(--mm-space-4);
}

.mm-edit__loading {
  color: var(--mm-muted);
  text-align: center;
  padding: var(--mm-space-6) 0;
}

.mm-edit__review-banner {
  background-color: #FBF2E4;
  color: var(--mm-warning);
  border: 1px solid var(--mm-warning);
  border-radius: var(--mm-radius-m);
  padding: var(--mm-space-3) var(--mm-space-4);
  margin-bottom: var(--mm-space-4);
  font-size: var(--mm-font-s);
  font-weight: 600;
}

.mm-edit__section {
  margin-bottom: var(--mm-space-4);
}

.mm-edit__fields {
  display: flex;
  flex-direction: column;
  gap: var(--mm-space-4);
}

.mm-edit__fields--row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: var(--mm-space-4);
}

.mm-edit__field {
  display: flex;
  flex-direction: column;
  gap: var(--mm-space-1);
}

.mm-edit__label {
  font-size: var(--mm-font-s);
  font-weight: 600;
  color: var(--mm-ink);
}

.mm-edit__field input,
.mm-edit__field select,
.mm-edit__field textarea {
  min-height: 40px;
  padding: var(--mm-space-2) var(--mm-space-3);
  border: 1px solid var(--mm-border);
  border-radius: var(--mm-radius-m);
  background-color: var(--mm-white);
}

.mm-edit__field textarea {
  resize: vertical;
}

.mm-edit__fieldset {
  border: none;
  padding: 0;
  margin: 0;
  display: flex;
  align-items: center;
  gap: var(--mm-space-4);
  flex-wrap: wrap;
}

.mm-edit__checkbox {
  display: inline-flex;
  align-items: center;
  gap: var(--mm-space-2);
}

.mm-edit__hint {
  font-size: var(--mm-font-s);
  color: var(--mm-muted);
}

.mm-edit__field-error {
  font-size: var(--mm-font-s);
  color: var(--mm-danger);
}

.mm-edit__ok {
  font-size: var(--mm-font-s);
  color: var(--mm-success);
}

.mm-edit__images {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(96px, 1fr));
  gap: var(--mm-space-3);
  margin: var(--mm-space-3) 0;
}

.mm-edit__image {
  display: flex;
  flex-direction: column;
  gap: var(--mm-space-1);
}

.mm-edit__image img {
  width: 100%;
  aspect-ratio: 1;
  object-fit: cover;
  border: 1px solid var(--mm-border);
  border-radius: var(--mm-radius-m);
}

.mm-edit__image-delete {
  border: 1px solid var(--mm-danger);
  background: none;
  color: var(--mm-danger);
  border-radius: var(--mm-radius-s);
  font-size: var(--mm-font-s);
  padding: var(--mm-space-1) 0;
}

.mm-edit__upload {
  display: flex;
  align-items: center;
  gap: var(--mm-space-3);
  flex-wrap: wrap;
}

.mm-edit__stock-row {
  display: flex;
  align-items: flex-end;
  gap: var(--mm-space-3);
  flex-wrap: wrap;
  margin-top: var(--mm-space-3);
}

.mm-edit__stock-row .mm-edit__field {
  flex: 1;
  min-width: 180px;
}

.mm-edit__actions {
  display: flex;
  gap: var(--mm-space-3);
  flex-wrap: wrap;
}
</style>
