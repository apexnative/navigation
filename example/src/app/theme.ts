import { Platform } from 'react-native';
import type { NavigationTheme } from '@apexnative/navigation';
import { dynamic } from '../ui/theme';

/**
 * The look of the Showcase's navigation: buttons and the selected tab in its color, its backgrounds and titles. The
 * bars take the color of the cards on Android only: iOS 26 keeps its glass bars unless a color is set.
 * `navigation.setTheme` changes it.
 */
export const showcaseTheme: NavigationTheme = {
  colors: {
    primary: dynamic.primary,
    background: dynamic.background,
    text: dynamic.text,
    ...(Platform.OS === 'android' && { bar: dynamic.card }),
  },
};
