import { log } from '../showcase/journal';

/** The props the native profile screens show. */
export interface NativeProfileProps {
  name: string;
  role?: string;
  tags?: string[];
  accentColor?: string;
  isPremium?: boolean;
  initialCount?: number;
}

/** What the native profile screens call in JS (`useProps`): the counter, and what they could not do, to the Journal. */
export function useProfileFunctions() {
  return {
    sendCounter: (count: number) =>
      log('event', 'Native screen: counter', JSON.stringify({ count })),
    log: (message: string) => log('event', 'Native screen', message),
  };
}

export const nativeProfile: NativeProfileProps = {
  name: 'Alex Kim',
  role: 'Designer',
  tags: ['UIKit', 'SwiftUI', 'React'],
  accentColor: '#6C5CE7',
  isPremium: true,
  initialCount: 3,
};
