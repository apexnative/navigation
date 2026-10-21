import { useEffect, useLayoutEffect, useRef } from 'react';
import { Platform } from 'react-native';
import { dismissPreview } from '../../engine/commands';
import { nextId } from '../../engine/ids';
import { pushPreview } from '../commands/commands';
import { onNavigationEvent } from '../events/navigationEvents';
import type { ScreenName, ScreenProps } from '../types';
import { useScreen } from './screenHooks';

/** An item of the menu under a preview; with `actions`, a submenu. */
export interface PreviewAction {
  id: string;
  title: string;
  style?: 'default' | 'selected' | 'destructive';
  onPress?: () => void;
  actions?: PreviewAction[];
}

/** What a view previews: the screen with its props, and the menu under it. */
export type PreviewConfig<Name extends ScreenName> = {
  screen: Name;
  actions?: PreviewAction[];
  /** The width of the preview; the width of the screen without it. */
  width?: number;
  /** The height of the preview; the height of the screen without it. */
  height?: number;
} & (Partial<ScreenProps<Name>> extends ScreenProps<Name>
  ? { props?: ScreenProps<Name> }
  : { props: ScreenProps<Name> });

/**
 * (iOS) A long press on the view with `nativeID` in this screen (a right click with a pointer on iPad) shows `screen`
 * small, with `actions` as a menu under it; a tap on the preview pushes the screen. UIKit's context menu. The preview
 * goes when the component unmounts. Elsewhere the view has no preview.
 */
export function usePreview<Name extends ScreenName>(
  nativeID: string,
  config: PreviewConfig<Name>
): void {
  const { id: screenId } = useScreen();
  const {
    screen,
    props,
    actions = [],
    width,
    height,
  } = config as PreviewConfig<ScreenName> & {
    props?: object;
  };
  const latest = useRef(actions);
  useLayoutEffect(() => {
    latest.current = actions;
  });
  // A new preview when what it shows changes; the handlers of the menu are read when it is pressed.
  const shown = JSON.stringify([screen, props ?? null, menuOf(actions), width, height]);
  useEffect(() => {
    if (Platform.OS !== 'ios') return undefined;
    const [name, previewProps, menu, previewWidth, previewHeight] = JSON.parse(shown);
    let active = true;
    let current: string | undefined;
    const stop = onNavigationEvent('buttonPressed', (event) => {
      if (event.screen?.id === current) findAction(latest.current, event.buttonId)?.onPress?.();
    });
    // A preview stands for one push: once a tap opened the screen, the view previews it again. The push ends with an
    // error when the preview goes, which is not one here.
    const arm = () => {
      const previewId = nextId('Preview');
      current = previewId;
      pushPreview(
        screenId,
        name,
        previewProps ?? undefined,
        { nativeID, commit: true, actions: menu, width: previewWidth, height: previewHeight },
        previewId,
        () => active
      ).then(
        () => {
          if (active) arm();
        },
        () => {}
      );
    };
    arm();
    // The preview goes with the component, or when what it shows changes: otherwise it and its screen stay until the
    // screen it is in closes. One a tap has just pushed stays, the native side tells them apart.
    return () => {
      active = false;
      stop();
      if (current) dismissPreview(current);
    };
  }, [screenId, nativeID, shown]);
}

// The menu as the native side takes it: the titles and the ids, without the handlers.
function menuOf(actions: PreviewAction[]): object[] {
  return actions.map(({ id, title, style, actions: submenu }) => ({
    id,
    title,
    ...(style && { style }),
    ...(submenu && { actions: menuOf(submenu) }),
  }));
}

function findAction(actions: PreviewAction[], id: string): PreviewAction | undefined {
  for (const action of actions) {
    if (action.id === id) return action;
    const inner = action.actions && findAction(action.actions, id);
    if (inner) return inner;
  }
  return undefined;
}
