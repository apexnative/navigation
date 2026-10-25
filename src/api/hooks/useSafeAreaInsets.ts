import { useCallback, useSyncExternalStore } from 'react';
import { type EdgeInsets, getScreenInsets, subscribeScreenInsets } from '../safeArea/screenInsets';
import { useScreen } from './screenHooks';

/**
 * How much of this screen's content the bars and the system still cover on each side, past what the screen keeps its
 * content off itself (`layout.safeAreaEdges`): 0 on an edge the screen pads, the whole inset on an edge its content goes
 * under — the bottom by default (the home indicator, the navigation bar, a tab bar or toolbar the screen goes under),
 * under a top bar, under the vertical bar at the side of iPhone Duo, every edge with `safeAreaEdges: []`. A list adds
 * exactly this to its own padding. The native side measures every screen on its own, also the screens of
 * `appProvider`.
 *
 * @example
 * const insets = useSafeAreaInsets();
 * <ScrollView contentContainerStyle={{ paddingBottom: 16 + insets.bottom }} />
 */
export function useSafeAreaInsets(): EdgeInsets {
  const { id } = useScreen();
  const subscribe = useCallback(
    (onChange: () => void) => subscribeScreenInsets(id, onChange),
    [id]
  );
  return useSyncExternalStore(subscribe, () => getScreenInsets(id));
}
