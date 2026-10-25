import {
  navigation,
  Screen,
  Stack,
  Tab,
  Tabs,
  type OptionsBottomTab,
} from '@apexnative/navigation';
import type { ControlValues } from '../../sections/controls';
import { sectionControls, type Section } from '../../sections/scenario';
import { icons } from '../../ui/icons';
import { buildOptions } from './optionsCode';

/** The options of a tab that are the Inbox tab's only: its badge, its dot and its symbol. */
const INBOX_ONLY = ['badge', 'badgeColor', 'dotIndicator', 'icon'] as const;

/** `bottomTab` of the panel split in what every tab takes (colors, font) and what only the Inbox tab takes. */
export function splitTabOptions(bottomTab: OptionsBottomTab = {}) {
  const everyTab: OptionsBottomTab = { ...bottomTab };
  const inbox: OptionsBottomTab = {};
  for (const key of INBOX_ONLY) {
    if (bottomTab[key] !== undefined) {
      (inbox as Record<string, unknown>)[key] = bottomTab[key];
      delete everyTab[key];
    }
  }
  return { everyTab, inbox };
}

/**
 * Shows a demo app of three tabs as the root, with the options of the Tabs panel: `bottomTabs` on the tab bar, the
 * colors and the font of `bottomTab` on every tab, its badge, dot and symbol on the Inbox tab. Each tab has a button
 * back to the Showcase. With `customRow` each tab draws its item with `TabItemCell`, and the tabs have buttons for a
 * push, a sheet and a modal over them.
 */
export function openTabsDemo(section: Section, values: ControlValues) {
  const options = buildOptions(sectionControls(section), values);
  const { everyTab, inbox } = splitTabOptions(options.bottomTab);
  // The screens go over tabs that hide on scroll: their rows end above the tabs themselves.
  const tabsHideOnScroll = options.bottomTabs?.hideOnScroll === true;
  const customRow = options.bottomTabs?.customRow !== undefined;
  const cell = (title: string): OptionsBottomTab =>
    customRow ? { component: { name: 'TabItemCell', passProps: { title } } } : {};
  return navigation.switchTo(
    Tabs(
      Tab(Stack(Screen('TabsDemo', { title: 'Home', tabsHideOnScroll, customRow })))
        .label('Home')
        .icon({ ios: 'house', android: icons.home })
        .options({ ...everyTab, ...cell('Home') }),
      Tab(Stack(Screen('TabsDemo', { title: 'Inbox', tabsHideOnScroll, customRow })))
        .label('Inbox')
        .icon({ ios: 'tray', android: icons.bell })
        .options({ ...everyTab, ...inbox, ...cell('Inbox') }),
      // An image icon, not a symbol: on iOS 26 only an image takes iconColor on a tab that is not selected.
      Tab(Stack(Screen('TabsDemo', { title: 'Profile', tabsHideOnScroll, customRow })))
        .label('Profile')
        .icon(icons.person)
        .options({ ...everyTab, ...cell('Profile') })
    ).options(options.bottomTabs ?? {})
  );
}
