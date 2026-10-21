import type { Section } from '../scenario';
import inALayout from './18.3-in-a-layout';
import swiftui from './18.2-swiftui';
import uikit from './18.1-uikit';
import useProps from './18.4-use-props';
import functionResult from './18.5-function-result';
import commands from './18.6-commands';
import nativeFirst from './18.7-native-first';

/** Section 18: screens made on the native side. */
export const nativeScreensSection: Section = {
  id: 18,
  title: 'Native screens',
  intro:
    'Screens written natively — UIKit, SwiftUI or Android views — next to the React ones: registered on the native side, named in `screens` with `nativeScreen`, opened by the same commands. With `useProps` they get data and functions from the app, and they run commands of the navigation themselves. Each group runs with a button; the code is under it.',
  scenarios: [uikit, swiftui, inALayout, useProps, functionResult, commands, nativeFirst],
  runsOnly: true,
};
