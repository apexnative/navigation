import { useLayoutEffect, useRef } from 'react';
import { Platform } from 'react-native';
import { useScreenOptions } from '@apexnative/navigation';
import { dynamic } from '../../ui/theme';

/**
 * What a panel shows: Details pushed, in a modal and as a list; one thing of its own (a modal, a demo of tabs) with
 * Show; or nothing, when its groups have buttons of their own.
 */
export type PanelMode = 'details' | 'show' | 'none';

/** What the buttons of a panel do. */
export interface PanelActions {
  push: () => unknown;
  modal: () => unknown;
  list: () => unknown;
  show: () => unknown;
  reset: () => unknown;
}

/**
 * The buttons of a panel in the stack's toolbar, as text: Push, Modal, List (a plain list with the same options) and,
 * on the far side, Reset; or Show and Reset. A toolbar keeps each text button in a capsule of its own. The toolbar
 * keeps the same buttons while the values change; a press runs what the panel has at that moment. The toolbar is
 * iOS's: on Android the panel has a footer instead.
 */
export function usePanelToolbar(actions: PanelActions, mode: PanelMode) {
  const latest = useRef(actions);
  useLayoutEffect(() => {
    latest.current = actions;
  });
  useScreenOptions((s) =>
    Platform.OS !== 'ios' || mode === 'none'
      ? s
      : s.toolbar({
          buttons:
            mode === 'show'
              ? [
                  // The id is panel-modal: a popover of the modals section points at it.
                  {
                    id: 'panel-modal',
                    text: 'Show',
                    color: dynamic.primary,
                    onPress: () => latest.current.show(),
                  },
                  { systemItem: 'flexibleSpace' },
                  {
                    id: 'panel-reset',
                    text: 'Reset',
                    color: dynamic.danger,
                    onPress: () => latest.current.reset(),
                  },
                ]
              : [
                  {
                    id: 'panel-push',
                    text: 'Push',
                    color: dynamic.primary,
                    onPress: () => latest.current.push(),
                  },
                  {
                    id: 'panel-modal',
                    text: 'Modal',
                    color: dynamic.primary,
                    onPress: () => latest.current.modal(),
                  },
                  {
                    id: 'panel-list',
                    text: 'List',
                    color: dynamic.primary,
                    onPress: () => latest.current.list(),
                  },
                  { systemItem: 'flexibleSpace' },
                  {
                    id: 'panel-reset',
                    text: 'Reset',
                    color: dynamic.danger,
                    onPress: () => latest.current.reset(),
                  },
                ],
        })
  );
}
