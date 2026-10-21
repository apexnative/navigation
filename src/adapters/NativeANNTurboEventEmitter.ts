import { TurboModuleRegistry, type TurboModule } from 'react-native';

/**
 * (iOS) The module the native side sends its events `ANN.<Name>` through. Android has none: its events come without
 * a module.
 */
export interface Spec extends TurboModule {
  /** A listener of the event came. */
  addListener(eventType: string): void;
  /** The number of listeners that went. */
  removeListeners(count: number): void;
}

// Missing on Android and without the native side (Jest).
export default TurboModuleRegistry.get<Spec>('ANNTurboEventEmitter') ?? undefined;
