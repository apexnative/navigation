import { AppRegistry as ReactNativeAppRegistry } from 'react-native';
import { onNativeEvent } from '../../engine/native';
import { familyOf } from '../../engine/refresh';
import { SLOT_HOST_MODULE } from '../../engine/slots';
import { SlotHost } from '../slots/SlotHost';
import { isNavigationApp, type NavigationApp, startApp } from './createNavigation';

type NavigationAppProvider = () => NavigationApp<any, any>;

let appKey: string | undefined;
let registered: NavigationApp<any, any> | NavigationAppProvider | undefined;
let app: NavigationApp | undefined;
let listening = false;

function onAppLaunched() {
  if (!registered) return;
  if (!app) {
    // A function loads the app's module here, not when index.js runs: a headless task never loads the navigation.
    const created = typeof registered === 'function' ? registered() : registered;
    if (!isNavigationApp(created)) {
      throw new Error(
        `[navigation] AppRegistry.registerComponent('${appKey}', ...) expects the navigation made by createNavigation, or a function returning it`
      );
    }
    app = created;
  }
  startApp(app).catch((error) => console.error('[navigation] The app failed to start', error));
}

/**
 * Registers the app's navigation under the app's name; it starts when the native side launches the app. A function
 * that returns the navigation loads its module only then.
 */
function registerComponent(
  key: string,
  navigation: NavigationApp<any, any> | NavigationAppProvider
): string {
  if (__DEV__ && registered) {
    // The entry file running again after an edit (Fast Refresh) registers its new version of the same app: Metro puts
    // it in the family of the old one once the file ran.
    const before = { key: appKey, app: registered };
    Promise.resolve().then(() => {
      const family = familyOf(before.app);
      if (family && family === familyOf(navigation)) return;
      console.warn(`[navigation] '${key}' replaces the app registered as '${before.key}'`);
    });
  }
  appKey = key;
  registered = navigation;
  if (!listening) {
    listening = true;
    // The native side runs the app's one React tree as this module once a screen of an app with `appProvider`
    // shows, and again after a reload, before the app starts.
    ReactNativeAppRegistry.registerComponent(SLOT_HOST_MODULE, () => SlotHost);
    // AppLaunched comes after the bundle ran, so the app registered in index.js is in place by then. It comes again
    // on every launch of the native side: a JavaScript reload, and on Android a new activity.
    onNativeEvent('AppLaunched', onAppLaunched);
  }
  return key;
}

/** React Native's AppRegistry, with `registerComponent` taking the navigation instead of a root component. */
export const AppRegistry: Omit<typeof ReactNativeAppRegistry, 'registerComponent'> & {
  registerComponent: typeof registerComponent;
} = { ...ReactNativeAppRegistry, registerComponent };
