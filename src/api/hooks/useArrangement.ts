import { useCallback, useSyncExternalStore } from 'react';
import {
  type ArrangementState,
  getArrangementState,
  subscribeArrangementState,
} from '../arrangement/arrangementState';
import { useScreen } from './screenHooks';

/**
 * How this screen's pair (`arrangement` of a screen, or `defineArrangement`) shows now: which view the screen is, how the two lie, whether the
 * screen is over the other one or out of the window. Null for a screen outside a pair. Changes as the window and the
 * fold change, as the safe area does.
 *
 * @example
 * // A list under a player: shorter while the player covers it.
 * const pair = useArrangement();
 * const collapsed = pair?.layout === 'overlay' && !pair.front;
 */
export function useArrangement(): ArrangementState | null {
  const { id } = useScreen();
  const subscribe = useCallback(
    (onChange: () => void) => subscribeArrangementState(id, onChange),
    [id]
  );
  return useSyncExternalStore(subscribe, () => getArrangementState(id));
}
