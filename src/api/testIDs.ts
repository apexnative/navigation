/**
 * Ids of the views the navigation shows without an id from the app, for end-to-end tests (Detox, Maestro, Appium) to
 * find them by.
 */
export const TestIDs = {
  /**
   * The back button of a stack's top bar: UIKit gives the system button this id on iOS, and the navigation gives the
   * same one on Android. `topBar.backButton.testID` sets another on Android.
   */
  backButton: 'BackButton',
} as const;
