import { createNavigation, navigation, Root, Screen, Stack } from '@apexnative/navigation';
import { findScenario } from '../sections';
import { log } from '../showcase/journal';
import { ErrorFallback } from './ErrorFallback';
import { LoadingFallback } from './LoadingFallback';
import { initialContext, type AppContext } from './context';
import { nativeFirst, oneTree, takeFailStart } from './launchMode';
import { bundleStartedAt } from './launchTiming';
import { listeners } from './listeners';
import { options } from './options';
import { roots } from './roots';
import { SavedItemsProvider } from './SavedItems';
import { groups, screens } from './screens';
import { showcaseTheme } from './theme';

/** The Showcase: its tabs are the first root. */
export const App = createNavigation({
  screens,
  groups,
  theme: showcaseTheme,
  options,
  context: initialContext,
  // Before the first root, while the splash shows: an app loads what it starts with here and puts it into the context
  // with navigation.setContext (section 19).
  willMount: () => {
    log('state', 'willMount', 'before the first root');
    // Section 14.4: a start that fails shows errorFallback in place of the splash screen; Try again starts again.
    if (takeFailStart()) throw new Error('The start failed on purpose (14.4)');
  },
  didMount: () => {
    log('state', 'didMount', 'the first root shows');
    if (nativeFirst) {
      log(
        'state',
        'Native first screen: shown',
        `${Date.now() - bundleStartedAt} ms after the bundle started`
      );
    }
  },
  listeners,
  roots,
  initialRoot: () => (nativeFirst ? 'nativeFirst' : 'showcase'),
  // One store for the whole app in one tree, a store per screen otherwise (section 16).
  ...(oneTree ? { appProvider: SavedItemsProvider } : { screenProvider: SavedItemsProvider }),
  errorFallback: ErrorFallback,
  loadingFallback: LoadingFallback,
  onError: (error, source) => log('error', `onError: ${source.type}`, String(error)),
  // The links of the Showcase, apexnative://… (section 19.5).
  linking: {
    prefixes: ['apexnative://'],
    routes: {
      // A cold start shows Details over the Showcase at once; the running app pushes it.
      details: {
        start: () => Root(roots.showcase).modal(Stack(Screen('Details'))),
        open: () => navigation.push('Details'),
      },
      // Only `start`: the running app switches to this root.
      'welcome/:name': { start: ({ name }) => ['welcome', name] },
      // A screen name: pushed.
      login: 'LoginDemo',
      // Waits until someone signed in.
      saved: { when: (context) => context.user !== null, open: () => navigation.push('Details') },
      // A scenario's panel, so a check run from a script opens it without taps: apexnative://scenario/2.7.
      'scenario/:id': {
        open: ({ id }) => {
          const found = findScenario(id);
          if (!found) return log('error', 'No scenario for the link', id);
          navigation.push('Section', { props: { id: found.section.id, focus: id } });
        },
      },
    },
    unmatched: (url) => log('error', 'No route for the link', url),
  },
});

declare module '@apexnative/navigation' {
  interface Register {
    app: typeof App;
    context: AppContext;
  }
}
