// In development React records a `performance.measure` for every render of a component, for the performance tracks of
// React DevTools, and React Native keeps every entry: a long session of navigation runs out of memory. A trace that
// records takes the entries as they come, so dropping them from the buffer loses nothing.

// React names the measure of a component's render after the component with this mark in front; nothing else does.
const RENDER_MARK = '​';

// The part of React Native's Performance API this uses.
interface Measures {
  getEntriesByType?(type: 'measure'): readonly { name: string }[];
  clearMeasures?(name?: string): void;
}

/** Drops React's measures of component renders (development only); the app's own measures stay. */
export function dropReactRenderMeasures(): void {
  if (!__DEV__) return;
  const performance = (globalThis as { performance?: Measures }).performance;
  if (!performance?.getEntriesByType || !performance.clearMeasures) return;
  const names = new Set<string>();
  for (const entry of performance.getEntriesByType('measure')) {
    if (entry.name.startsWith(RENDER_MARK)) names.add(entry.name);
  }
  names.forEach((name) => performance.clearMeasures?.(name));
}
