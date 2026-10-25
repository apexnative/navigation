import { Platform } from 'react-native';
import { useSafeAreaInsets } from '@apexnative/navigation';

/**
 * The side padding of a list: its own and what the bars cover, so the rows stay clear of the vertical bar of iPhone Duo
 * (0 on any other iPhone).
 */
export function useSidePadding(padding = 0): { paddingLeft: number; paddingRight: number } {
  const insets = useSafeAreaInsets();
  return { paddingLeft: padding + insets.left, paddingRight: padding + insets.right };
}

/**
 * The bottom padding of content that goes down to the bottom edge of the window: its own and what covers the edge (the
 * home indicator, the navigation bar), so the last rows and the controls pinned to the bottom stay clear of it.
 */
export function useBottomPadding(padding = 0): { paddingBottom: number } {
  const insets = useSafeAreaInsets();
  return { paddingBottom: padding + insets.bottom };
}

/**
 * The same for a scroll view with `contentInsetAdjustmentBehavior="automatic"`: iOS keeps its last rows clear of the
 * home indicator by itself, on Android they end above the navigation bar here.
 */
export function useScrollBottomPadding(padding = 0): { paddingBottom: number } {
  const bottom = useBottomPadding(padding);
  return Platform.OS === 'android' ? bottom : { paddingBottom: padding };
}

/**
 * The layout of a list screen: on iOS its content goes under the bars and the home indicator too, and the scroll view
 * keeps its rows clear of the top and the bottom by itself; the sides take `useSidePadding`. On Android the screen goes
 * down to the navigation bar too, and the rows end above it with `useScrollBottomPadding`.
 */
export const LIST_LAYOUT = Platform.OS === 'ios' ? { safeAreaEdges: [] } : {};
