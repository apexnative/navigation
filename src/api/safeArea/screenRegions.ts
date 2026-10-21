import ANNTurboModule from '../../adapters/NativeANNTurboModule';
import { onNativeEvent } from '../../engine/native';
import type { ScreenRegionsChangedEvent } from '../../interfaces/Events';

/** A part of a screen in its coordinates: points on iOS, dp on Android. */
export interface ReservedRegion {
  x: number;
  y: number;
  width: number;
  height: number;
}

/** The parts of a screen the hardware takes. */
export interface ReservedRegions {
  /** Where the screen divides: the fold of a partly folded device. Keep controls and text off it. */
  division: ReservedRegion[];
  /** Where hardware covers the screen: a camera, a hinge that hides what is under it. */
  occlusion: ReservedRegion[];
}

const NONE: ReservedRegions = Object.freeze({ division: [], occlusion: [] });

// The regions of the screens read so far, by screen id, until the screen unmounts.
const regionsById = new Map<string, ReservedRegions>();
const listeners = new Map<string, Set<() => void>>();
let listening = false;

/**
 * The regions of a screen: the last ones the native side sent, or read from it at once, so a screen's first render
 * has them; none before its first layout and where the platform has none.
 */
export function getScreenRegions(screenId: string): ReservedRegions {
  listen();
  const known = regionsById.get(screenId);
  if (known) return known;
  const read = readNative(screenId);
  if (!read) return NONE;
  regionsById.set(screenId, read);
  return read;
}

/** Calls `onChange` when the screen's regions change; returns the function that stops it. */
export function subscribeScreenRegions(screenId: string, onChange: () => void): () => void {
  listen();
  const own = listeners.get(screenId) ?? new Set<() => void>();
  own.add(onChange);
  listeners.set(screenId, own);
  return () => {
    own.delete(onChange);
    if (own.size === 0) listeners.delete(screenId);
  };
}

/** The screen unmounted: its regions go. */
export function forgetScreenRegions(screenId: string) {
  regionsById.delete(screenId);
  listeners.delete(screenId);
}

function listen() {
  if (listening) return;
  listening = true;
  onNativeEvent<ScreenRegionsChangedEvent>('ScreenRegionsChanged', ({ componentId, ...regions }) => {
    // Only the screens that read their regions keep them.
    if (!regionsById.has(componentId) && !listeners.has(componentId)) return;
    const next = toRegions(regions);
    const current = regionsById.get(componentId);
    if (current && sameRegions(current, next)) return;
    regionsById.set(componentId, next);
    listeners.get(componentId)?.forEach((onChange) => onChange());
  });
}

function readNative(screenId: string): ReservedRegions | undefined {
  // No native module in tests with the mock.
  const read = ANNTurboModule?.getScreenRegions?.(screenId) as Partial<ReservedRegions> | undefined;
  return read && Array.isArray(read.division) ? toRegions(read) : undefined;
}

function toRegions(value: Partial<ReservedRegions>): ReservedRegions {
  return { division: value.division ?? [], occlusion: value.occlusion ?? [] };
}

function sameRegions(a: ReservedRegions, b: ReservedRegions): boolean {
  return sameList(a.division, b.division) && sameList(a.occlusion, b.occlusion);
}

function sameList(a: ReservedRegion[], b: ReservedRegion[]): boolean {
  return (
    a.length === b.length &&
    a.every(
      (region, index) =>
        region.x === b[index]!.x &&
        region.y === b[index]!.y &&
        region.width === b[index]!.width &&
        region.height === b[index]!.height
    )
  );
}
