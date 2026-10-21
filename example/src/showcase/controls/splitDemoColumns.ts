import type { OptionsSplitView } from '@apexnative/navigation';

/**
 * The columns of the split view demo: a middle one with an option of three columns, an inspector with an option of
 * it, and the compact layout unless `compactTopColumn` asks UIKit to collapse the columns itself.
 */
export function splitDemoColumns(split: OptionsSplitView) {
  return {
    supplementary:
      split.supplementary !== undefined ||
      split.showsSecondaryOnlyButton !== undefined ||
      split.displayMode?.startsWith('two') === true,
    inspector: split.inspector !== undefined,
    compact: split.compactTopColumn === undefined,
  };
}
