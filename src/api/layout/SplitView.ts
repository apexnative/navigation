import { Platform } from 'react-native';
import type { Layout } from '../../interfaces/Layout';
import type { Options, OptionsSplitView, OptionsSplitViewColumn } from '../../interfaces/Options';
import { mergeOptions } from '../../engine/mergeOptions';
import {
  BUILD,
  buildContent,
  type LayoutContent,
  type LayoutElement,
  onPlatform,
  type ScreenUse,
  type ShowsScreens,
  type UsesOf,
} from './element';

/** The columns of a split view; a screen alone, by name or `Screen(…)`, gets a stack, but in the inspector. */
export interface SplitViewColumns {
  primary: LayoutContent;
  secondary: LayoutContent;
  /** The middle column (iOS). */
  supplementary?: LayoutContent;
  /** The column at the trailing edge (iOS 26+). */
  inspector?: LayoutContent;
  /** What an iPhone, a narrow window and a narrow Android screen show. */
  compact?: LayoutContent;
}

/**
 * Columns side by side on iPad. On Android the primary and the secondary pane: side by side when the width takes
 * both (a tablet, a phone in landscape); narrower, the compact layout, or without it the secondary pane over the
 * primary one. Android has no supplementary or inspector column.
 */
export class SplitViewElement<Uses extends ScreenUse = ScreenUse> implements LayoutElement<Uses> {
  // No command takes the id of a split view: its screens show and hide its columns (`showColumn`, `hideColumn`).
  constructor(
    private readonly columns: SplitViewColumns,
    private readonly built: Options = {}
  ) {}

  private withSplit(splitView: OptionsSplitView): SplitViewElement<Uses> {
    return new SplitViewElement(this.columns, mergeOptions(this.built, { splitView }));
  }

  displayMode(displayMode: NonNullable<OptionsSplitView['displayMode']>): SplitViewElement<Uses> {
    return this.withSplit({ displayMode });
  }

  splitBehavior(
    splitBehavior: NonNullable<OptionsSplitView['splitBehavior']>
  ): SplitViewElement<Uses> {
    return this.withSplit({ splitBehavior });
  }

  primaryColumn(
    column: OptionsSplitViewColumn & {
      edge?: OptionsSplitView['primaryEdge'];
      background?: OptionsSplitView['primaryBackgroundStyle'];
    }
  ): SplitViewElement<Uses> {
    const { edge, background, ...size } = column;
    return this.withSplit({
      ...size,
      primaryEdge: edge,
      primaryBackgroundStyle: background,
    });
  }

  supplementaryColumn(supplementary: OptionsSplitViewColumn): SplitViewElement<Uses> {
    return this.withSplit({ supplementary });
  }

  inspectorColumn(inspector: NonNullable<OptionsSplitView['inspector']>): SplitViewElement<Uses> {
    return this.withSplit({ inspector });
  }

  displayModeButton(
    displayModeButton: NonNullable<OptionsSplitView['displayModeButton']>
  ): SplitViewElement<Uses> {
    return this.withSplit({ displayModeButton });
  }

  presentsWithGesture(presentsWithGesture: boolean): SplitViewElement<Uses> {
    return this.withSplit({ presentsWithGesture });
  }

  showsSecondaryOnlyButton(showsSecondaryOnlyButton: boolean): SplitViewElement<Uses> {
    return this.withSplit({ showsSecondaryOnlyButton });
  }

  compactTopColumn(
    compactTopColumn: NonNullable<OptionsSplitView['compactTopColumn']>
  ): SplitViewElement<Uses> {
    return this.withSplit({ compactTopColumn });
  }

  /** Modifiers for iOS only. */
  ios(build: (split: SplitViewElement<Uses>) => SplitViewElement<Uses>): SplitViewElement<Uses> {
    return onPlatform<SplitViewElement<Uses>>('ios', this, build);
  }

  /** Modifiers for Android only. */
  android(
    build: (split: SplitViewElement<Uses>) => SplitViewElement<Uses>
  ): SplitViewElement<Uses> {
    return onPlatform<SplitViewElement<Uses>>('android', this, build);
  }

  /** Options of the split view no modifier covers. The options of screens go on the screens. */
  options(options: OptionsSplitView): SplitViewElement<Uses> {
    return this.withSplit(options);
  }

  [BUILD](): Layout {
    const { primary, secondary, compact } = this.columns;
    // Android has no supplementary or inspector column.
    const ios = Platform.OS === 'ios';
    const supplementary = ios ? this.columns.supplementary : undefined;
    const inspector = ios ? this.columns.inspector : undefined;
    // A screen alone gets a stack of its own, on iOS too where UIKit would give it a navigation controller (and
    // leaves one in the compact column without it): the column has a bar and pushes, and the navigation knows the
    // stack.
    const pane = (content: LayoutContent): Layout => {
      const layout = buildContent(content, 'stack');
      return layout.component || layout.externalComponent ? { stack: { children: [layout] } } : layout;
    };
    return {
      splitView: {
        master: pane(primary),
        detail: pane(secondary),
        ...(supplementary && { supplementary: pane(supplementary) }),
        ...(inspector && { inspector: buildContent(inspector, 'screen') }),
        ...(compact && { compact: pane(compact) }),
        options: this.built,
      },
    };
  }
}

// The type of an element carries the screens it shows.
export interface SplitViewElement<Uses extends ScreenUse = ScreenUse> extends ShowsScreens<Uses> {}

export function SplitView<const Columns extends SplitViewColumns>(
  columns: Columns
): SplitViewElement<UsesOf<Columns[keyof Columns]>> {
  return new SplitViewElement(columns);
}
