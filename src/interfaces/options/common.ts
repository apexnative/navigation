import type { ColorValue, ImageRequireSource, ImageURISource } from 'react-native';

/**
 * A color for the light and for the dark appearance of the interface. The native side switches between them by
 * itself when the appearance changes. A side left out shows the platform's own look in that appearance, as if the
 * option were not set.
 */
export interface ThemeColor {
  /** The color in the light appearance: a color string or a `PlatformColor`. */
  light?: ColorValue;
  /** The color in the dark appearance: a color string or a `PlatformColor`. */
  dark?: ColorValue;
}

/**
 * A color of an option: a color string (`'#1E88E5'`, `'rgba(0,0,0,0.5)'`, `'red'`), a `PlatformColor` or
 * `DynamicColorIOS`, or `{ light, dark }` for each appearance. `null` takes the color away: the element shows as the
 * platform draws it without the option, also over a color its stack, its tabs or the default options give. A color
 * string the app can't parse counts as not set.
 */
export type Color = ColorValue | ThemeColor | null;

/**
 * An image of an option: `require('./icon.png')`, a source with a `uri` (a file, an asset of the app's bundle, a
 * `data:` address on iOS), the name of an Android drawable resource, or a different icon for each platform
 * (`{ ios, android }`). Remote addresses (`https:`) are not loaded, apart from Metro's assets in development; an
 * image that doesn't load leaves its element without an icon and reports the error in development.
 */
export type ImageResource = ImageRequireSource | ImageURISource | string | PlatformIcon;

/**
 * A different icon for each platform: `{ ios: 'square.and.arrow.up', android: 'ic_share' }`. A string is the name of
 * an SF Symbol on iOS and of a drawable resource of the app on Android; an image source works on either side. A
 * platform without its side shows no icon.
 */
export interface PlatformIcon {
  /** The icon on iOS: the name of an SF Symbol, or an image. */
  ios?: string | ImageRequireSource | ImageURISource;
  /** The icon on Android: the name of a drawable resource of the app, or an image. */
  android?: string | ImageRequireSource | ImageURISource;
}

/**
 * The weight of a font: `'normal'`, `'bold'` or `'100'`–`'900'`; on iOS also the names of UIKit's weights. An unknown
 * value draws the normal weight.
 */
export type FontWeight =
  | 'normal'
  | 'bold'
  | '100'
  | '200'
  | '300'
  | '400'
  | '500'
  | '600'
  | '700'
  | '800'
  | '900'
  | 'ultralight'
  | 'thin'
  | 'light'
  | 'regular'
  | 'medium'
  | 'semibold'
  | 'heavy'
  | 'black';

/** The slant of a font, on both platforms. */
export type FontStyle = 'normal' | 'italic';

/**
 * The family of a font: a font the app ships (iOS: listed under `UIAppFonts` in `Info.plist`; Android: in
 * `assets/fonts/` or a `res/font` resource React Native knows), or a system family by name. A family that isn't found
 * draws the system font.
 */
export type FontFamily = string;
