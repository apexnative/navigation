import type { OptionsBottomTab, OptionsTopBar } from '@apexnative/navigation';
import { fonts } from '../ui/fonts';

/**
 * The fonts of the Showcase's own bars: Righteous for large titles, Golos Text for titles. Only the Showcase's screens
 * take them: Details and the demos keep the system font, so a scenario of fonts shows what it sets.
 */
export const barFonts: OptionsTopBar = {
  title: { fontFamily: fonts.bodySemiBold },
  largeTitle: { fontFamily: fonts.display },
};

/** The font of the Showcase's tabs. */
export const tabFonts: OptionsBottomTab = { fontFamily: fonts.bodyMedium };
