import {ref} from 'vue'

export const confirmation=ref<{message:string;resolve:(accepted:boolean)=>void}|null>(null)
export function askConfirmation(message:string):Promise<boolean> {
  if(confirmation.value)return Promise.resolve(false)
  return new Promise(resolve=>{confirmation.value={message,resolve}})
}
export function answerConfirmation(accepted:boolean) {
  const current=confirmation.value
  confirmation.value=null
  current?.resolve(accepted)
}
