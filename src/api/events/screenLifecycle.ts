import { forgetArrangementState } from '../arrangement/arrangementState';
import { forgetScreenInsets } from '../safeArea/screenInsets';
import { forgetScreenRegions } from '../safeArea/screenRegions';
import { forgetAppeared } from '../state/navigationState';
import { clearButtonHandlers } from '../../engine/buttons';
import { forgetTabsScreen } from '../../engine/tabs';

// What runs when a screen of `screens` unmounts, by screen id.
const unmountHandlers = new Map<string, (() => void)[]>();

/** Runs the handler once the screen unmounts: its native screen is gone. */
export function whenScreenUnmounts(screenId: string, handler: () => void) {
  unmountHandlers.set(screenId, [...(unmountHandlers.get(screenId) ?? []), handler]);
}

/** A screen of `screens` unmounted. */
export function screenUnmounted(screenId: string) {
  clearButtonHandlers(screenId);
  forgetTabsScreen(screenId);
  forgetScreenInsets(screenId);
  forgetScreenRegions(screenId);
  forgetArrangementState(screenId);
  forgetAppeared(screenId);
  const handlers = unmountHandlers.get(screenId);
  unmountHandlers.delete(screenId);
  handlers?.forEach((handler) => handler());
}
