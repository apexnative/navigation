import { navigation, Screen, SplitView } from '@apexnative/navigation';
import type { ControlValues } from '../../sections/controls';
import { sectionControls, type Section } from '../../sections/scenario';
import { buildOptions } from './optionsCode';
import { splitDemoColumns } from './splitDemoColumns';

/**
 * Shows a demo split view as the root, with the `splitView` options of the Split view panel: Library (folders), the
 * items of a folder with three columns, Details, and Info as the inspector. A pick goes to the next column with
 * `showInColumn`. Library has a button back to the Showcase.
 */
export function openSplitViewDemo(section: Section, values: ControlValues) {
  const options = buildOptions(sectionControls(section), values);
  const split = options.splitView ?? {};
  const columns = splitDemoColumns(split);
  const next = columns.supplementary ? 'supplementary' : 'secondary';
  return navigation.switchTo(
    SplitView({
      primary: Screen('SplitSidebar', { next }),
      ...(columns.supplementary && { supplementary: Screen('SplitItems', {}) }),
      secondary: Screen('SplitDetail', {}),
      ...(columns.inspector && { inspector: Screen('SplitInspector') }),
      // Collapsed into it, showInColumn pushes in it.
      ...(columns.compact && { compact: Screen('SplitSidebar', { next }) }),
    }).options(split)
  );
}
