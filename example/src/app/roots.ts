import { Screen, Stack, Tab, Tabs } from '@apexnative/navigation';
import { icons } from '../ui/icons';
import { tabFonts } from './barFonts';

function showcaseTab<Name extends string>(
  screen: Name,
  label: string,
  icon: keyof typeof icons,
  sfSymbol: string
) {
  return Tab(Stack(screen))
    .label(label)
    .icon({ ios: sfSymbol, android: icons[icon] })
    .options(tabFonts);
}

/** The roots `navigation.switchTo` takes by name. */
export const roots = {
  /** The Showcase itself. */
  showcase: Tabs(
    showcaseTab('Sections', 'Sections', 'stack', 'square.stack.3d.up'),
    showcaseTab('Examples', 'Examples', 'star', 'sparkles'),
    showcaseTab('Search', 'Search', 'search', 'magnifyingglass').ios((tab) => tab.role('search')),
    showcaseTab('Journal', 'Journal', 'bell', 'list.bullet.rectangle'),
    showcaseTab('Settings', 'Settings', 'gear', 'gearshape')
  ),
  /** Signed out (section 19). */
  login: Stack('LoginDemo'),
  /** Signed in, made from the name of the user (section 19). */
  welcome: (name: string) => Stack(Screen('WelcomeDemo', { name })),
  /** A native screen first (section 18.7). */
  nativeFirst: Stack(Screen('UsePropsDemo', { first: true })),
};
