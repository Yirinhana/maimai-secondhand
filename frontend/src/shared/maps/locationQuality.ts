import type { LocationResult } from './amap';

/** IP locations and uncertain desktop fixes must not become delivery addresses. */
export function locationProblem(result: LocationResult): string | null {
  if (
    !result.position ||
    !Number.isFinite(result.position.getLng()) ||
    !Number.isFinite(result.position.getLat()) ||
    Math.abs(result.position.getLng()) > 180 ||
    Math.abs(result.position.getLat()) > 90
  )
    return '没有获得有效位置，请搜索附近地标或在地图上选点。';
  if (
    /ip/i.test(result.location_type ?? '') ||
    !Number.isFinite(result.accuracy) ||
    Number(result.accuracy) <= 0
  )
    return '定位服务没有提供可靠精度，未填入地址。请搜索附近地标，再在地图上确认位置。';
  if (Number(result.accuracy) > 200)
    return `本次定位误差约 ${Math.round(Number(result.accuracy))} 米，未填入地址。请搜索具体地点或手动选点。`;
  return null;
}
