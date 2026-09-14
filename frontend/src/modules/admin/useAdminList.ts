import {computed,onMounted,ref,type Ref} from 'vue'
import {get,post,type ApiError} from '../../shared/api'
import type {TotalPage} from '../../shared/types'
export function useAdminList<T>(path:string,query:()=>Record<string,string> = ()=>({})) {
  const items=ref<T[]>([]) as Ref<T[]>,page=ref(0),total=ref(0),loading=ref(false),busy=ref(false),error=ref('')
  const pages=computed(()=>Math.ceil(total.value/15))
  async function load(){loading.value=true;error.value='';try{const r=await get<TotalPage<T>>(path,{...query(),page:page.value,size:15});items.value=r.content;total.value=r.total}catch(e){error.value=(e as ApiError).message}finally{loading.value=false}}
  async function action(url:string,body:unknown){if(busy.value)return;busy.value=true;error.value='';try{await post(url,body);await load()}catch(e){error.value=(e as ApiError).message}finally{busy.value=false}}
  onMounted(load)
  return {items,page,pages,total,loading,busy,error,load,action}
}
