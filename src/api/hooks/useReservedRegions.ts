import { useCallback, useSyncExternalStore } from 'react';
import {
  getScreenRegions,
  type ReservedRegions,
  subscribeScreenRegions,
} from '../safeArea/screenRegions';
import { useScreen } from './screenHooks';

/**
 * The parts of this screen the hardware takes, in the screen's coordinates: `division`, the fold of a partly folded
 * device, and `occlusion`, a camera or a hinge over the screen. Only what is active now: the fold of a device opened
 * flat is not there. Empty where the platform has none (before iOS 27.1, a device without a fold). Changes as the
 * device folds, as the safe area does.
 *
 * @example
 * const { division } = useReservedRegions();
 * const fold = division[0]; // keep the buttons on one side of it
 */
export function useReservedRegions(): ReservedRegions {
  const { id } = useScreen();
  const subscribe = useCallback(
    (onChange: () => void) => subscribeScreenRegions(id, onChange),
    [id]
  );
  return useSyncExternalStore(subscribe, () => getScreenRegions(id));
}
