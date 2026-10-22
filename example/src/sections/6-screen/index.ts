import type { Section } from '../scenario';
import safeArea from './6.1-safe-area';
import statusBar from './6.2-status-bar';
import orientation from './6.3-orientation';
import homeIndicatorAndGestures from './6.4-home-indicator-and-gestures';
import keyboardShortcuts from './6.5-keyboard-shortcuts';
import background from './6.6-background';
import androidSystemBars from './6.7-android-system-bars';
import direction from './6.8-direction';
import reservedRegions from './6.9-reserved-regions';
import hinge from './6.10-hinge';
import plainPush from './6.11-plain-push';

/** Section 6: the screen itself — its safe area, the status bar, orientation, gestures, keys and background. */
export const screenSection: Section = {
  id: 6,
  title: 'Screen',
  intro:
    'What a screen sets for itself: where its content goes (safe area), the status bar, the orientations it allows, the home indicator and system gestures, keyboard shortcuts, its background, the system bars of Android, the direction of the app, and the first push of an app as it is written.',
  scenarios: [
    safeArea,
    statusBar,
    orientation,
    homeIndicatorAndGestures,
    keyboardShortcuts,
    background,
    androidSystemBars,
    direction,
    reservedRegions,
    hinge,
    plainPush,
  ],
};
