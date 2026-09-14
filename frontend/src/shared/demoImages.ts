// DataSeeder uses seed-ID names; normal user uploads have UUID filenames.
export function isDemoProductImage(path?: string | null): boolean {
  return /^\/uploads\/products\/seed-[1-9]\d*\.jpg(?:[?#].*)?$/.test(
    path ?? '',
  );
}
