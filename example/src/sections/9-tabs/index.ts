import type { Section } from '../scenario';
import tabBar from './9.1-tab-bar';
import aTab from './9.2-a-tab';
import ios26TabBar from './9.3-ios26-tab-bar';
import ipadSidebar from './9.4-ipad-sidebar';
import androidTabBar from './9.5-android-tab-bar';
import customRow from './9.6-custom-row';

/** Section 9: bottom tabs — the tab bar and its tabs, shown as a demo app. */
export const tabsSection: Section = {
  id: 9,
  title: 'Tabs',
  intro:
    'The tab bar and its tabs. Show switches the app to a demo of three tabs (Home, Inbox, Profile) with these options: the tab bar takes `bottomTabs`, the Inbox tab `bottomTab`. Back to the Showcase on any tab comes back here.',
  scenarios: [tabBar, aTab, ios26TabBar, ipadSidebar, androidTabBar, customRow],
  demo: 'tabs',
};
