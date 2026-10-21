import { useSyncExternalStore } from 'react';
import { initialValues, type ControlValues } from '../sections/controls';
import { sectionControls, type Section } from '../sections/scenario';

// The values set on each section's panel, for as long as the app runs: back on the panel they are still there.
const values = new Map<number, ControlValues>();
const listeners = new Set<() => void>();

function current(section: Section): ControlValues {
  let found = values.get(section.id);
  if (!found) {
    found = initialValues(sectionControls(section));
    values.set(section.id, found);
  }
  return found;
}

function notify() {
  listeners.forEach((listener) => listener());
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

/** The values of the section's controls, a setter of one by its path, and a reset to the first ones. */
export function useSectionValues(
  section: Section
): [ControlValues, (path: string, value: unknown) => void, () => void] {
  const snapshot = useSyncExternalStore(subscribe, () => current(section));
  const set = (path: string, value: unknown) => {
    values.set(section.id, { ...current(section), [path]: value });
    notify();
  };
  const reset = () => {
    values.delete(section.id);
    notify();
  };
  return [snapshot, set, reset];
}
