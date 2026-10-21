import type { PendingLink } from './types';

// The link that waits for `when` of `linking`: one, the latest. The navigation and `usePendingLink` read it here; the
// linking runs it through the opener it gives at its start.
let pending: PendingLink | null = null;
const listeners = new Set<() => void>();
let opener: ((link: PendingLink) => Promise<void>) | undefined;

export function getPendingLink(): PendingLink | null {
  return pending;
}

export function setPendingLink(link: PendingLink | null): void {
  if (pending === link) return;
  pending = link;
  listeners.forEach((listener) => listener());
}

export function subscribePendingLink(listener: () => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

export function setPendingLinkOpener(open: (link: PendingLink) => Promise<void>): void {
  opener = open;
}

/** Runs the waiting link now, whatever `when` says, and forgets it; false without one. */
export async function openPendingLink(): Promise<boolean> {
  const link = pending;
  if (!link || !opener) return false;
  setPendingLink(null);
  await opener(link);
  return true;
}
