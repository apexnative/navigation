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
 * The layout of a list screen: on iOS its content goes under the bars and the home indicator too, and the scroll view
 * keeps its rows clear of the top and the bottom by itself; the sides take `useSidePadding`.
 */
export const LIST_LAYOUT = Platform.OS === 'ios' ? { safeAreaEdges: [] } : {};
