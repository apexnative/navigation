import { navigation, useNavigation } from '@apexnative/navigation';

/**
 * The commands with the names of the clips' screens. The Showcase registers its own app's types, so the clips, a
 * second app in the same project, use the commands without them.
 */
export interface ClipNavigation {
  push(name: string, config?: object): Promise<string>;
  pop(): Promise<string>;
  goBack(): Promise<string>;
  popToRoot(): Promise<string>;
  setStack(...entries: unknown[]): Promise<string>;
  showModal(name: string, config?: object): Promise<string>;
  dismissModal(id: string): Promise<string>;
  dismissAllModals(): Promise<string>;
  dismiss(): Promise<string>;
  showOverlay(name: string, config?: object): Promise<string>;
  dismissOverlay(id: string): Promise<string>;
  dismissAllOverlays(): Promise<string>;
  switchTo(root: unknown): Promise<void>;
  selectTab(tab: number | string): Promise<void>;
  setTabBadge(badge: string | number | null, tab?: string): Promise<void>;
  openMenu(side: 'left' | 'right'): Promise<void>;
  closeMenu(): Promise<void>;
  showInColumn(name: string, config?: object): Promise<string>;
  setOptions(options: object): Promise<void>;
  setContext(next: object): void;
  setTheme(theme: object): void;
  setDirection(direction: 'ltr' | 'rtl'): void;
}

export const clipNavigation = navigation as unknown as ClipNavigation;

export function useClipNavigation(): ClipNavigation {
  return useNavigation() as unknown as ClipNavigation;
}
