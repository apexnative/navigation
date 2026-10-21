import type { ComponentType, ReactNode } from 'react';
import type { SlotDestroyedEvent } from '../interfaces/Events';
import { onNativeEvent } from './native';

/**
 * Wraps the whole app once, in one React tree with every screen: what it holds (a store, a query client, a theme)
 * lives as long as the app does, whatever the navigation does.
 */
export type AppProvider = ComponentType<{ children: ReactNode }>;

/** The module the native side runs the app's one React tree as. */
export const SLOT_HOST_MODULE = 'apexnative.slots';

// The name a screen goes to the native side with: it gets an empty container there instead of a React root, and the
// slot with its id shows it.
const SLOT_PREFIX = 'slot:';

/** Where the native side laid a screen out before its push (Android), in dp. */
export interface SlotFrame {
  width: number;
  height: number;
  /** The padding of its safe area view. */
  padding: { top: number; right: number; bottom: number; left: number };
}

/** A screen of the app's React tree, shown in the native container with its id. */
export interface Slot {
  id: string;
  name: string;
  /** Tells apart two screens shown under one id, so the later one starts with a state of its own. */
  key: string;
  /** Renders nothing yet: the native side lays the screen out first. */
  held?: boolean;
  /** The screen was laid out before its push: the slot renders at this size, the screen at once with its padding. */
  frame?: SlotFrame;
}

export interface SlotsState {
  provider?: AppProvider;
  slots: readonly Slot[];
}

let state: SlotsState = { slots: [] };
const listeners = new Set<() => void>();
let enabled = false;
let shown = 0;
// The slots of the command running now, so a failed command takes them back.
let collecting: string[] | undefined;
// The pushes waiting for React to commit their slot, by the slot's key.
const commits = new Map<string, { resolve: () => void; reject: (error: Error) => void }>();
// A provider that holds its children back (a gate, a suspense above the screens) never commits the slot: its push goes
// after this time anyway, as a push that doesn't wait for its screen.
const COMMIT_WAIT_LIMIT = 500;

/** The app's provider and its screens, for the host's tree. */
export const slotStore = {
  subscribe(listener: () => void) {
    listeners.add(listener);
    return () => {
      listeners.delete(listener);
    };
  },
  get: (): SlotsState => state,
};

function setState(next: SlotsState) {
  state = next;
  listeners.forEach((listener) => listener());
}

/**
 * Renders the screens of the app in one React tree under `provider`: every command marks the screens of the app it
 * shows, and the native side gives each an empty container that the slot with the screen's id fills.
 */
export function enableSlots(provider: AppProvider) {
  if (state.provider !== provider) setState({ ...state, provider });
  if (enabled) return;
  enabled = true;
  onNativeEvent<SlotDestroyedEvent>('SlotDestroyed', ({ componentId }) =>
    removeSlots([componentId])
  );
}

/** Runs a command; when it fails, the slots of the screens it was to show go. */
export function withSlots<T>(command: () => Promise<T>): Promise<T> {
  if (!enabled) return command();
  const added: string[] = [];
  const outer = collecting;
  collecting = added;
  let result: Promise<T>;
  try {
    result = command();
  } catch (error) {
    removeSlots(added);
    throw error;
  } finally {
    collecting = outer;
  }
  return result.catch((error) => {
    removeSlots(added);
    throw error;
  });
}

/** The name a React screen of the app goes to the native side with: in one tree, its slot's. */
export function slotName(id: string, name: string): string {
  if (!enabled) return name;
  const slot = { id, name, key: `${id}#${++shown}` };
  setState({ ...state, slots: [...state.slots.filter((other) => other.id !== id), slot] });
  collecting?.push(id);
  return SLOT_PREFIX + name;
}

/** Whether the app's one tree shows a screen with this id: its slot is there, whatever renders in it now. */
export function hasSlot(id: string): boolean {
  return state.slots.some((slot) => slot.id === id);
}

/** Whether the native side gets this screen as a slot of the app's React tree. */
export function isSlotName(nativeName: string | undefined): boolean {
  return nativeName?.startsWith(SLOT_PREFIX) === true;
}

/** The app's name of a screen, from the name it went to the native side with. */
export function screenNameOf(nativeName: string): string {
  return isSlotName(nativeName) ? nativeName.slice(SLOT_PREFIX.length) : nativeName;
}

/** The slot renders nothing until `showSlot`: the native side lays its screen out first. */
export function holdSlot(id: string) {
  updateSlot(id, { held: true });
}

/**
 * Shows a held slot. With the frame its screen was laid out in, it renders at that size, and the promise resolves once
 * React committed it: the push may go then. Without, it renders as any slot, and the promise resolves at once.
 */
export function showSlot(id: string, frame?: SlotFrame): Promise<void> {
  const slot = state.slots.find((other) => other.id === id);
  if (!slot) return Promise.reject(new Error(`the screen '${id}' is no longer shown`));
  if (!frame) {
    updateSlot(id, { held: false });
    return Promise.resolve();
  }
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => slotCommitted(slot.key), COMMIT_WAIT_LIMIT);
    const done = () => clearTimeout(timer);
    commits.set(slot.key, {
      resolve: () => {
        done();
        resolve();
      },
      reject: (error) => {
        done();
        reject(error);
      },
    });
    updateSlot(id, { held: false, frame });
  });
}

/** React committed the slot with this key at its frame. */
export function slotCommitted(key: string) {
  commits.get(key)?.resolve();
  commits.delete(key);
}

/** `[top, right, bottom, left]` of a screen laid out before its push; undefined for any other. */
export function preparedPadding(id: string): number[] | undefined {
  const padding = state.slots.find((slot) => slot.id === id)?.frame?.padding;
  return padding && [padding.top, padding.right, padding.bottom, padding.left];
}

/** The screens with these ids are not shown, or no longer: their slots go. */
export function removeSlots(ids: string[]) {
  const removed = state.slots.filter(({ id }) => ids.includes(id));
  if (removed.length === 0) return;
  // A push that waited for one of them goes no further.
  removed.forEach(({ id, key }) => {
    commits.get(key)?.reject(new Error(`the screen '${id}' is no longer shown`));
    commits.delete(key);
  });
  setState({ ...state, slots: state.slots.filter(({ id }) => !ids.includes(id)) });
}

function updateSlot(id: string, change: Partial<Slot>) {
  setState({
    ...state,
    slots: state.slots.map((slot) => (slot.id === id ? { ...slot, ...change } : slot)),
  });
}
