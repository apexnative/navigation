import { topBarSection } from './1-top-bar';
import { barButtonsSection } from './2-bar-buttons';
import { backSection } from './3-back-and-gestures';
import { searchSection } from './4-search';
import { toolbarAndFabSection } from './5-toolbar-and-fab';
import { screenSection } from './6-screen';
import { modalsSection } from './7-modals';
import { overlaysSection } from './8-overlays';
import { tabsSection } from './9-tabs';
import { sideMenuSection } from './10-side-menu';
import { splitViewSection } from './11-split-view';
import { stackSection } from './12-stack';
import { eventsSection } from './13-events-and-hooks';
import { errorsSection } from './14-errors-and-loading';
import { themeSection } from './15-theme-and-context';
import { oneTreeSection } from './16-one-tree';
import { transitionsSection } from './17-transitions';
import { nativeScreensSection } from './18-native-screens';
import { launchSection } from './19-launch-and-roots';
import type { Scenario, Section } from './scenario';

/** Every section of the Showcase, in the order they are read. */
export const sections: Section[] = [
  topBarSection,
  barButtonsSection,
  backSection,
  searchSection,
  toolbarAndFabSection,
  screenSection,
  modalsSection,
  overlaysSection,
  tabsSection,
  sideMenuSection,
  splitViewSection,
  stackSection,
  eventsSection,
  errorsSection,
  themeSection,
  oneTreeSection,
  transitionsSection,
  nativeScreensSection,
  launchSection,
];

export function findSection(id: number): Section | undefined {
  return sections.find((section) => section.id === id);
}

export function findScenario(id: string): { section: Section; scenario: Scenario } | undefined {
  for (const section of sections) {
    const scenario = section.scenarios.find((item) => item.id === id);
    if (scenario) return { section, scenario };
  }
  return undefined;
}

/** The scenarios whose number, title, text, code or keywords have every word of the query. */
export function searchScenarios(query: string): Scenario[] {
  const words = query.toLowerCase().split(/\s+/).filter(Boolean);
  if (words.length === 0) return [];
  return sections.flatMap((section) =>
    section.scenarios.filter((scenario) => {
      const text = [
        scenario.id,
        scenario.title,
        scenario.check,
        ...(scenario.keywords ?? []),
        ...Object.keys(scenario.controls ?? {}),
        ...Object.entries(scenario.code).flat(),
      ]
        .join(' ')
        .toLowerCase();
      return words.every((word) => text.includes(word));
    })
  );
}
