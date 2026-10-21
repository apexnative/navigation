import { useSyncExternalStore } from 'react';

/** What an entry of the journal is about. */
export type JournalKind = 'event' | 'state' | 'command' | 'error';

export interface JournalEntry {
  id: number;
  time: string;
  kind: JournalKind;
  title: string;
  detail?: string;
}

const LIMIT = 300;
let entries: readonly JournalEntry[] = [];
let lastId = 0;
const listeners = new Set<() => void>();

function timeNow() {
  const now = new Date();
  const pad = (value: number, size = 2) => String(value).padStart(size, '0');
  return `${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}.${pad(now.getMilliseconds(), 3)}`;
}

function notify() {
  listeners.forEach((listener) => listener());
}

/** Adds an entry, newest first. */
export function log(kind: JournalKind, title: string, detail?: unknown) {
  const text =
    detail === undefined ? undefined : typeof detail === 'string' ? detail : JSON.stringify(detail);
  entries = [{ id: ++lastId, time: timeNow(), kind, title, detail: text }, ...entries].slice(
    0,
    LIMIT
  );
  notify();
}

export function clearJournal() {
  entries = [];
  notify();
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

/** The entries, newest first, for code outside React (checks through the debugger). */
export function journalEntries(): readonly JournalEntry[] {
  return entries;
}

/** The entries, newest first; the component renders again on every new one. */
export function useJournal(): readonly JournalEntry[] {
  return useSyncExternalStore(subscribe, () => entries);
}
