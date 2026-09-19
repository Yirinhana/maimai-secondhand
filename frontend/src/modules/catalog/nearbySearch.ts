export interface NearbyOrigin {
  latitude: number;
  longitude: number;
  label: string;
}
const prefix = 'maimai-nearby-origin-v1:';
// Coordinates stay in this tab, are isolated by account, and expire after 30 minutes.
export function readNearbyOrigin(userId: number): NearbyOrigin | null {
  if (!userId) return null;
  try {
    const saved = JSON.parse(
      sessionStorage.getItem(prefix + userId) || 'null',
    );
    const age = Date.now() - saved?.savedAt;
    const value = saved?.origin;
    if (
      !Number.isFinite(age) ||
      age < 0 ||
      age > 30 * 60 * 1000 ||
      !value ||
      !Number.isFinite(value.latitude) ||
      Math.abs(value.latitude) > 90 ||
      !Number.isFinite(value.longitude) ||
      Math.abs(value.longitude) > 180 ||
      typeof value.label !== 'string' ||
      value.label.length > 300
    )
      return null;
    return {
      latitude: value.latitude,
      longitude: value.longitude,
      label: value.label,
    };
  } catch {
    return null;
  }
}
export function saveNearbyOrigin(
  userId: number,
  origin: NearbyOrigin | null,
) {
  if (!userId) return;
  try {
    if (origin)
      sessionStorage.setItem(
        prefix + userId,
        JSON.stringify({ origin, savedAt: Date.now() }),
      );
    else sessionStorage.removeItem(prefix + userId);
  } catch {
    /* Optional storage must not prevent searching. */
  }
}
