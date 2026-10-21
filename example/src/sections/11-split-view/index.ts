import type { Section } from '../scenario';
import columns from './11.1-columns';
import threeColumns from './11.2-three-columns';
import inspector from './11.3-inspector';
import buttonsAndGesture from './11.4-buttons-and-gesture';
import collapsed from './11.5-collapsed';
import androidPanes from './11.6-android-panes';
import pair from './11.7-pair';

/** Section 11: a split view, shown as a demo app. */
export const splitViewSection: Section = {
  id: 11,
  title: 'Split view',
  intro:
    'Columns side by side on iPad: a sidebar, a middle column, Details and, on iPadOS 26, an inspector. Show switches the app to a demo with these options; Back to the Showcase in Library comes back here. On iPhone the columns collapse into one stack. On Android Library and Details are panes: side by side in landscape, one over the other in portrait.',
  scenarios: [columns, threeColumns, inspector, buttonsAndGesture, collapsed, androidPanes, pair],
  demo: 'splitView',
};
