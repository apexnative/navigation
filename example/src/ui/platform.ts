import { Platform } from 'react-native';

/** The platform of a feature that only one has: iPad is iOS on an iPad. */
export type PlatformName = 'ios' | 'android' | 'ipad';

/** How the Showcase names a platform. */
export function platformLabel(platform: PlatformName): string {
  return { ios: 'iOS', android: 'Android', ipad: 'iPad' }[platform];
}

/** A feature of another platform than this one: it can't be tried here. */
export function isOtherPlatform(platform: PlatformName | undefined): boolean {
  switch (platform) {
    case undefined:
      return false;
    case 'ios':
      return Platform.OS !== 'ios';
    case 'android':
      return Platform.OS !== 'android';
    case 'ipad':
      return Platform.OS !== 'ios' || !Platform.isPad;
  }
}
