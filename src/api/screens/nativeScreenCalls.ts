import {
  answerNativeScreenCall,
  callNativeScreenFunction,
  nativeScreenInfo,
} from '../../engine/nativeScreens';
import { onNativeEvent } from '../../engine/native';
import type { NativeScreenCallEvent } from '../../interfaces/Events';
import { screenNavigation, type ScreenNavigation } from '../hooks/useNavigation';

// The commands a native screen runs from itself, as `useNavigation()` of a React screen does.
const COMMANDS = [
  'push',
  'pop',
  'popTo',
  'popToRoot',
  'showInColumn',
  'showModal',
  'dismiss',
  'showOverlay',
  'setOptions',
  'selectTab',
  'setTabBadge',
] as const satisfies readonly (keyof ScreenNavigation)[];

let listening = false;

/** Runs what the native screens call: the functions of their props, and the commands of the navigation from them. */
export function listenToNativeScreenCalls(): void {
  if (listening) return;
  listening = true;
  onNativeEvent<NativeScreenCallEvent>(
    'NativeScreenCall',
    ({ componentId, callId, kind, name, args }) => {
      answerNativeScreenCall(
        callId,
        new Promise((resolve) =>
          resolve(
            kind === 'command'
              ? command(componentId, name, args)
              : callNativeScreenFunction(componentId, name, args)
          )
        )
      );
    }
  );
}

function command(componentId: string, name: string, args: unknown[]): unknown {
  const screen = nativeScreenInfo(componentId);
  if (!screen) throw new Error(`[navigation] No native screen has the id '${componentId}'`);
  if (!(COMMANDS as readonly string[]).includes(name)) {
    throw new Error(`[navigation] '${name}' is not a command of a screen: ${COMMANDS.join(', ')}`);
  }
  const navigation = screenNavigation(screen) as unknown as Record<
    string,
    (...args: unknown[]) => unknown
  >;
  return navigation[name](...args);
}
