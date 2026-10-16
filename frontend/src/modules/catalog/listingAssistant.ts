import type { Category } from '../../shared/types';

export interface ListingRecognition {
  title: string;
  categoryId: number | null;
  description: string;
  visibleFeatures: string[];
  checklist: string[];
}
export interface ListingFields {
  title: string;
  categoryId: string;
  description: string;
}
export type ListingEdits = Record<keyof ListingFields, number>;
export interface ComparableItem {
  id: number;
  title: string;
  priceCents: number;
  condition: string;
  region: string;
  coverImage: string | null;
  sellerNickname: string;
  experienceSource: string | null;
  url: string;
}
export interface PriceComparison {
  currency: 'CNY';
  priceBasis: 'ASKING';
  sampleCount: number;
  minCents: number | null;
  maxCents: number | null;
  medianCents: number | null;
  matchLevel: 'CATEGORY_KEYWORD' | 'CATEGORY' | 'KEYWORD' | 'NONE';
  keyword: string;
  items: ComparableItem[];
  fetchedAt: string;
  notice: string;
}

export function categoryLeaves(
  tree: Category[],
  prefix = '',
): { id: number; name: string }[] {
  return tree.flatMap((node) => {
    const name = prefix + node.name;
    return node.children?.length
      ? categoryLeaves(node.children, name + ' / ')
      : [{ id: node.id, name }];
  });
}

export function categoryRoot(tree: Category[], selected: string): string {
  if (!selected) return '';
  const contains = (node: Category): boolean =>
    String(node.id) === selected || !!node.children?.some(contains);
  return String(tree.find(contains)?.id ?? '');
}

/** Never overwrite a field the seller touched while the request was running. */
export function blankRecognitionPatch(
  current: ListingFields,
  atStart: ListingFields,
  edits: ListingEdits,
  startEdits: ListingEdits,
  suggestion: ListingRecognition,
  validCategoryIds: number[],
): { patch: Partial<ListingFields>; filled: string[]; kept: string[] } {
  const candidate: ListingFields = {
    title: suggestion.title?.trim().slice(0, 120) ?? '',
    description: suggestion.description?.trim() ?? '',
    categoryId:
      suggestion.categoryId !== null &&
      validCategoryIds.includes(suggestion.categoryId)
        ? String(suggestion.categoryId)
        : '',
  };
  const patch: Partial<ListingFields> = {},
    filled: string[] = [],
    kept: string[] = [];
  const labels = {
    title: '标题',
    categoryId: '分类',
    description: '商品描述',
  };
  for (const key of Object.keys(candidate) as (keyof ListingFields)[]) {
    if (!candidate[key]) continue;
    if (
      !current[key].trim() &&
      !atStart[key].trim() &&
      edits[key] === startEdits[key]
    ) {
      patch[key] = candidate[key];
      filled.push(labels[key]);
    } else kept.push(labels[key]);
  }
  return { patch, filled, kept };
}

/** Samples are asking prices ordered by price, never dated observations. */
export function askingPricePoints(
  items: ComparableItem[],
  currentPriceCents: number | null,
  width = 640,
) {
  const sorted = [...items]
    .filter(
      (item) =>
        Number.isSafeInteger(item.priceCents) && item.priceCents > 0,
    )
    .sort((a, b) => a.priceCents - b.priceCents || a.id - b.id);
  const reference =
    currentPriceCents !== null &&
    Number.isSafeInteger(currentPriceCents) &&
    currentPriceCents > 0
      ? currentPriceCents
      : null;
  const max =
    Math.max(
      100,
      ...sorted.map((item) => item.priceCents),
      reference ?? 0,
    ) * 1.15;
  const left = 61,
    right = width - 24,
    top = 22,
    bottom = 204;
  const y = (cents: number) => bottom - (cents / max) * (bottom - top);
  return {
    sorted,
    max,
    left,
    right,
    top,
    bottom,
    points: sorted.map((item, i) => ({
      item,
      x:
        sorted.length > 1
          ? left + (i * (right - left)) / (sorted.length - 1)
          : (left + right) / 2,
      y: y(item.priceCents),
    })),
    referenceY: reference === null ? null : y(reference),
    ticks: [0, 0.25, 0.5, 0.75, 1].map((ratio) => ({
      cents: max * ratio,
      y: y(max * ratio),
    })),
  };
}
