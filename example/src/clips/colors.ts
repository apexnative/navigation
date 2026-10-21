import { brand, useTheme } from '../ui';

/** The Showcase's colors, so the clips look like the rest of the site. */
export function useColors() {
  const t = useTheme();
  return {
    background: t.background,
    card: t.card,
    grouped: t.code,
    text: t.text,
    muted: t.textMuted,
    line: t.border,
    tint: t.primary,
  };
}

export type Colors = ReturnType<typeof useColors>;

/** The colors of the tiles and avatars: the brand's. */
export const TILES = [
  brand.coral,
  brand.teal,
  brand.mustard,
  brand.sky,
  brand.lavender,
  brand.orange,
  brand.pink,
  brand.red,
  brand.navy,
];
