import { useMemo, useSyncExternalStore } from 'react';
import { useColorScheme } from 'react-native';
import { getAppContext, subscribeAppContext } from '../context/appContext';
import {
  getTheme,
  resolveTheme,
  type ResolvedNavigationTheme,
  subscribeTheme,
} from '../theme/theme';
import type { RegisteredContext } from '../types';

/**
 * The theme of the navigation, its colors for the current appearance: they go straight into styles. The component
 * renders again when `navigation.setTheme` changes the theme, and when the appearance turns light or dark.
 */
export function useNavigationTheme(): ResolvedNavigationTheme {
  const theme = useSyncExternalStore(subscribeTheme, getTheme);
  const scheme = useColorScheme() === 'dark' ? 'dark' : 'light';
  return useMemo(() => resolveTheme(theme, scheme), [theme, scheme]);
}

/**
 * The app's context: `context` of createNavigation, as `navigation.setContext` changed it since. Any component, in any
 * screen. With `select`, the component renders again only when the selected part changes, so a change of the language
 * doesn't render a component that reads the user; without it, on every change.
 */
export function useAppContext<Context = RegisteredContext>(): Context;
export function useAppContext<Selected, Context = RegisteredContext>(
  select: (context: Context) => Selected
): Selected;
export function useAppContext<Selected, Context = RegisteredContext>(
  select?: (context: Context) => Selected
): Selected | Context {
  return useSyncExternalStore(subscribeAppContext, () =>
    select ? select(getAppContext<Context>()) : getAppContext<Context>()
  );
}
