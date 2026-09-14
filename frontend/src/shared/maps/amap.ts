import {get} from '../api'

export interface LngLat {getLng():number;getLat():number}
export interface Place {name:string;address:string;location:LngLat}
export interface AddressResult {formattedAddress:string;addressComponent:{province:string;city:string|[];district:string}}
export interface MapInstance {on(type:'click',callback:(event:{lnglat:LngLat})=>void):void;setCenter(center:LngLat):void;setZoom(zoom:number):void;add(marker:MarkerInstance):void;destroy():void}
export interface MarkerInstance {setPosition(position:LngLat):void}
export interface GeocoderInstance {getAddress(position:LngLat,callback:(status:string,result:{regeocode?:AddressResult})=>void):void}
export interface PlaceSearchInstance {search(keyword:string,callback:(status:string,result:{poiList?:{pois:Place[]}})=>void):void}
export interface GeolocationInstance {getCurrentPosition(callback:(status:string,result:{position?:LngLat})=>void):void}
export interface AMapSdk {
  Map:new(container:HTMLElement,options:Record<string,unknown>)=>MapInstance
  Marker:new(options:Record<string,unknown>)=>MarkerInstance
  Geocoder:new(options?:Record<string,unknown>)=>GeocoderInstance
  PlaceSearch:new(options?:Record<string,unknown>)=>PlaceSearchInstance
  Geolocation:new(options?:Record<string,unknown>)=>GeolocationInstance
  plugin(names:string[],callback:()=>void):void
}
declare global {interface Window {AMap?:AMapSdk;_AMapSecurityConfig?:{serviceHost:string}}}
let sdkPromise:Promise<AMapSdk>|null=null
export function loadAmap():Promise<AMapSdk> {
  if(window.AMap)return Promise.resolve(window.AMap)
  if(sdkPromise)return sdkPromise
  sdkPromise=(async()=>{
    const config=await get<{enabled:boolean;key?:string;serviceHost:string}>('/maps/js-config')
    if(!config.enabled||!config.key)throw new Error('地图服务尚未配置，仍可手动填写地址。')
    if(config.serviceHost!=='/_AMapService')throw new Error('地图服务地址配置异常')
    window._AMapSecurityConfig={serviceHost:window.location.origin+config.serviceHost}
    return new Promise<AMapSdk>((resolve,reject)=>{
      const script=document.createElement('script')
      const timeout=window.setTimeout(()=>reject(new Error('地图加载超时，请手动填写或稍后重试。')),20000)
      script.src=`https://webapi.amap.com/maps?v=2.0&key=${encodeURIComponent(config.key!)}`
      script.async=true
      script.onload=()=>{window.clearTimeout(timeout);if(window.AMap)resolve(window.AMap);else reject(new Error('地图服务不可用，请检查平台授权。'))}
      script.onerror=()=>{window.clearTimeout(timeout);reject(new Error('地图加载失败，请检查网络或手动填写。'))}
      document.head.appendChild(script)
    })
  })().catch(error=>{sdkPromise=null;throw error})
  return sdkPromise
}

export async function loadMapPlugins(sdk:AMapSdk):Promise<void> {
  await new Promise<void>((resolve,reject)=>{
    const timer=window.setTimeout(()=>reject(new Error('地图地址插件加载超时，请手动填写。')),15000)
    sdk.plugin(['AMap.Geocoder','AMap.PlaceSearch','AMap.Geolocation'],()=>{window.clearTimeout(timer);resolve()})
  })
}
