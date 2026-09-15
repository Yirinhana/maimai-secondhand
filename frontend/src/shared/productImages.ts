export type ProductImageSize = 'thumb' | 'card' | 'detail' | 'original';
const widths = { thumb: 160, card: 480, detail: 960 } as const;
export function productImageUrl(
  src: string | null | undefined,
  size: ProductImageSize,
): string | undefined {
  if (!src) return undefined;
  if (size === 'original') return src;
  const match =
    /^\/uploads\/products\/([A-Za-z0-9][A-Za-z0-9_-]{0,120}\.(?:jpg|jpeg|png))$/.exec(
      src,
    );
  return match
    ? `/uploads/thumbnails/products/${widths[size]}/${match[1]}`
    : src;
}
