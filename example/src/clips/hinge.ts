import { useEffect } from 'react';
import { type Hinge, useHinge } from '@apexnative/navigation';
import { emit } from './bus';

let current: Hinge | null = null;

/** The last hinge the root screen of an iPhone Duo clip saw: a pose the device is already in goes on at once. */
export function lastHinge(): Hinge | null {
  return current;
}

/**
 * Sends the hinge to the steps of the iPhone Duo clips, which wait for the device to open or fold. The root screen of
 * each such clip calls it, so it stays mounted under the screens the clip pushes.
 */
export function useHingeForClips() {
  const hinge = useHinge();
  useEffect(() => {
    current = hinge;
    emit('hinge', hinge);
  }, [hinge]);
}
