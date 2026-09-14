<script setup lang="ts">
import {computed,onMounted,ref} from 'vue'
import {get,post,put,type ApiError} from '../../shared/api'
import MmButton from '../../shared/components/MmButton.vue'
interface CategoryItem{id:number;parentId:number|null;name:string;sort:number;status:'ACTIVE'|'DISABLED'}
const items=ref<CategoryItem[]>([]),editing=ref<number|null>(null),showForm=ref(false),loading=ref(false),busy=ref(false),error=ref(''),hint=ref('')
const blank=()=>({parentId:'' as string,name:'',sort:0,status:'ACTIVE' as 'ACTIVE'|'DISABLED',reason:''})
const form=ref(blank())
const parentChoices=computed(()=>items.value.filter(item=>item.id!==editing.value))
function categoryName(item:CategoryItem){
  const names=[item.name],visited=new Set<number>([item.id]);let parentId=item.parentId
  while(parentId!==null&&!visited.has(parentId)){const parent=items.value.find(c=>c.id===parentId);if(!parent)break;visited.add(parentId);names.unshift(parent.name);parentId=parent.parentId}
  return names.join(' / ')
}
async function load(){loading.value=true;error.value='';try{items.value=await get<CategoryItem[]>('/admin/categories')}catch(e){error.value=(e as ApiError).message}finally{loading.value=false}}
function edit(item?:CategoryItem){editing.value=item?.id??null;form.value=item?{parentId:item.parentId===null?'':String(item.parentId),name:item.name,sort:item.sort,status:item.status,reason:''}:blank();showForm.value=true;hint.value=''}
async function save(){busy.value=true;error.value='';try{
  const payload={parentId:form.value.parentId?Number(form.value.parentId):null,name:form.value.name.trim(),sort:Number(form.value.sort),status:form.value.status,reason:form.value.reason.trim()}
  if(editing.value)await put(`/admin/categories/${editing.value}`,payload);else await post('/admin/categories',payload)
  showForm.value=false;hint.value='分类已保存，停用分类及其子类不可用于新发布。';await load()
}catch(e){error.value=(e as ApiError).message}finally{busy.value=false}}
onMounted(load)
</script>
<template><section class="mm-stack"><div class="mm-actions"><h1>分类维护</h1><MmButton @click="edit()">新增分类</MmButton><MmButton variant="ghost" :disabled="loading" @click="load">刷新</MmButton></div><p class="mm-muted">停用保留分类与历史商品。父分类停用后，其子类也不可新发布或提交审核。</p><p v-if="error" class="mm-error" role="alert">{{error}}</p><p v-if="hint" class="mm-notice" role="status">{{hint}}</p><form v-if="showForm" class="mm-panel mm-form" @submit.prevent="save"><h2>{{editing?'编辑分类':'新增分类'}}</h2><label>名称<input v-model="form.name" required maxlength="50" /></label><label>父分类<select v-model="form.parentId"><option value="">顶级分类</option><option v-for="item in parentChoices" :key="item.id" :value="String(item.id)">{{categoryName(item)}}{{item.status==='DISABLED'?'（已停用）':''}}</option></select></label><label>排序值<input v-model.number="form.sort" type="number" min="0" step="1" required /></label><label>状态<select v-model="form.status"><option value="ACTIVE">启用</option><option value="DISABLED">停用</option></select></label><label>变更原因<textarea v-model="form.reason" required maxlength="500" /></label><div class="mm-actions"><MmButton type="submit" :loading="busy">保存分类</MmButton><MmButton variant="ghost" @click="showForm=false">取消</MmButton></div></form><p v-if="loading">加载中…</p><article v-for="item in items" :key="item.id" class="mm-panel"><strong>{{categoryName(item)}}</strong><p class="mm-muted">{{item.status==='ACTIVE'?'启用':'已停用'}} · 排序 {{item.sort}}</p><MmButton variant="ghost" @click="edit(item)">编辑</MmButton></article></section></template>
