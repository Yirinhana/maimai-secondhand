export interface SupportResource {
  kind: "PAGE" | "PRODUCT";
  title: string;
  path: string;
  priceCents: number | null;
  imagePath: string | null;
  detail: string;
  experience: boolean;
}

// Model text is never interpreted as a link. Accept only explicit site destinations.
export function safeSupportPath(path: string): boolean {
  if (/[\\\r\n]/.test(path) || !path.startsWith("/") || path.startsWith("//"))
    return false;
  const target = path.split("?")[0];
  return (
    [
      "/policies",
      "/orders",
      "/support",
      "/publish",
      "/search",
      "/community/demands",
      "/official/buying-and-selling-guide",
    ].includes(target ?? "") || /^\/products\/\d+$/.test(target ?? "")
  );
}
