import { useEffect } from 'react';
import { nativeScreen, navigation, useAppContext } from '@apexnative/navigation';
import { setNativeFirst } from '../app/launchMode';
import { bundleStartedAt } from '../app/launchTiming';
import { log } from '../showcase/journal';
import { counter, useCount } from './counter';

/** The props a command gives the native screen of 18.4–18.7. */
export interface UsePropsDemoProps {
  /** It is the first root of the app (18.7). */
  first?: boolean;
}

const ITEMS = ['Profile', 'Settings', 'Details'];

/**
 * A native screen (SwiftUI on iOS, Android views) with props from the app: the count of a store, the user of the app's
 * context, and functions it calls in JS — one returns items after a while, one fails, one writes to the Journal.
 */
export const usePropsDemo = nativeScreen<UsePropsDemoProps>('UsePropsDemoScreen', {
  options: (s, props) => s.title(props.first ? 'Native first' : 'useProps').hideBottomTabs(),
  useProps: ({ first }) => {
    const count = useCount();
    const { user } = useAppContext();
    useEffect(() => {
      if (first) {
        log(
          'state',
          'Native first screen: its props are ready',
          `${Date.now() - bundleStartedAt} ms after the bundle started`
        );
      }
    }, [first]);
    return {
      count,
      user: user ?? 'Nobody',
      increment: () => counter.increment(),
      signIn: (name: string) => navigation.setContext((context) => ({ ...context, user: name })),
      signOut: () => navigation.setContext((context) => ({ ...context, user: null })),
      loadItems: () => new Promise<string[]>((resolve) => setTimeout(() => resolve(ITEMS), 300)),
      failToLoad: () => Promise.reject(new Error('The server did not answer')),
      log: (message: string) => log('event', 'Native screen', message),
      ...(first ? { backToShowcase: () => setNativeFirst(false) } : {}),
    };
  },
});
