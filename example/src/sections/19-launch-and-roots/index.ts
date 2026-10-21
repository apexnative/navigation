import type { Section } from '../scenario';
import groups from './19.3-groups';
import deepLinks from './19.5-deep-links';
import launch from './19.4-launch';
import namedRoots from './19.1-named-roots';
import rootWithModal from './19.2-root-with-modal';

/** Section 19: how the app starts and the roots it switches between. */
export const launchSection: Section = {
  id: 19,
  title: 'Launch and roots',
  intro:
    'What createNavigation sets up: the roots of the app by name and with params, a root with a modal over it, screens that share options in groups, what runs as the app starts, and the links that open screens. Each group runs with a button; the code is under it.',
  scenarios: [namedRoots, rootWithModal, groups, launch, deepLinks],
  runsOnly: true,
};
