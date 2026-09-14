<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref } from 'vue';
import {
  loadAmap,
  loadMapPlugins,
  type AMapSdk,
  type MapInstance,
  type MarkerInstance,
  type LngLat,
  type Place,
} from '../maps/amap';
import MmButton from './MmButton.vue';
export interface SelectedAddress {
  region: string;
  detail: string;
  fullAddress: string;
  longitude: number;
  latitude: number;
  coordinateSystem: 'GCJ-02';
}
const emit = defineEmits<{ select: [address: SelectedAddress] }>();
const opened = ref(false),
  busy = ref(false),
  error = ref(''),
  query = ref(''),
  places = ref<Place[]>([]),
  chosen = ref<SelectedAddress | null>(null),
  container = ref<HTMLElement | null>(null);
let sdk: AMapSdk | null = null,
  map: MapInstance | null = null,
  marker: MarkerInstance | null = null,
  disposed = false;
async function open() {
  opened.value = true;
  busy.value = true;
  error.value = '';
  try {
    sdk = await loadAmap();
    await loadMapPlugins(sdk);
    await nextTick();
    if (disposed || !container.value) return;
    map?.destroy();
    map = new sdk.Map(container.value, {
      zoom: 11,
      center: [121.4737, 31.2304],
      viewMode: '2D',
    });
    map.on('click', (event) => selectPoint(event.lnglat));
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    busy.value = false;
  }
}
function selectPoint(position: LngLat) {
  if (!sdk || !map) return;
  error.value = '';
  chosen.value = null;
  if (!marker) {
    marker = new sdk.Marker({ position });
    map.add(marker);
  } else marker.setPosition(position);
  map.setCenter(position);
  const geocoder = new sdk.Geocoder();
  geocoder.getAddress(position, (status, result) => {
    if (disposed) return;
    if (status !== 'complete' || !result.regeocode) {
      error.value = '该位置地址解析失败，可更换地点或手动填写。';
      return;
    }
    const a = result.regeocode,
      parts = a.addressComponent;
    const region = [
      parts.province,
      typeof parts.city === 'string' && parts.city !== parts.province
        ? parts.city
        : '',
      parts.district,
    ]
      .filter(Boolean)
      .join(' ');
    chosen.value = {
      region,
      detail: a.formattedAddress,
      fullAddress: a.formattedAddress,
      longitude: position.getLng(),
      latitude: position.getLat(),
      coordinateSystem: 'GCJ-02',
    };
  });
}
function search() {
  if (!sdk || !query.value.trim()) return;
  busy.value = true;
  error.value = '';
  places.value = [];
  const searcher = new sdk.PlaceSearch({
    pageSize: 6,
    pageIndex: 1,
    extensions: 'base',
  });
  searcher.search(query.value.trim(), (status, result) => {
    busy.value = false;
    if (disposed) return;
    if (status === 'complete' && result.poiList) {
      places.value = result.poiList.pois.filter((p) => p.location);
      if (!places.value.length) error.value = '没有找到地点，请增加城市名称。';
    } else error.value = '地点查询暂不可用，请确认地图授权或手动填写。';
  });
}
function locate() {
  if (!sdk) return;
  busy.value = true;
  error.value = '';
  const geolocation = new sdk.Geolocation({
    enableHighAccuracy: true,
    timeout: 10000,
    convert: true,
  });
  geolocation.getCurrentPosition((status, result) => {
    busy.value = false;
    if (disposed) return;
    if (status === 'complete' && result.position) {
      selectPoint(result.position);
      map?.setZoom(16);
    } else error.value = '未获得定位权限或定位失败，仍可搜索地点和手动填写。';
  });
}
function choose() {
  if (chosen.value) emit('select', chosen.value);
}
onBeforeUnmount(() => {
  disposed = true;
  map?.destroy();
  map = null;
});
</script>
<template>
  <section class="mm-stack">
    <MmButton v-if="!opened" variant="ghost" @click="open"
      >使用地图辅助选址</MmButton
    ><template v-else
      ><p class="mm-muted">
        地图由高德提供。点击定位才会请求位置权限；地址仍需核对门牌号，地图距离不用于计算快递运费。
      </p>
      <div class="mm-actions">
        <input
          v-model="query"
          class="mm-select"
          style="flex: 1; min-width: 160px"
          aria-label="搜索城市或地点"
          placeholder="例如：上海人民广场"
          @keydown.enter.prevent="search"
        /><MmButton variant="ghost" :disabled="busy || !sdk" @click="search"
          >搜索地点</MmButton
        ><MmButton variant="ghost" :disabled="busy || !sdk" @click="locate"
          >使用我的位置</MmButton
        >
      </div>
      <p v-if="busy" role="status">地图服务处理中…</p>
      <p v-if="error" class="mm-error" role="alert">{{ error }}</p>
      <div
        ref="container"
        style="
          height: 300px;
          min-width: 0;
          border-radius: 12px;
          overflow: hidden;
          background: var(--mm-canvas);
        "
        aria-label="地点选择地图"
      />
      <ul class="mm-stack">
        <li v-for="(p, index) in places" :key="index">
          <MmButton variant="ghost" @click="selectPoint(p.location)"
            >{{ p.name }} ·
            {{ typeof p.address === 'string' ? p.address : '' }}</MmButton
          >
        </li>
      </ul>
      <div v-if="chosen" class="mm-panel">
        <p>{{ chosen.fullAddress }}</p>
        <MmButton @click="choose">使用此地址</MmButton>
      </div></template
    >
  </section>
</template>
