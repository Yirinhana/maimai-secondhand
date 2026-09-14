// DataSeeder uses seed-ID names; normal user uploads have UUID filenames.
export function isDemoProductImage(path?: string | null): boolean {
  return /^\/uploads\/products\/(?:seed-[1-9]\d*|demo043-item-\d{3}-(?:front|back|side))\.jpg(?:[?#].*)?$/.test(
    path ?? '',
  );
}

export function demoImageView(path?: string | null): string | null {
  const view = path?.match(
    /^\/uploads\/products\/demo043-item-\d{3}-(front|back|side)\.jpg$/,
  )?.[1];
  return view
    ? ({ front: '正面', back: '背面', side: '侧面' } as Record<string, string>)[
        view
      ]!
    : null;
}
