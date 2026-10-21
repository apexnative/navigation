import { useSyncExternalStore } from 'react';
import { getPendingLink, subscribePendingLink } from '../linking/pendingLinks';
import type { PendingLink } from '../linking/types';

/**
 * The link that waits for `when` of `linking`, or null: the component renders again when one starts waiting and when
 * it runs or is forgotten. For a screen that offers it after a sign-in.
 */
export function usePendingLink(): PendingLink | null {
  return useSyncExternalStore(subscribePendingLink, getPendingLink);
}
