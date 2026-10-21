import { NativeEventEmitter } from 'react-native';
import NativeModule, { type Spec } from '../adapters/NativeANNTurboModule';
import NativeEmitter from '../adapters/NativeANNTurboEventEmitter';

/** The commands of the native side; missing without it (Jest). */
export const commands: Spec = NativeModule;

// iOS sends its events through the module; Android sends them without one, so the emitter goes without it there.
// Without the native side (Jest) there is nothing to listen to.
const emitter = createEmitter();

function createEmitter(): NativeEventEmitter | undefined {
  try {
    return new NativeEventEmitter(NativeEmitter);
  } catch {
    return undefined;
  }
}

/** The URL that launched the native side, given once; null without one, and without the native side (Jest). */
export function getLaunchLink(): Promise<string | null> {
  return commands?.getLaunchLink?.() ?? Promise.resolve(null);
}

/** What the native side tells the JavaScript at its start. */
export interface StartInfo {
  /** The first JavaScript runtime of the process: a cold start, not a reload. */
  coldStart: boolean;
  /** The size of the native splash's logo as the CLI wrote it, in points (dp); null without it. */
  splashLogo: { width: number; height: number } | null;
  /** Whether the native side shows two screens as a pair: iOS 27.1+ built with its SDK, Android. */
  arrangements: boolean;
}

let startInfo: StartInfo | undefined;

/** What the native side tells at the start, asked once; without the native side (Jest) a cold start without a logo. */
export function getStartInfo(): StartInfo {
  startInfo ??= (commands?.getStartInfo?.() as StartInfo | undefined) ?? {
    coldStart: true,
    splashLogo: null,
    arrangements: false,
  };
  return startInfo;
}

/** Listens to an event of the native side, `ANN.<name>`; returns the function that stops listening. */
export function onNativeEvent<Event>(name: string, listener: (event: Event) => void): () => void {
  const subscription = emitter?.addListener(`ANN.${name}`, listener as (event: unknown) => void);
  return () => subscription?.remove();
}
