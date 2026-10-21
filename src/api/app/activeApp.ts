import type { Presentation } from '../commands/presentation';
import type { RootsMap } from './roots';

/** The started app, for the commands made outside it. */
export interface ActiveApp {
  roots: RootsMap | undefined;
  presentation: Presentation | undefined;
  /** Resolves once the first root is shown. */
  mounted: Promise<void>;
}

let active: ActiveApp | undefined;

export function setActiveApp(app: ActiveApp) {
  active = app;
}

export function getActiveApp(): ActiveApp | undefined {
  return active;
}
